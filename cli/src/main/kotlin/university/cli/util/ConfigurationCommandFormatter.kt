package university.cli.util

import university.cli.command.CommandMessageType
import university.cli.command.CommandOutputLine

internal object ConfigurationCommandFormatter {
    private const val BOX_INNER_WIDTH = 66
    private const val BOX_CONTENT_WIDTH = BOX_INNER_WIDTH - 2

    fun format(index: Int, title: String, fields: List<Pair<String, String>>): List<CommandOutputLine> = buildList {
        if (index > 0) add(CommandOutputLine(""))
        add(CommandOutputLine(topBorder(title), CommandMessageType.ACCENT))
        fields.forEach { (label, value) ->
            TextUtil.wrap("$label: $value", BOX_CONTENT_WIDTH).forEach { line ->
                add(CommandOutputLine(row(line)))
            }
        }
        add(CommandOutputLine("╰${"─".repeat(BOX_INNER_WIDTH)}╯", CommandMessageType.ACCENT))
    }

    fun parameters(parameters: Map<String, String>): String = parameters.entries
        .sortedBy(Map.Entry<String, String>::key)
        .joinToString { (key, value) -> "$key=$value" }
        .ifBlank { "—" }

    private fun topBorder(title: String): String {
        val decoratedTitle = "─ $title "
        return "╭$decoratedTitle${"─".repeat((BOX_INNER_WIDTH - decoratedTitle.length).coerceAtLeast(0))}╮"
    }

    private fun row(value: String): String =
        "│ ${value.take(BOX_CONTENT_WIDTH).padEnd(BOX_CONTENT_WIDTH)} │"
}
