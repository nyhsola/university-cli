package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class IndexingOperationLog(
    val command: String,
    val startedAt: String,
    val completedAt: String,
    val durationMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val scope: String,
    val indexProfileId: Long,
    val files: List<IndexedFileLog>,
)

@Serializable
data class IndexedFileLog(
    val fileName: String,
    val status: IndexingFileLogStatus,
    val indexId: Long? = null,
    val documentId: Long? = null,
    val chunksCount: Int? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val error: String? = null,
)

@Serializable
enum class IndexingFileLogStatus {
    INDEXED,
    SKIPPED,
    FAILED,
}
