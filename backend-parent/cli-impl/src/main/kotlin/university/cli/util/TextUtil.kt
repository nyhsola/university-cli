package university.cli.util

object TextUtil {
    fun wrap(text: String, width: Int): List<String> {
        require(width > 0) { "Width must be positive" }
        return text.lines().flatMap { line -> wrapLine(line, width) }
    }

    private fun wrapLine(line: String, width: Int): List<String> {
        if (line.isEmpty()) return listOf("")

        val result = mutableListOf<String>()
        var remaining = line.trimEnd()
        while (remaining.length > width) {
            val spaceIndex = remaining.take(width + 1).lastIndexOf(' ')
            val breakIndex = spaceIndex.takeIf { it > 0 } ?: width
            result += remaining.take(breakIndex).trimEnd()
            remaining = remaining.drop(breakIndex).trimStart()
        }
        result += remaining
        return result
    }
}
