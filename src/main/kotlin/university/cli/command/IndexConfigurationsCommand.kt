package university.cli.command

import university.cli.service.configuration.IndexConfigurationService
import university.cli.util.ConfigurationCommandFormatter

class IndexConfigurationsCommand(
    private val configurationService: IndexConfigurationService,
) : ChatCommand {
    override val name = "/cf-index"
    override val description = "List available indexing configurations"

    private companion object {
        const val DEFAULT_CONFIGURATION = "default"
    }

    override fun execute(arguments: List<String>): CommandResult {
        if (arguments.isNotEmpty()) {
            return CommandResult("Usage: /cf-index", CommandMessageType.WARNING)
        }

        return try {
            val lines = configurationService.getAll().entries.flatMapIndexed { index, (name, configuration) ->
                ConfigurationCommandFormatter.format(
                    index,
                    "Index configuration ${configuration.id}",
                    buildList {
                        add("Name" to name)
                        if (name == DEFAULT_CONFIGURATION) add("Default" to "yes")
                        add("Embedding model" to configuration.embeddingModel)
                        add("Chunking strategy" to configuration.chunkingStrategy.name)
                        add("Parameters" to ConfigurationCommandFormatter.parameters(configuration.parameters))
                    },
                )
            }
            CommandResult(lines = lines)
        } catch (error: Exception) {
            CommandResult("Unable to load indexing configurations: ${error.message}", CommandMessageType.WARNING)
        }
    }
}
