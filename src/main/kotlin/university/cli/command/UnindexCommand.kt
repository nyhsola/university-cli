package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.service.configuration.IndexProfileService
import university.cli.service.indexing.IndexRemovalService
import university.cli.service.indexing.ProjectFileService
import university.cli.util.CommandOptionParser

class UnindexCommand(
    private val migrator: FlywayMigrator,
    private val projectFileService: ProjectFileService,
    private val indexRemovalService: IndexRemovalService,
    private val profileService: IndexProfileService,
) : ChatCommand {
    override val name = "/unindex"
    override val usage = "/unindex <file> [--config ID]"
    override val description = "Remove stored indexes for one project text file"

    override fun execute(arguments: List<String>): CommandResult = try {
        val parsed = CommandOptionParser.parse(
            arguments,
            valueOptions = mapOf("--config" to "config", "-c" to "config"),
        )
        require(parsed.positionals.isNotEmpty()) { "File path is required" }

        val profileId = parsed.options["config"]?.toLongOrNull()
        require(parsed.options["config"] == null || profileId != null && profileId > 0) {
            "Index profile id must be positive"
        }

        val file = projectFileService.resolve(parsed.positionals.joinToString(" ").trim('"'))
        val profile = profileId?.let(profileService::get)
        migrator.migrate()
        val removed = indexRemovalService.remove(file, profile)
        val fileName = projectFileService.relativeName(file)

        if (removed == 0) {
            CommandResult("No matching indexes found for $fileName.", CommandMessageType.WARNING)
        } else {
            CommandResult("Removed $removed index${if (removed == 1) "" else "es"} for $fileName.")
        }
    } catch (error: IllegalArgumentException) {
        usageError(error.message ?: "Invalid arguments")
    } catch (error: Exception) {
        CommandResult("Unable to remove index: ${error.message}", CommandMessageType.WARNING)
    }
}
