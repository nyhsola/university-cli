package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class ResearchSource(
    val id: String,
    val indexId: Long,
    val chunkId: Long,
    val fileName: String,
    val content: String,
    val documentId: Long,
)

@Serializable
data class ResearchClaim(
    val text: String,
    val sourceIds: List<String>,
)

@Serializable
data class ResearchAnswer(
    val claims: List<ResearchClaim>,
    val unresolved: List<String>,
)

@Serializable
enum class ResearchAction {
    SEARCH,
    FINISH,
}

@Serializable
data class ResearchDecision(
    val action: ResearchAction,
    val query: String,
    val purpose: String,
)

@Serializable
data class ResearchDecisionLog(
    val decision: ResearchDecision,
    val contextSourceIds: List<String>,
    val tokenUsage: TokenUsage,
)

@Serializable
data class ResearchStep(
    val number: Int,
    val query: String,
    val purpose: String,
    val foundChunks: Int,
    val newSources: Int,
    val sourceIds: List<String>,
    val tokenUsage: TokenUsage,
)

@Serializable
enum class ResearchStopReason {
    FINISHED,
    SEARCH_LIMIT,
    REPEATED_QUERY,
    NO_PROGRESS,
    NO_EVIDENCE,
}

@Serializable
data class ResearchTrace(
    val queryProfileId: Long,
    val retrievalMode: String,
    val model: String,
    val decisions: List<ResearchDecisionLog>,
    val steps: List<ResearchStep>,
    val sources: List<ResearchSource>,
    val contextSourceIds: List<String>,
    val stopReason: ResearchStopReason,
    val tokenUsage: TokenUsage,
    val scope: String = "all",
)

data class ResearchResult(
    val answer: ResearchAnswer,
    val trace: ResearchTrace,
    val contextStats: ContextSelectionStats,
)

data class ResearchProgress(
    val status: String,
    val detail: String? = null,
    val alwaysShow: Boolean = false,
)
