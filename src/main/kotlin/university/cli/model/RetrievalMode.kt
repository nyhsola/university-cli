package university.cli.model

enum class RetrievalMode {
    DENSE,
    LEXICAL,
    HYBRID;

    companion object {
        fun from(value: String): RetrievalMode = entries.firstOrNull {
            it.name.equals(value, ignoreCase = true)
        } ?: error("Unknown retrieval mode: $value. Expected dense, lexical, or hybrid")
    }
}
