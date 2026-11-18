package university.cli.util

import university.cli.command.CommandMessageType
import university.cli.command.CommandOutputLine
import university.cli.command.CommandOutputTable

object CommandTableFormatter {
    private const val FIRST_COLUMN_WIDTH = 24
    private const val TABLE_OVERHEAD = 7

    fun format(table: CommandOutputTable, availableWidth: Int): List<CommandOutputLine> {
        val width = availableWidth.coerceAtLeast(TABLE_OVERHEAD + 2)
        val firstWidth = minOf(FIRST_COLUMN_WIDTH, (width - TABLE_OVERHEAD) / 3).coerceAtLeast(1)
        val secondWidth = width - TABLE_OVERHEAD - firstWidth
        val title = " ${table.title} ".take(width - 3)
        return buildList {
            add(CommandOutputLine("╭─$title${"─".repeat(width - title.length - 3)}╮", CommandMessageType.ACCENT))
            addAll(rows(table.firstColumnTitle, table.secondColumnTitle, firstWidth, secondWidth).map {
                it.copy(type = CommandMessageType.ACCENT)
            })
            add(CommandOutputLine("├${"─".repeat(firstWidth + 2)}┼${"─".repeat(secondWidth + 2)}┤", CommandMessageType.ACCENT))
            table.rows.forEach { (first, second) ->
                addAll(rows(first, second, firstWidth, secondWidth))
            }
            add(CommandOutputLine("╰${"─".repeat(width - 2)}╯", CommandMessageType.ACCENT))
        }.map { it.copy(fullWidth = true) }
    }

    private fun rows(first: String, second: String, firstWidth: Int, secondWidth: Int): List<CommandOutputLine> {
        val firstLines = TextUtil.wrap(first, firstWidth)
        val secondLines = TextUtil.wrap(second, secondWidth)
        return List(maxOf(firstLines.size, secondLines.size)) { index ->
            val firstLine = firstLines.getOrElse(index) { "" }.padEnd(firstWidth)
            val secondLine = secondLines.getOrElse(index) { "" }.padEnd(secondWidth)
            CommandOutputLine("│ $firstLine │ $secondLine │")
        }
    }
}
