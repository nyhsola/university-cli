package university.cli.service.evaluation

import university.cli.model.AnswerVerdict
import university.cli.model.ComparisonLeaders
import university.cli.model.DatasetComparison
import university.cli.model.EvaluationComparison
import university.cli.model.EvaluationReport
import university.cli.model.IndexProfileComparison
import university.cli.model.ProfileCombinationComparison
import university.cli.model.ReportProfile
import university.cli.model.ReportProfileSummary
import university.cli.model.ReportQuestionResult
import university.cli.model.ReportStatus

class ReportComparisonService(
    private val metricsService: ReportMetricsService,
) {
    fun create(report: EvaluationReport): EvaluationComparison {
        val indexProfiles = report.indexProfiles.associateBy(ReportProfile::id)
        val queryProfiles = report.queryProfiles.associateBy(ReportProfile::id)
        val combinations = combinations(report.summary, indexProfiles, queryProfiles)
        val indexComparisons = indexComparisons(report.questions, indexProfiles)
        val datasetComparisons = report.datasets.map { dataset ->
            val questions = report.questions.filter { it.datasetId == dataset.id }
            DatasetComparison(
                dataset.id,
                dataset.name,
                combinations(metricsService.summarize(questions), indexProfiles, queryProfiles),
            )
        }

        return EvaluationComparison(
            report.datasets,
            report.status,
            combinations,
            datasetComparisons,
            indexComparisons,
            leaders(indexComparisons),
        )
    }

    private fun combinations(
        summaries: List<ReportProfileSummary>,
        indexProfiles: Map<Long, ReportProfile>,
        queryProfiles: Map<Long, ReportProfile>,
    ): List<ProfileCombinationComparison> = summaries.map { summary ->
        ProfileCombinationComparison(
            indexProfileId = summary.indexProfileId,
            indexProfileName = checkNotNull(indexProfiles[summary.indexProfileId]).name,
            queryProfileId = summary.queryProfileId,
            queryProfileName = checkNotNull(queryProfiles[summary.queryProfileId]).name,
            runs = summary.runs,
            successfulRuns = summary.successfulRuns,
            completeAnswers = summary.completeAnswers,
            incompleteAnswers = summary.incompleteAnswers,
            incorrectAnswers = summary.incorrectAnswers,
            judgeErrors = summary.judgeErrors,
            contextCharBudget = summary.contextCharBudget,
            maxChunks = summary.maxChunks,
            meanContextUsedChars = summary.meanContextUsedChars,
            meanEffectiveTopK = summary.meanEffectiveTopK,
            meanAvailableCandidates = summary.meanAvailableCandidates,
            meanJudgeScore = summary.meanJudgeScore,
            meanRecallAt3 = summary.meanRecallAt3,
            meanRecallAt5 = summary.meanRecallAt5,
            meanReciprocalRank = summary.meanReciprocalRank,
            meanNdcgAt3 = summary.meanNdcgAt3,
            meanNdcgAt5 = summary.meanNdcgAt5,
        )
    }

    private fun indexComparisons(
        questions: List<ReportQuestionResult>,
        indexProfiles: Map<Long, ReportProfile>,
    ): List<IndexProfileComparison> = questions.groupBy { it.indexProfileId }.map { (profileId, runs) ->
        val evaluations = runs.mapNotNull { it.judge.evaluation }
        IndexProfileComparison(
            indexProfileId = profileId,
            indexProfileName = checkNotNull(indexProfiles[profileId]).name,
            queryProfiles = runs.map { it.queryProfileId }.distinct().size,
            runs = runs.size,
            successfulRuns = runs.count { it.status == ReportStatus.COMPLETED },
            completeAnswers = evaluations.count { it.verdict == AnswerVerdict.COMPLETE },
            incompleteAnswers = evaluations.count { it.verdict == AnswerVerdict.NOT_COMPLETE },
            incorrectAnswers = evaluations.count { it.verdict == AnswerVerdict.INCORRECT },
            judgeErrors = runs.count { it.judge.error != null },
            meanContextUsedChars = runs.mapNotNull {
                it.retrieval.contextUsedChars?.toDouble()
            }.averageOrNull(),
            meanEffectiveTopK = runs.mapNotNull {
                it.retrieval.effectiveTopK?.toDouble()
            }.averageOrNull(),
            meanAvailableCandidates = runs.mapNotNull {
                it.retrieval.availableCandidates?.toDouble()
            }.averageOrNull(),
            meanJudgeScore = evaluations.map { it.score.toDouble() }.averageOrNull(),
            meanRecallAt3 = runs.mapNotNull { it.retrieval.recallAt3 }.averageOrNull(),
            meanRecallAt5 = runs.mapNotNull { it.retrieval.recallAt5 }.averageOrNull(),
            meanReciprocalRank = runs.mapNotNull { it.retrieval.reciprocalRank }.averageOrNull(),
            meanNdcgAt3 = runs.mapNotNull { it.retrieval.ndcgAt3 }.averageOrNull(),
            meanNdcgAt5 = runs.mapNotNull { it.retrieval.ndcgAt5 }.averageOrNull(),
        )
    }.sortedBy(IndexProfileComparison::indexProfileId)

    private fun leaders(rows: List<IndexProfileComparison>): ComparisonLeaders = ComparisonLeaders(
        leadersByInt(rows, IndexProfileComparison::completeAnswers),
        leadersByDouble(rows, IndexProfileComparison::meanJudgeScore),
        leadersByDouble(rows, IndexProfileComparison::meanRecallAt3),
        leadersByDouble(rows, IndexProfileComparison::meanRecallAt5),
        leadersByDouble(rows, IndexProfileComparison::meanReciprocalRank),
        leadersByDouble(rows, IndexProfileComparison::meanNdcgAt3),
        leadersByDouble(rows, IndexProfileComparison::meanNdcgAt5),
    )

    private fun leadersByInt(
        rows: List<IndexProfileComparison>,
        metric: (IndexProfileComparison) -> Int,
    ): List<Long> {
        val maximum = rows.maxOfOrNull(metric) ?: return emptyList()
        return rows.filter { metric(it) == maximum }.map(IndexProfileComparison::indexProfileId)
    }

    private fun leadersByDouble(
        rows: List<IndexProfileComparison>,
        metric: (IndexProfileComparison) -> Double?,
    ): List<Long> {
        val maximum = rows.mapNotNull(metric).maxOrNull() ?: return emptyList()
        return rows.filter { metric(it) == maximum }.map(IndexProfileComparison::indexProfileId)
    }

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
}
