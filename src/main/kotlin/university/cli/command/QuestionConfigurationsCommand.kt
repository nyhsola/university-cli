package university.cli.command

import university.cli.service.configuration.QuestionConfigurationService
import university.cli.util.ConfigurationCommandFormatter

class QuestionConfigurationsCommand(
    private val configurationService: QuestionConfigurationService,
) : ChatCommand {
    override val name = "/cf-question"
    override val description = "List available question configurations"

    private companion object {
        const val DEFAULT_CONFIGURATION = "default"
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return CommandResult("Usage: /cf-question", CommandMessageType.WARNING)
        }

        return try {
            val lines = configurationService.getAll().entries.flatMapIndexed { index, (name, configuration) ->
                ConfigurationCommandFormatter.format(
                    index,
                    "Question configuration ${configuration.id}",
                    buildList {
                        add("Name" to name)
                        if (name == DEFAULT_CONFIGURATION) add("Default" to "yes")
                        add("Parameters" to ConfigurationCommandFormatter.parameters(configuration.parameters))
                    },
                )
            }
            CommandResult(lines = lines)
        } catch (error: Exception) {
            CommandResult("Unable to load question configurations: ${error.message}", CommandMessageType.WARNING)
        }
    }
}
