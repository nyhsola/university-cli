package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.IndexConfiguration
import university.cli.model.IndexedFileInfo
import university.cli.model.IndexingStatus
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.configuration.IndexConfigurationService
import university.cli.util.TextUtil

class ListCommand(
    private val migrator: FlywayMigrator,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val configurationService: IndexConfigurationService,
) : ChatCommand {
    override val name = "/list"
    override val description = "List indexes, files, statuses, and configurations"

    private companion object {
        const val BOX_INNER_WIDTH = 66
        const val BOX_CONTENT_WIDTH = BOX_INNER_WIDTH - 2
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return CommandResult("Usage: /list", CommandMessageType.WARNING)
        }

        return try {
            migrator.migrate()
            val files = configurationRepository.listFiles()
            if (files.isEmpty()) {
                CommandResult("No indexed files found.")
            } else {
                val configurationsByHash = configurationService.getAll().entries.associateBy { it.value.hash }
                CommandResult(
                    lines = files.flatMapIndexed { index, file ->
                        formatFile(index, file, configurationsByHash[file.configurationHash])
                    },
                )
            }
        } catch (error: Exception) {
            CommandResult("Unable to list indexed files: ${error.message}", CommandMessageType.WARNING)
        }
    }

    private fun formatFile(
        index: Int,
        file: IndexedFileInfo,
        configurationEntry: Map.Entry<String, IndexConfiguration>?,
    ): List<CommandOutputLine> = buildList {
        if (index > 0) add(CommandOutputLine(""))

        add(CommandOutputLine(topBorder(file.configurationId), CommandMessageType.ACCENT))

        addField("Document", file.fileName)
        addField("Status", file.status.name, file.status.messageType())

        if (configurationEntry == null) {
            addField("Configuration", "unavailable — resource changed or removed", CommandMessageType.WARNING)
            addField("Configuration hash", file.configurationHash)
        } else {
            val (name, configuration) = configurationEntry

            val parameters = configuration.parameters.entries
                .sortedBy(Map.Entry<String, String>::key)
                .joinToString { (key, value) -> "$key=$value" }

            addField("Configuration", "[${configuration.id}] $name")
            addField("Embedding model", configuration.embeddingModel)
            addField("Chunking strategy", configuration.chunkingStrategy.name)
            addField("Parameters", parameters.ifBlank { "—" })
        }

        add(CommandOutputLine("╰${"─".repeat(BOX_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
    }

    private fun MutableList<CommandOutputLine>.addField(
        label: String,
        value: String,
        type: CommandMessageType = CommandMessageType.INFO,
    ) {
        TextUtil.wrap("$label: $value", BOX_CONTENT_WIDTH).forEach { line ->
            add(CommandOutputLine(row(line), type))
        }
    }

    private fun topBorder(indexId: Long): String {
        val title = "─ Index $indexId "
        return "╭$title${"─".repeat((BOX_INNER_WIDTH - title.length).coerceAtLeast(0))}╮"
    }

    private fun row(value: String): String =
        "│ ${value.take(BOX_CONTENT_WIDTH).padEnd(BOX_CONTENT_WIDTH)} │"

    private fun IndexingStatus.messageType(): CommandMessageType = when (this) {
        IndexingStatus.READY -> CommandMessageType.SUCCESS
        IndexingStatus.PROCESSING -> CommandMessageType.ACCENT
        IndexingStatus.FAILED -> CommandMessageType.WARNING
        IndexingStatus.CANCELLED -> CommandMessageType.MUTED
    }
}
