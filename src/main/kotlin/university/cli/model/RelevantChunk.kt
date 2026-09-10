package university.cli.model

data class RelevantChunk(
    val configurationId: Long,
    val chunkId: Long,
    val documentId: Long,
    val fileName: String,
    val content: String,
    val distance: Double,
)
