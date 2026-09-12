package university.cli.command

import university.cli.service.configuration.IndexProfileService
import university.cli.service.configuration.QueryProfileService
import university.cli.util.ConfigurationCommandFormatter

class ProfilesCommand(
    private val indexProfileService: IndexProfileService,
    private val queryProfileService: QueryProfileService,
) : ChatCommand {
    override val name = "/profiles"
    override val usage = "/profiles [index|query]"
    override val description = "List index and query profiles"

    private companion object {
        const val DEFAULT_PROFILE = "default"
        const val INDEX = "index"
        const val QUERY = "query"
    }

    override fun execute(arguments: List<String>): CommandResult {
        val type = arguments.singleOrNull()?.lowercase()
        if (arguments.size > 1 || type != null && type !in setOf(INDEX, QUERY)) {
            return usageError("Expected either index or query")
        }

        return try {
            val lines = buildList {
                if (type == null || type == INDEX) addAll(indexProfiles())
                if (type == null || type == QUERY) {
                    if (isNotEmpty()) add(CommandOutputLine(""))
                    addAll(queryProfiles())
                }
            }
            CommandResult(lines = lines)
        } catch (error: Exception) {
            CommandResult("Unable to load profiles: ${error.message}", CommandMessageType.WARNING)
        }
    }

    private fun indexProfiles(): List<CommandOutputLine> =
        indexProfileService.getAll().entries.flatMapIndexed { index, (name, configuration) ->
            ConfigurationCommandFormatter.format(
                index,
                "Index profile ${configuration.id}",
                buildList {
                    add("Name" to name)
                    if (name == DEFAULT_PROFILE) add("Default" to "yes")
                    add("Embedding model" to configuration.embeddingModel)
                    add("Chunking strategy" to configuration.chunkingStrategy.name)
                    add("Parameters" to ConfigurationCommandFormatter.parameters(configuration.parameters))
                },
            )
        }

    private fun queryProfiles(): List<CommandOutputLine> =
        queryProfileService.getAll().entries.flatMapIndexed { index, (name, configuration) ->
            ConfigurationCommandFormatter.format(
                index,
                "Query profile ${configuration.id}",
                buildList {
                    add("Name" to name)
                    if (name == DEFAULT_PROFILE) add("Default" to "yes")
                    add("Parameters" to ConfigurationCommandFormatter.parameters(configuration.parameters))
                },
            )
        }
}
