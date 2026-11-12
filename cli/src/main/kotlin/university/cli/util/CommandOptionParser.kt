package university.cli.util

data class ParsedCommandArguments(
    val positionals: List<String>,
    val options: Map<String, String>,
    val flags: Set<String>,
)

object CommandOptionParser {
    fun parse(
        arguments: List<String>,
        valueOptions: Map<String, String> = emptyMap(),
        flagOptions: Map<String, String> = emptyMap(),
    ): ParsedCommandArguments {
        val positionals = mutableListOf<String>()
        val options = mutableMapOf<String, String>()
        val flags = mutableSetOf<String>()
        var index = 0

        while (index < arguments.size) {
            val argument = arguments[index]
            val valueName = valueOptions[argument]
            val flagName = flagOptions[argument]
            when {
                valueName != null -> {
                    require(valueName !in options) { "Option $argument may only be specified once" }
                    val value = arguments.getOrNull(index + 1)
                    require(value != null && !value.startsWith("--")) { "Option $argument requires a value" }
                    options[valueName] = value.trim('"')
                    index += 2
                }

                flagName != null -> {
                    require(flags.add(flagName)) { "Option $argument may only be specified once" }
                    index++
                }

                argument.startsWith('-') -> error("Unknown option: $argument")
                else -> {
                    positionals += argument
                    index++
                }
            }
        }
        return ParsedCommandArguments(positionals, options, flags)
    }
}
