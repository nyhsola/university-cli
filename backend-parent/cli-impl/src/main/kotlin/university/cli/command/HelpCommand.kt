package university.cli.command

class HelpCommand(
    private val commands: List<ChatCommand>,
) : ChatCommand {
    override val name = "/help"
    override val description = "Show available commands"

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return usageError("Unexpected arguments")
        }

        val availableCommands = (commands + this).sortedForDisplay()
        return CommandResult(
            table = CommandOutputTable(
                "Available commands",
                "COMMAND",
                "DESCRIPTION",
                availableCommands.map { it.name to it.description },
            ),
        )
    }
}
