package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.repository.JdbcIndexingConfigurationRepository

class ListCommand(
    private val migrator: FlywayMigrator,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
) : ChatCommand {
    override val name = "/list"
    override val description = "List indexed files and their statuses"

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) return CommandResult("Usage: /list", CommandMessageType.WARNING)

        return try {
            migrator.migrate()
            val files = configurationRepository.listFiles()
            if (files.isEmpty()) {
                CommandResult("No indexed files found.")
            } else {
                CommandResult(
                    files.joinToString("\n") { file ->
                        "[${file.configurationId}] ${file.fileName} — ${file.status}"
                    },
                )
            }
        } catch (error: Exception) {
            CommandResult("Unable to list files: ${error.message}", CommandMessageType.WARNING)
        }
    }
}
