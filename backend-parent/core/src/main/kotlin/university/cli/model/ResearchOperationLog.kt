package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class ResearchOperationLog(
    val command: String,
    val startedAt: String,
    val completedAt: String,
    val durationMs: Long,
    val question: String,
    val status: String,
    val trace: ResearchTrace,
    val answer: ResearchAnswer?,
    val error: String?,
)
