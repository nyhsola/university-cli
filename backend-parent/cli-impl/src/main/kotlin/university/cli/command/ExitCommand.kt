package university.cli.command

class ExitCommand : ChatCommand {
    override val name = "/exit"
    override val description = "Exit the application"

    override fun execute(arguments: List<String>) = CommandResult(
        message = "Goodbye!",
        messageType = CommandMessageType.MUTED,
        shouldExit = true,
    )
}
