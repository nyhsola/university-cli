package university.cli.model

import java.nio.file.Path

data class DocumentIndexingResult(
    val configurationId: Long,
    val documentId: Long,
    val chunksCount: Int,
    val chunksFile: Path?,
    val outcome: DocumentIndexingOutcome,
)

enum class DocumentIndexingOutcome {
    INDEXED,
    SKIPPED,
}
