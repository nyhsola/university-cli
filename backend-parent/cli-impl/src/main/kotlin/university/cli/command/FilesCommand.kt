package university.cli.command

import university.cli.database.FlywayMigrator
import university.cli.model.FileIndexStatus
import university.cli.model.ProjectFileStatus
import university.cli.service.indexing.ProjectFileStatusService
import university.cli.util.CommandOptionParser
import university.cli.util.TextUtil

class FilesCommand(
    private val migrator: FlywayMigrator,
    private val statusService: ProjectFileStatusService,
) : ChatCommand {
    override val name = "/files"
    override val usage = "/files [--config ID] [--indexed|--pending|--failed]"
    override val description = "List project text files and their indexing status"

    private companion object {
        const val BOX_INNER_WIDTH = 66
        const val BOX_CONTENT_WIDTH = BOX_INNER_WIDTH - 2
    }

    @Suppress("TooGenericExceptionCaught")
    override fun execute(arguments: List<String>): CommandResult = try {
        val parsed = CommandOptionParser.parse(
            arguments,
            valueOptions = mapOf("--config" to "config", "-c" to "config"),
            flagOptions = mapOf(
                "--indexed" to "indexed",
                "--pending" to "pending",
                "--failed" to "failed",
            ),
        )
        require(parsed.positionals.isEmpty()) { "Unexpected positional arguments" }
        require(parsed.flags.size <= 1) { "Only one status filter may be specified" }
        val profileId = parsed.options["config"]?.toLongOrNull()
        require(parsed.options["config"] == null || (profileId != null && profileId > 0)) {
            "Index profile id must be positive"
        }

        migrator.migrate()
        val files = statusService.getAll(profileId).filter { file -> matchesFilter(file.status, parsed.flags) }
        if (files.isEmpty()) {
            CommandResult("No matching project text files found.")
        } else {
            CommandResult(
                lines = files
                    .groupBy(ProjectFileStatus::fileName)
                    .values
                    .flatMapIndexed(::formatFile),
            )
        }
    } catch (error: IllegalArgumentException) {
        usageError(error.message ?: "Invalid arguments")
    } catch (error: Exception) {
        CommandResult("Unable to list project files: ${error.message}", CommandMessageType.WARNING)
    }

    private fun matchesFilter(status: FileIndexStatus, filters: Set<String>): Boolean = when (filters.singleOrNull()) {
        "indexed" -> status == FileIndexStatus.READY
        "pending" -> status == FileIndexStatus.NOT_INDEXED || status == FileIndexStatus.CHANGED
        "failed" -> status == FileIndexStatus.FAILED
        else -> true
    }

    private fun formatFile(index: Int, files: List<ProjectFileStatus>): List<CommandOutputLine> = buildList {
        val fileName = files.first().fileName
        if (index > 0) add(CommandOutputLine(""))
        add(CommandOutputLine(topBorder(fileName), CommandMessageType.ACCENT))

        if (files.size == 1 && files.first().indexId == null) {
            val file = files.first()
            addField("Status", file.status.name, file.status.messageType())
        } else {
            files.forEach { file ->
                val index = file.indexId ?: return@forEach
                val profile = file.indexProfileId?.toString() ?: "UNKNOWN"
                addField(
                    "Index $index",
                    "profile $profile — ${file.status.name}",
                    file.status.messageType(),
                )
            }
        }

        add(CommandOutputLine("╰${"─".repeat(BOX_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
    }

    private fun MutableList<CommandOutputLine>.addField(label: String, value: String, type: CommandMessageType = CommandMessageType.INFO) {
        TextUtil.wrap("$label: $value", BOX_CONTENT_WIDTH).forEach { line ->
            add(CommandOutputLine(row(line), type))
        }
    }

    private fun topBorder(fileName: String): String {
        val title = "─ ${fileName.take(BOX_CONTENT_WIDTH - 3)} "
        return "╭$title${"─".repeat((BOX_INNER_WIDTH - title.length).coerceAtLeast(0))}╮"
    }

    private fun row(value: String): String = "│ ${value.take(BOX_CONTENT_WIDTH).padEnd(BOX_CONTENT_WIDTH)} │"

    private fun FileIndexStatus.messageType(): CommandMessageType = when (this) {
        FileIndexStatus.READY -> CommandMessageType.SUCCESS
        FileIndexStatus.PROCESSING -> CommandMessageType.ACCENT
        FileIndexStatus.FAILED -> CommandMessageType.WARNING
        FileIndexStatus.CANCELLED -> CommandMessageType.MUTED
        FileIndexStatus.NOT_INDEXED, FileIndexStatus.CHANGED -> CommandMessageType.WARNING
    }
}
