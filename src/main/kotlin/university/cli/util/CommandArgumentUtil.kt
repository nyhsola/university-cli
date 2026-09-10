package university.cli.util

object CommandArgumentUtil {
    fun text(arguments: List<String>, offset: Int = 0): String {
        val value = arguments.drop(offset).joinToString(" ").trim()
        if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
            return value.substring(1, value.lastIndex).trim()
        }
        return value
    }
}
