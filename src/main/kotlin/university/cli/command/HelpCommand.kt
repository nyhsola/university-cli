package university.cli.command

import university.cli.util.TextUtil

class HelpCommand(
    private val commands: List<ChatCommand>,
) : ChatCommand {
    override val name = "/help"
    override val description = "Show available commands"

    private companion object {
        const val COMMAND_COLUMN_WIDTH = 24
        const val DESCRIPTION_COLUMN_WIDTH = 49
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return usageError("Unexpected arguments")
        }

        val availableCommands = (commands + this).sortedForDisplay()
        val divider = "├${"─".repeat(COMMAND_COLUMN_WIDTH + 2)}┼${"─".repeat(DESCRIPTION_COLUMN_WIDTH + 2)}┤"
        val lines = buildList {
            add(CommandOutputLine(topBorder(), CommandMessageType.ACCENT))
            add(CommandOutputLine(row("COMMAND", "DESCRIPTION"), CommandMessageType.ACCENT))
            add(CommandOutputLine(divider, CommandMessageType.ACCENT))
            availableCommands.forEach { command ->
                addAll(rows(command.name, command.description))
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
        "│ ${command.padEnd(COMMAND_COLUMN_WIDTH)} │ ${description.padEnd(DESCRIPTION_COLUMN_WIDTH)} │"

    private fun rows(command: String, description: String): List<CommandOutputLine> {
        val commandLines = TextUtil.wrap(command, COMMAND_COLUMN_WIDTH)
        val descriptionLines = TextUtil.wrap(description, DESCRIPTION_COLUMN_WIDTH)
        return List(maxOf(commandLines.size, descriptionLines.size)) { index ->
            CommandOutputLine(
                row(
                    commandLines.getOrElse(index) { "" },
                    descriptionLines.getOrElse(index) { "" },
                ),
            )
        }
    }
}
