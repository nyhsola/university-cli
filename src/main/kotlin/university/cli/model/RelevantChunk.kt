package university.cli.model

data class RelevantChunk(
    val configurationId: Long,
    val chunkId: Long,
    val documentId: Long,
    val fileName: String,
    val content: String,
    val distance: Double? = null,
    val bm25Score: Double? = null,
    val denseRank: Int? = null,
    val lexicalRank: Int? = null,
    val rrfScore: Double? = null,
)
