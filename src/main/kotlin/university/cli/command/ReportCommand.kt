package university.cli.command

import university.cli.model.ReportStatus
import university.cli.service.chat.ChatStatusService
import university.cli.service.evaluation.ReportService
import java.util.concurrent.CancellationException

class ReportCommand(
    private val reportService: ReportService,
    private val chatStatusService: ChatStatusService,
) : ChatCommand {
    override val name = "/report"
    override val usage = "/report <dataset-directory>"
    override val description = "Generate a retrieval and answer evaluation report"

    override fun execute(arguments: List<String>): CommandResult {
        val datasetDirectory = arguments.singleOrNull()
            ?: return usageError("Specify exactly one dataset directory")

        return try {
            val result = reportService.generate(datasetDirectory, chatStatusService::set)
            val message = buildString {
                appendLine("Report written to ${result.reportFile}")
                appendLine("Comparison written to ${result.comparisonFile}")
                appendLine("Evaluations: ${result.questionsCount}")
                appendLine("Complete: ${result.completeAnswers}")
                appendLine("Not complete: ${result.incompleteAnswers}")
                appendLine("Incorrect: ${result.incorrectAnswers}")
                append("Errors: ${result.errors}")
            }
            val type = when (result.status) {
                ReportStatus.COMPLETED -> CommandMessageType.SUCCESS
                ReportStatus.COMPLETED_WITH_ERRORS, ReportStatus.FAILED -> CommandMessageType.WARNING
            }
            CommandResult(message, type)
        } catch (error: CancellationException) {
            CommandResult("Report generation cancelled", CommandMessageType.MUTED)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            CommandResult("Report generation cancelled", CommandMessageType.MUTED)
        } catch (error: IllegalArgumentException) {
            usageError(error.message ?: "Invalid report dataset")
        } catch (error: Exception) {
            CommandResult("Unable to generate report: ${error.message}", CommandMessageType.WARNING)
        } finally {
            chatStatusService.clear()
        }
    }
}
