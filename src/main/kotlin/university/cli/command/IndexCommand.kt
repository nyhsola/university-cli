package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.ProjectIndexingResult
import university.cli.service.chat.ChatOutputService
import university.cli.service.chat.ChatStatusService
import university.cli.service.indexing.ProjectIndexingService
import university.cli.service.configuration.IndexConfigurationService
import java.util.concurrent.CancellationException

class IndexCommand(
    private val migrator: FlywayMigrator,
    private val projectIndexingService: ProjectIndexingService,
    private val configurationService: IndexConfigurationService,
    private val chatStatusService: ChatStatusService,
    private val chatOutputService: ChatOutputService,
) : ChatCommand {
    override val name = "/index"
    override val description = "Index all project text files using a configuration id"

    override fun execute(arguments: List<String>): CommandResult {
        val configurationId = arguments.singleOrNull()?.toLongOrNull()
        if (configurationId == null || configurationId <= 0) {
            return CommandResult("Usage: /index <configurationId>", CommandMessageType.WARNING)
        }

        return try {
            migrator.migrate()
            val result = projectIndexingService.index(
                configurationService.get(configurationId),
                { progress ->
                    chatOutputService.write("Indexing file (${progress.current} of ${progress.total}): ${progress.path}")
                    chatStatusService.set("Indexing chunks: 0%")
                },
                { progress -> chatStatusService.set("Indexing chunks: ${progress.percentage}%") },
            )
            result.toCommandResult()
        } catch (_: CancellationException) {
            CommandResult("Indexing cancelled.", CommandMessageType.MUTED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult("Indexing cancelled.", CommandMessageType.MUTED)
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
