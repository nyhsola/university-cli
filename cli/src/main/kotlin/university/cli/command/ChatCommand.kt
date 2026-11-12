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
)

data class CommandOutputLine(
    val text: String,
    val type: CommandMessageType = CommandMessageType.INFO,
)

enum class CommandMessageType {
    INFO,
    SUCCESS,
    WARNING,
    MUTED,
    ACCENT,
}
