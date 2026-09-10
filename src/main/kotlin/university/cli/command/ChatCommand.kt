package university.cli.command

interface ChatCommand {
    val name: String
    val description: String

    fun execute(arguments: List<String>): CommandResult
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
