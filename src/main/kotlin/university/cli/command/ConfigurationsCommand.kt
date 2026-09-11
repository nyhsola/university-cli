package university.cli.command

import university.cli.service.indexing.ConfigurationService

class ConfigurationsCommand(
    private val configurationService: ConfigurationService,
) : ChatCommand {
    override val name = "/configurations"
    override val description = "List available indexing configurations"

    private companion object {
        const val DEFAULT_CONFIGURATION = "default"
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return CommandResult("Usage: /configurations", CommandMessageType.WARNING)
        }

        return try {
            val configurations = configurationService.getAll()
            val lines = configurations.flatMap { (name, configuration) ->
                val marker = if (name == DEFAULT_CONFIGURATION) "*" else " "
                val parameters = configuration.parameters.entries
                    .sortedBy(Map.Entry<String, String>::key)
                    .joinToString { (key, value) -> "$key=$value" }
                listOf(
                    CommandOutputLine("$marker $name", CommandMessageType.ACCENT),
                    CommandOutputLine("  id: ${configuration.id}"),
                    CommandOutputLine("  embeddingModel: ${configuration.embeddingModel}"),
                    CommandOutputLine("  strategy: ${configuration.strategy}"),
                    CommandOutputLine("  parameters: $parameters"),
                )
            }
            CommandResult(lines = lines)
        } catch (error: Exception) {
            CommandResult("Unable to load configurations: ${error.message}", CommandMessageType.WARNING)
        }
    }
}
