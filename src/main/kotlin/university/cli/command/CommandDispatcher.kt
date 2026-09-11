package university.cli.command

class CommandDispatcher(
    commands: List<ChatCommand>,
) {
    private val registeredCommands = commands.sortedForDisplay()
    private val commandsByName = registeredCommands.associateBy(ChatCommand::name)

    fun suggestions(input: String): List<CommandSuggestion> {
        val commandPrefix = input.trimStart().substringBefore(' ')
        if (!commandPrefix.startsWith('/')) {
            return emptyList()
        }

        return registeredCommands
            .filter { it.name.startsWith(commandPrefix, ignoreCase = true) }
            .map { CommandSuggestion(name = it.name, description = it.description) }
    }

    fun dispatch(input: String): CommandResult {
        val normalizedInput = input.trim().trimStart('\uFEFF')
        val tokens = normalizedInput.split(Regex("\\s+")).filter(String::isNotEmpty)
        if (tokens.isEmpty()) {
            return CommandResult()
        }

        return dispatch(tokens.first(), tokens.drop(1))
    }

    fun dispatch(commandName: String, arguments: List<String>): CommandResult {
        val command = commandsByName[commandName] ?: return CommandResult(
            message = "Unknown command: $commandName",
            messageType = CommandMessageType.WARNING,
        )

        return command.execute(arguments)
    }
}

data class CommandSuggestion(
    val name: String,
    val description: String,
)

internal fun Iterable<ChatCommand>.sortedForDisplay(): List<ChatCommand> = sortedWith(
        compareBy<ChatCommand> { COMMAND_DISPLAY_ORDER[it.name] ?: Int.MAX_VALUE }
            .thenBy(ChatCommand::name),
    )

private val COMMAND_DISPLAY_ORDER = listOf(
    "/cf-index",
    "/index",
    "/list",
    "/relevant",
    "/cf-question",
    "/question",
    "/help",
    "/exit",
).withIndex().associate { (index, name) -> name to index }
