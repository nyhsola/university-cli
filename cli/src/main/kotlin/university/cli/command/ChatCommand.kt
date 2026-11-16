package university.cli.command

interface ChatCommand {
    val name: String
    val usage: String
        get() = name
    val description: String

    fun execute(arguments: List<String>): CommandResult

    fun usageError(message: String): CommandResult =
        CommandResult("$message\nUsage: $usage", CommandMessageType.WARNING)
}

data class CommandResult(
    val message: String? = null,
    val messageType: CommandMessageType = CommandMessageType.INFO,
    val shouldExit: Boolean = false,
    val lines: List<CommandOutputLine> = emptyList(),
    val table: CommandOutputTable? = null,
)

data class CommandOutputTable(
    val title: String,
    val firstColumnTitle: String,
    val secondColumnTitle: String,
    val rows: List<Pair<String, String>>,
)

data class CommandOutputLine(
    val text: String,
    val type: CommandMessageType = CommandMessageType.INFO,
    val fullWidth: Boolean = false,
)

enum class CommandMessageType {
    INFO,
    SUCCESS,
    WARNING,
    MUTED,
    ACCENT,
}
