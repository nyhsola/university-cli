package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.SearchScope
import university.cli.service.chat.ChatStatusService
import university.cli.service.configuration.QueryProfileService
import university.cli.service.indexing.ProjectFileService
import university.cli.service.operation.OperationLogService
import university.cli.service.retrieval.AnswerOutputService
import university.cli.service.retrieval.AskService
import university.cli.util.QueryCommandParser
import university.cli.util.TextUtil
import java.util.concurrent.CancellationException
import java.time.Instant
import kotlin.io.path.relativeTo

class AskCommand(
    private val migrator: FlywayMigrator,
    private val askService: AskService,
    private val profileService: QueryProfileService,
    private val projectFileService: ProjectFileService,
    private val answerOutputService: AnswerOutputService,
    private val operationLogService: OperationLogService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/ask"
    override val usage =
        "/ask <question> [--all|--file PATH|--index ID] [--profile ID] [--output FILE]"
    override val description = "Search indexed chunks and generate an answer"

    private companion object {
        const val DEFAULT_PROFILE = "default"
        const val ANSWER_INNER_WIDTH = 66
        const val ANSWER_CONTENT_WIDTH = ANSWER_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult = try {
        val startedAt = Instant.now()
        val request = QueryCommandParser.parse(arguments, allowExplain = false, allowOutput = true)
        val scope = normalizeScope(request.scope)
        val profile = request.profileId?.let(profileService::get)
            ?: checkNotNull(profileService.getAll()[DEFAULT_PROFILE]) { "Default query profile not found" }

        migrator.migrate()
        chatStatusService.set("Searching indexed chunks...")
        val result = askService.ask(scope, profile, request.query) {
            chatStatusService.set("Generating answer...")
        }
        check(result.answer.answer.isNotBlank()) { "Model returned an empty answer" }

        val outputFile = request.outputFile?.let { answerOutputService.write(it, result.answer.answer) }
        operationLogService.write(
            "ask",
            startedAt,
            scope,
            profile.id,
            request.query,
            result.relevantChunks,
            result.answer.answer,
            outputFile?.relativeTo(projectFileService.projectDirectory)?.toString(),
        )
        CommandResult(
            lines = buildList {
                addAll(formatAnswer(result.answer.answer))
                outputFile?.let { file ->
                    add(CommandOutputLine(""))
                    add(
                        CommandOutputLine(
                            "Saved to: ${file.relativeTo(projectFileService.projectDirectory)}",
                            CommandMessageType.MUTED,
                        ),
                    )
                }
            },
        )
    } catch (_: CancellationException) {
        CommandResult("Answer generation cancelled.", CommandMessageType.MUTED)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        CommandResult("Answer generation cancelled.", CommandMessageType.MUTED)
    } catch (error: IllegalArgumentException) {
        usageError(error.message ?: "Invalid arguments")
    } catch (error: Exception) {
        CommandResult("Answer generation failed: ${error.message}", CommandMessageType.WARNING)
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
