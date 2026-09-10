package university.cli.command

class HelpCommand(
    private val commands: List<ChatCommand>,
) : ChatCommand {
    override val name = "/help"
    override val description = "Show available commands"

    private companion object {
        const val COMMAND_COLUMN_WIDTH = 12
        const val DESCRIPTION_COLUMN_WIDTH = 58
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return CommandResult("Usage: /help", CommandMessageType.WARNING)
        }

        val availableCommands = (commands + this).sortedBy(ChatCommand::name)
        val divider = "├${"─".repeat(COMMAND_COLUMN_WIDTH + 2)}┼${"─".repeat(DESCRIPTION_COLUMN_WIDTH + 2)}┤"
        val lines = buildList {
            add(CommandOutputLine(topBorder(), CommandMessageType.ACCENT))
            add(CommandOutputLine(row("COMMAND", "DESCRIPTION"), CommandMessageType.ACCENT))
            add(CommandOutputLine(divider, CommandMessageType.ACCENT))
            availableCommands.forEach { command ->
                add(CommandOutputLine(row(command.name, command.description)))
            }
            add(CommandOutputLine(bottomBorder(), CommandMessageType.ACCENT))
        }
        return CommandResult(lines = lines)
    }

    private fun topBorder(): String {
        val width = COMMAND_COLUMN_WIDTH + DESCRIPTION_COLUMN_WIDTH + 5
        val title = " Available commands "
        return "╭─$title${"─".repeat((width - title.length - 1).coerceAtLeast(0))}╮"
    }

    private fun bottomBorder(): String {
        val width = COMMAND_COLUMN_WIDTH + DESCRIPTION_COLUMN_WIDTH + 5
        return "╰${"─".repeat(width)}╯"
    }

    private fun row(command: String, description: String): String =
        "│ ${command.take(COMMAND_COLUMN_WIDTH).padEnd(COMMAND_COLUMN_WIDTH)} │ " +
            "${description.take(DESCRIPTION_COLUMN_WIDTH).padEnd(DESCRIPTION_COLUMN_WIDTH)} │"
}
