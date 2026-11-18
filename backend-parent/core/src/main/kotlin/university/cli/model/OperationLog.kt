package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class OperationLog(
    val command: String,
    val startedAt: String,
    val completedAt: String,
    val durationMs: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val scope: String,
    val queryProfileId: Long,
    val query: String,
    val chunks: List<RetrievedChunkLog>,
    val answer: String? = null,
    val outputFile: String? = null,
)

@Serializable
data class RetrievedChunkLog(
    val indexId: Long,
    val chunkId: Long,
    val fileName: String,
    val denseRank: Int? = null,
    val distance: Double? = null,
    val lexicalRank: Int? = null,
    val bm25Score: Double? = null,
    val rrfScore: Double? = null,
    val content: String,
)
