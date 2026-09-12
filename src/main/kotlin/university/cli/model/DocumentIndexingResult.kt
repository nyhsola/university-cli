package university.cli.model

data class DocumentIndexingResult(
    val configurationId: Long,
    val documentId: Long,
    val chunksCount: Int,
    val outcome: DocumentIndexingOutcome,
)

enum class DocumentIndexingOutcome {
    INDEXED,
    SKIPPED,
}
