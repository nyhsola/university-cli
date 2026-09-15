package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class EvaluationComparison(
    val datasets: List<ReportDatasetReference>,
    val status: ReportStatus,
    val profileCombinations: List<ProfileCombinationComparison>,
    val datasetComparisons: List<DatasetComparison>,
    val indexProfiles: List<IndexProfileComparison>,
    val leaders: ComparisonLeaders,
)

@Serializable
data class DatasetComparison(
    val datasetId: Long,
    val datasetName: String,
    val profileCombinations: List<ProfileCombinationComparison>,
)

@Serializable
data class ProfileCombinationComparison(
    val indexProfileId: Long,
    val indexProfileName: String,
    val queryProfileId: Long,
    val queryProfileName: String,
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
data class IndexProfileComparison(
    val indexProfileId: Long,
    val indexProfileName: String,
    val queryProfiles: Int,
    val runs: Int,
    val successfulRuns: Int,
    val completeAnswers: Int,
    val incompleteAnswers: Int,
    val incorrectAnswers: Int,
    val judgeErrors: Int,
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
data class ComparisonLeaders(
    val mostCompleteAnswers: List<Long>,
    val bestMeanJudgeScore: List<Long>,
    val bestMeanRecallAt3: List<Long>,
    val bestMeanRecallAt5: List<Long>,
    val bestMeanReciprocalRank: List<Long>,
    val bestMeanNdcgAt3: List<Long>,
    val bestMeanNdcgAt5: List<Long>,
)
