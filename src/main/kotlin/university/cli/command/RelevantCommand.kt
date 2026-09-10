package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.RelevantChunk
import university.cli.service.chat.ChatStatusService
import university.cli.service.retrieval.RelevantService
import university.cli.util.CommandArgumentUtil
import university.cli.util.TextUtil
import java.util.Locale
import java.util.concurrent.CancellationException

class RelevantCommand(
    private val migrator: FlywayMigrator,
    private val relevantService: RelevantService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/relevant"
    override val description = "Show relevant chunks: /relevant <id> \"question\""

    private companion object {
        const val RELEVANT_CHUNKS = 3
        const val BOX_INNER_WIDTH = 66
        const val BOX_CONTENT_WIDTH = BOX_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult {
        val configurationId = arguments.firstOrNull()?.toLongOrNull()
        val question = CommandArgumentUtil.text(arguments, 1)
        if (configurationId == null || configurationId <= 0 || question.isBlank()) {
            return CommandResult("Usage: /relevant <id> \"question\"", CommandMessageType.WARNING)
        }

        return try {
            migrator.migrate()
            chatStatusService.set("Searching relevant chunks...")
            val chunks = relevantService.getTopRelevant(configurationId, question, RELEVANT_CHUNKS)
            if (chunks.isEmpty()) {
                CommandResult("No relevant chunks found for configuration $configurationId.", CommandMessageType.WARNING)
            } else {
                CommandResult(lines = chunks.flatMapIndexed(::formatChunk))
            }
        } catch (_: CancellationException) {
            CommandResult("Search cancelled.", CommandMessageType.MUTED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult("Search cancelled.", CommandMessageType.MUTED)
        } catch (error: Exception) {
            CommandResult("Search failed: ${error.message}", CommandMessageType.WARNING)
        } finally {
            chatStatusService.clear()
        }
    }

    private fun formatChunk(index: Int, chunk: RelevantChunk): List<CommandOutputLine> {
        val distance = String.format(Locale.ROOT, "%.6f", chunk.distance)
        val title = " Chunk ${chunk.chunkId} "
        return buildList {
            if (index > 0) add(CommandOutputLine(""))
            add(CommandOutputLine(topBorder(title), CommandMessageType.ACCENT))
            add(CommandOutputLine(row("Configuration: ${chunk.configurationId}"), CommandMessageType.MUTED))
            add(CommandOutputLine(row("File: ${chunk.fileName}"), CommandMessageType.MUTED))
            add(CommandOutputLine(row("Distance: $distance"), CommandMessageType.MUTED))
            add(CommandOutputLine("├${"─".repeat(BOX_INNER_WIDTH)}┤", CommandMessageType.ACCENT))
            TextUtil.wrap(chunk.content, BOX_CONTENT_WIDTH).forEach { line ->
                add(CommandOutputLine(row(line)))
            }
            add(CommandOutputLine("╰${"─".repeat(BOX_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
        }
    }

    private fun topBorder(title: String): String {
        val decoratedTitle = "─$title"
        val remainingWidth = (BOX_INNER_WIDTH - decoratedTitle.length).coerceAtLeast(0)
        return "╭$decoratedTitle${"─".repeat(remainingWidth)}╮"
    }

    private fun row(value: String): String =
        "│ ${value.take(BOX_CONTENT_WIDTH).padEnd(BOX_CONTENT_WIDTH)} │"
}
