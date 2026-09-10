package university.cli.model

data class IndexParameters(
    val model: String,
    val strategy: ChunkingStrategy,
    val parameters: Map<String, String>,
) {
    companion object {
        fun default() = IndexParameters(
            "qwen3-embedding:8b",
            ChunkingStrategy.FIXED_SIZE,
            mapOf("chunkSize" to "1000"),
        )
    }

    init {
        require(model.isNotBlank()) { "Embedding model must not be blank" }
    }
}
