package university.cli.model

data class LexicalMatch(
    val chunkId: Long,
    val indexConfigurationId: Long,
    val bm25Score: Double,
)
