package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class EvaluationReport(
    val datasets: List<ReportDatasetReference>,
    val startedAt: String,
    val completedAt: String,
    val durationMs: Long,
    val status: ReportStatus,
    val indexProfiles: List<ReportProfile>,
    val queryProfiles: List<ReportProfile>,
    val indexing: List<ReportIndexing>,
    val summary: List<ReportProfileSummary>,
    val questions: List<ReportQuestionResult>,
)

@Serializable
data class ReportDatasetReference(
    val id: Long,
    val name: String,
    val description: String,
    val datasetFile: String,
    val sourceFile: String,
)

@Serializable
data class ReportProfile(
    val id: Long,
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
)

@Serializable
data class ReportIndexing(
    val datasetId: Long,
    val indexProfileId: Long,
    val indexConfigurationId: Long? = null,
    val status: ReportStatus,
    val durationMs: Long,
    val indexedFiles: Int,
    val skippedFiles: Int,
    val failedFiles: Int,
    val inputTokens: Long,
    val operationLog: String,
    val error: String? = null,
)

@Serializable
data class ReportQuestionResult(
    val datasetId: Long,
    val questionId: String,
    val indexProfileId: Long,
    val queryProfileId: Long,
    val question: String,
    val expectedAnswer: String,
    val actualAnswer: String? = null,
    val tags: Set<String>,
    val status: ReportStatus,
    val durationMs: Long,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val operationLog: String? = null,
    val error: String? = null,
    val retrieval: RetrievalEvaluation,
    val judge: JudgeReport,
)

@Serializable
data class RetrievalEvaluation(
    val contextCharBudget: Int? = null,
    val maxChunks: Int? = null,
    val contextUsedChars: Int? = null,
    val effectiveTopK: Int? = null,
    val availableCandidates: Int? = null,
    val recallAt3: Double? = null,
    val recallAt5: Double? = null,
    val reciprocalRank: Double? = null,
    val ndcgAt3: Double? = null,
    val ndcgAt5: Double? = null,
    val retrievedChunks: List<ReportRetrievedChunk> = emptyList(),
)

@Serializable
data class ReportRetrievedChunk(
    val chunkId: Long,
    val denseRank: Int? = null,
    val lexicalRank: Int? = null,
    val rrfScore: Double? = null,
)

@Serializable
data class JudgeReport(
    val evaluation: AnswerEvaluation? = null,
    val durationMs: Long = 0,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val error: String? = null,
)

@Serializable
data class ReportProfileSummary(
    val indexProfileId: Long,
    val queryProfileId: Long,
    val runs: Int,
    val successfulRuns: Int,
    val completeAnswers: Int,
    val incompleteAnswers: Int,
    val incorrectAnswers: Int,
    val judgeErrors: Int,
    val contextCharBudget: Int? = null,
    val maxChunks: Int? = null,
    val meanContextUsedChars: Double? = null,
    val meanEffectiveTopK: Double? = null,
    val meanAvailableCandidates: Double? = null,
    val meanJudgeScore: Double? = null,
    val meanRecallAt3: Double? = null,
    val meanRecallAt5: Double? = null,
    val meanReciprocalRank: Double? = null,
    val meanNdcgAt3: Double? = null,
    val meanNdcgAt5: Double? = null,
)

@Serializable
enum class ReportStatus {
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
}

data class ReportGenerationResult(
    val reportFile: java.nio.file.Path,
    val comparisonFile: java.nio.file.Path,
    val status: ReportStatus,
    val questionsCount: Int,
    val completeAnswers: Int,
    val incompleteAnswers: Int,
    val incorrectAnswers: Int,
    val errors: Int,
)
