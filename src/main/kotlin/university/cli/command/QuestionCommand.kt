package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.service.chat.ChatStatusService
import university.cli.service.retrieval.QuestionService
import university.cli.util.CommandArgumentUtil
import university.cli.util.TextUtil
import java.util.concurrent.CancellationException

class QuestionCommand(
    private val migrator: FlywayMigrator,
    private val questionService: QuestionService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/question"
    override val description = "Ask a question using an index: /question <id> \"question\""

    private companion object {
        const val ANSWER_INNER_WIDTH = 66
        const val ANSWER_CONTENT_WIDTH = ANSWER_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult {
        val configurationId = arguments.firstOrNull()?.toLongOrNull()
        val question = CommandArgumentUtil.text(arguments, 1)
        if (configurationId == null || configurationId <= 0 || question.isBlank()) {
            return CommandResult("Usage: /question <id> \"question\"", CommandMessageType.WARNING)
        }

        return try {
            migrator.migrate()
            chatStatusService.set("Searching relevant chunks...")
            val answer = questionService.question(configurationId, question) {
                chatStatusService.set("Generating answer...")
            }
            if (answer.text.isBlank()) {
                CommandResult("Model returned an empty answer.", CommandMessageType.WARNING)
            } else {
                CommandResult(lines = formatAnswer(answer.text))
            }
        } catch (_: CancellationException) {
            CommandResult("Question cancelled.", CommandMessageType.MUTED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult("Question cancelled.", CommandMessageType.MUTED)
        } catch (error: Exception) {
            CommandResult("Question failed: ${error.message}", CommandMessageType.WARNING)
        } finally {
            chatStatusService.clear()
        }
    }

    private fun formatAnswer(answer: String): List<CommandOutputLine> = buildList {
        add(CommandOutputLine(answerTopBorder(), CommandMessageType.ACCENT))
        TextUtil.wrap(answer.trim(), ANSWER_CONTENT_WIDTH).forEach { line ->
            add(CommandOutputLine(answerRow(line), CommandMessageType.SUCCESS))
        }
        add(CommandOutputLine("╰${"─".repeat(ANSWER_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
    }

    private fun answerTopBorder(): String {
        val title = "─ Answer "
        return "╭$title${"─".repeat(ANSWER_INNER_WIDTH - title.length)}╮"
    }

    private fun answerRow(value: String): String =
        "│ ${value.take(ANSWER_CONTENT_WIDTH).padEnd(ANSWER_CONTENT_WIDTH)} │"
}
