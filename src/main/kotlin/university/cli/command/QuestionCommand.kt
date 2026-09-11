package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.service.chat.ChatStatusService
import university.cli.service.configuration.QuestionConfigurationService
import university.cli.service.retrieval.QuestionService
import university.cli.util.CommandArgumentUtil
import university.cli.util.TextUtil
import java.util.concurrent.CancellationException

class QuestionCommand(
    private val migrator: FlywayMigrator,
    private val questionService: QuestionService,
    private val configurationService: QuestionConfigurationService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/question"
    override val description = "Ask using index and question configuration ids"

    private companion object {
        const val ANSWER_INNER_WIDTH = 66
        const val ANSWER_CONTENT_WIDTH = ANSWER_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult {
        val indexId = arguments.getOrNull(0)?.toLongOrNull()
        val questionConfigurationId = arguments.getOrNull(1)?.toLongOrNull()
        val question = CommandArgumentUtil.text(arguments, 2)
        if (indexId == null || indexId <= 0 || questionConfigurationId == null ||
            questionConfigurationId <= 0 || question.isBlank()
        ) {
            return CommandResult(
                "Usage: /question <indexId> <questionConfigurationId> \"question\"",
                CommandMessageType.WARNING,
            )
        }

        return try {
            migrator.migrate()
            val questionConfiguration = configurationService.get(questionConfigurationId)
            chatStatusService.set("Searching relevant chunks...")
            val answer = questionService.question(indexId, questionConfiguration, question) {
                chatStatusService.set("Generating answer...")
            }

            if (answer.answer.isBlank()) {
                CommandResult("Model returned an empty answer.", CommandMessageType.WARNING)
            } else {
                CommandResult(lines = formatAnswer(answer.answer))
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
