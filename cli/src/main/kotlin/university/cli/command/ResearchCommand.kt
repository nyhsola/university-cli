package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.service.chat.ChatOutputService
import university.cli.service.chat.ChatStatusService
import university.cli.service.configuration.QueryProfileService
import university.cli.service.research.ResearchService
import university.cli.util.CommandOptionParser
import university.cli.util.ResearchResultFormatter
import java.util.concurrent.CancellationException

class ResearchCommand(
    private val migrator: FlywayMigrator,
    private val researchService: ResearchService,
    private val profileService: QueryProfileService,
    private val chatStatusService: ChatStatusService,
    private val chatOutputService: ChatOutputService,
) : ChatCommand {
    private companion object {
        const val DEFAULT_PROFILE = "default"
    }

    override val name = "/research"
    override val usage = "/research <question> [--explain]"
    override val description = "Research a question across all indexes with up to three searches"

    override fun execute(arguments: List<String>): CommandResult = try {
        val parsed = CommandOptionParser.parse(arguments, flagOptions = mapOf("--explain" to "explain"))
        val question = parsed.positionals.joinToString(" ").trim()
        require(question.isNotBlank()) { "Question must not be blank" }
        val profile = checkNotNull(profileService.getAll()[DEFAULT_PROFILE]) { "Default query profile not found" }
        migrator.migrate()
        val result = researchService.research(question, profile) { progress ->
            chatStatusService.set(progress.status)
            if (progress.alwaysShow || "explain" in parsed.flags) {
                progress.detail?.let { chatOutputService.write(it, CommandMessageType.MUTED) }
            }
        }
        CommandResult(lines = ResearchResultFormatter.format(result))
    } catch (_: CancellationException) {
        CommandResult("Research cancelled.", CommandMessageType.MUTED)
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
        CommandResult("Research cancelled.", CommandMessageType.MUTED)
    } catch (error: IllegalArgumentException) {
        usageError(error.message ?: "Invalid arguments")
    } catch (error: Exception) {
        CommandResult("Research failed: ${error.message}", CommandMessageType.WARNING)
    } finally {
        chatStatusService.clear()
    }
}
