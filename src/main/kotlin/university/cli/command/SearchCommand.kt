package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.RelevantChunk
import university.cli.model.SearchScope
import university.cli.service.chat.ChatStatusService
import university.cli.service.configuration.QueryProfileService
import university.cli.service.indexing.ProjectFileService
import university.cli.service.operation.OperationLogService
import university.cli.service.retrieval.SearchService
import university.cli.util.QueryCommandParser
import university.cli.util.TextUtil
import java.util.Locale
import java.time.Instant
import java.util.concurrent.CancellationException

class SearchCommand(
    private val migrator: FlywayMigrator,
    private val searchService: SearchService,
    private val profileService: QueryProfileService,
    private val projectFileService: ProjectFileService,
    private val operationLogService: OperationLogService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/search"
    override val usage =
        "/search <query> [--all|--file PATH|--index ID] [--profile ID] [--explain]"
    override val description = "Search indexed chunks without generating an answer"

    private companion object {
        const val DEFAULT_PROFILE = "default"
        const val BOX_INNER_WIDTH = 66
        const val BOX_CONTENT_WIDTH = BOX_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult = try {
        val startedAt = Instant.now()
        val request = QueryCommandParser.parse(arguments, allowExplain = true, allowOutput = false)
        val scope = normalizeScope(request.scope)
        val profile = request.profileId?.let(profileService::get)
            ?: checkNotNull(profileService.getAll()[DEFAULT_PROFILE]) { "Default query profile not found" }

        migrator.migrate()
        chatStatusService.set("Searching indexed chunks...")
        val chunks = searchService.search(scope, profile, request.query)
        operationLogService.write("search", startedAt, scope, profile.id, request.query, chunks)
        if (chunks.isEmpty()) {
            CommandResult("No relevant chunks found.", CommandMessageType.WARNING)
        } else {
            CommandResult(lines = chunks.flatMapIndexed { index, chunk ->
                formatChunk(index, chunk, request.explain)
            })
        }
    } catch (_: CancellationException) {
        CommandResult("Search cancelled.", CommandMessageType.MUTED)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        CommandResult("Search cancelled.", CommandMessageType.MUTED)
    } catch (error: IllegalArgumentException) {
        usageError(error.message ?: "Invalid arguments")
    } catch (error: Exception) {
        CommandResult("Search failed: ${error.message}", CommandMessageType.WARNING)
    } finally {
        chatStatusService.clear()
    }

    private fun normalizeScope(scope: SearchScope): SearchScope = when (scope) {
        SearchScope.All, is SearchScope.Index -> scope
        is SearchScope.File -> {
            val file = projectFileService.resolve(scope.fileName)
            SearchScope.File(projectFileService.relativeName(file))
        }
    }

    private fun formatChunk(index: Int, chunk: RelevantChunk, explain: Boolean): List<CommandOutputLine> = buildList {
        if (index > 0) add(CommandOutputLine(""))
        add(CommandOutputLine(topBorder(" Chunk ${chunk.chunkId} "), CommandMessageType.ACCENT))
        add(CommandOutputLine(row("File: ${chunk.fileName}"), CommandMessageType.MUTED))
        if (explain) {
            add(CommandOutputLine(row("Index: ${chunk.configurationId}"), CommandMessageType.MUTED))
            chunk.distance?.let { distance ->
                add(CommandOutputLine(row("Dense: rank ${chunk.denseRank}, distance ${formatScore(distance)}"), CommandMessageType.MUTED))
            }
            chunk.bm25Score?.let { score ->
                add(CommandOutputLine(row("Lexical: rank ${chunk.lexicalRank}, BM25 ${formatScore(score)}"), CommandMessageType.MUTED))
            }
            chunk.rrfScore?.let { score ->
                add(CommandOutputLine(row("RRF: ${formatScore(score)}"), CommandMessageType.MUTED))
            }
        }
        add(CommandOutputLine("├${"─".repeat(BOX_INNER_WIDTH)}┤", CommandMessageType.ACCENT))
        TextUtil.wrap(chunk.content, BOX_CONTENT_WIDTH).forEach { line -> add(CommandOutputLine(row(line))) }
        add(CommandOutputLine("╰${"─".repeat(BOX_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
    }

    private fun topBorder(title: String): String {
        val decoratedTitle = "─$title"
        return "╭$decoratedTitle${"─".repeat((BOX_INNER_WIDTH - decoratedTitle.length).coerceAtLeast(0))}╮"
    }

    private fun row(value: String): String = "│ ${value.take(BOX_CONTENT_WIDTH).padEnd(BOX_CONTENT_WIDTH)} │"

    private fun formatScore(value: Double): String = String.format(Locale.ROOT, "%.6f", value)
}
