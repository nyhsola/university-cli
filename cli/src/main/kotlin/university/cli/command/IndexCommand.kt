package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.ProjectIndexingResult
import university.cli.service.chat.ChatOutputService
import university.cli.service.chat.ChatStatusService
import university.cli.service.indexing.ProjectIndexingService
import university.cli.service.indexing.ProjectFileService
import university.cli.service.configuration.IndexProfileService
import university.cli.service.operation.OperationLogService
import university.cli.util.CommandOptionParser
import java.time.Instant
import java.util.concurrent.CancellationException

class IndexCommand(
    private val migrator: FlywayMigrator,
    private val projectIndexingService: ProjectIndexingService,
    private val projectFileService: ProjectFileService,
    private val configurationService: IndexProfileService,
    private val chatStatusService: ChatStatusService,
    private val chatOutputService: ChatOutputService,
    private val operationLogService: OperationLogService,
) : ChatCommand {
    override val name = "/index"
    override val usage = "/index (<file>|--all) --config ID"
    override val description = "Index one text file or all project text files"

    override fun execute(arguments: List<String>): CommandResult {
        return try {
            val startedAt = Instant.now()
            val parsed = CommandOptionParser.parse(
                arguments,
                valueOptions = mapOf("--config" to "config", "-c" to "config"),
                flagOptions = mapOf("--all" to "all"),
            )
            val configurationId = parsed.options["config"]?.toLongOrNull()
            require(configurationId != null && configurationId > 0) {
                "A positive index profile id is required"
            }
            val all = "all" in parsed.flags
            require(all.xor(parsed.positionals.isNotEmpty())) {
                "Specify exactly one file or --all"
            }

            migrator.migrate()
            val configuration = configurationService.get(configurationId)
            val onFileProgress = { progress: university.cli.model.ProjectIndexingProgress ->
                chatOutputService.write("Indexing file (${progress.current} of ${progress.total}): ${progress.path}")
                chatStatusService.set("Indexing chunks: 0%")
            }
            val onChunkProgress = { progress: university.cli.model.ChunkIndexingProgress ->
                chatStatusService.set("Indexing chunks: ${progress.percentage}%")
            }
            val file = if (all) {
                null
            } else {
                projectFileService.resolve(parsed.positionals.joinToString(" ").trim('"'))
            }

            val result = if (file == null) {
                projectIndexingService.indexAll(configuration, onFileProgress, onChunkProgress)
            } else {
                projectIndexingService.indexFile(file, configuration, onFileProgress, onChunkProgress, )
            }

            val scope = file?.let { "file:${projectFileService.relativeName(it)}" } ?: "all"
            operationLogService.writeIndexing(startedAt, scope, configuration.id, result)
            result.toCommandResult()
        } catch (_: CancellationException) {
            CommandResult("Indexing cancelled.", CommandMessageType.MUTED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult("Indexing cancelled.", CommandMessageType.MUTED)
        } catch (error: IllegalArgumentException) {
            usageError(error.message ?: "Invalid arguments")
        } catch (error: Exception) {
            CommandResult("Indexing failed: ${error.message}", CommandMessageType.WARNING)
        } finally {
            chatStatusService.clear()
        }
    }

    private fun ProjectIndexingResult.toCommandResult(): CommandResult {
        if (indexedFiles.isEmpty() && skippedFiles.isEmpty() && failedFiles.isEmpty()) {
            return CommandResult("No files found to index.")
        }

        val output = buildList {
            if (indexedFiles.isNotEmpty()) {
                add("Indexed ${indexedFiles.size} file(s):")
                indexedFiles.forEach { file ->
                    add("  ${file.path} [configuration ${file.result.configurationId}]")
                }
            }
            if (skippedFiles.isNotEmpty()) {
                add("Skipped ${skippedFiles.size} existing file(s):")
                skippedFiles.forEach { file ->
                    add("  ${file.path} [configuration ${file.result.configurationId}]")
                }
            }
            if (failedFiles.isNotEmpty()) {
                add("Failed ${failedFiles.size} file(s):")
                failedFiles.forEach { file -> add("  ${file.path}: ${file.message}") }
            }
        }.joinToString("\n")

        val messageType = if (failedFiles.isEmpty()) CommandMessageType.SUCCESS else CommandMessageType.WARNING
        return CommandResult(output, messageType)
    }
}
