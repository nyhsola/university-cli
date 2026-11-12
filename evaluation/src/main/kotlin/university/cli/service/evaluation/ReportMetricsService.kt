package university.cli.service.evaluation

import university.cli.model.AnswerVerdict
import university.cli.model.ContextSelectionStats
import university.cli.model.GoldChunk
import university.cli.model.RelevantChunk
import university.cli.model.ReportProfileSummary
import university.cli.model.ReportQuestionResult
import university.cli.model.ReportRetrievedChunk
import university.cli.model.ReportStatus
import university.cli.model.RetrievalEvaluation
import kotlin.math.ln

class ReportMetricsService {
    fun retrieval(
        goldChunks: List<GoldChunk>,
        chunks: List<RelevantChunk>,
        contextStats: ContextSelectionStats,
    ): RetrievalEvaluation {
        val gold = goldChunks.associate { it.chunkId to it.relevance }
        val ranked = chunks.map(RelevantChunk::chunkId)
        return RetrievalEvaluation(
            contextCharBudget = contextStats.contextCharBudget,
            maxChunks = contextStats.maxChunks,
            contextUsedChars = contextStats.contextUsedChars,
            effectiveTopK = contextStats.effectiveTopK,
            availableCandidates = contextStats.availableCandidates,
            recallAt3 = metric(gold) { recallAt(ranked, gold.keys, 3) },
            recallAt5 = metric(gold) { recallAt(ranked, gold.keys, 5) },
            reciprocalRank = metric(gold) { reciprocalRank(ranked, gold.keys) },
            ndcgAt3 = metric(gold) { ndcgAt(ranked, gold, 3) },
            ndcgAt5 = metric(gold) { ndcgAt(ranked, gold, 5) },
            retrievedChunks = chunks.map { chunk ->
                ReportRetrievedChunk(
                    chunk.chunkId,
                    chunk.denseRank,
                    chunk.lexicalRank,
                    chunk.rrfScore,
                )
            },
        )
    }

    fun summarize(results: List<ReportQuestionResult>): List<ReportProfileSummary> =
        results.groupBy { it.indexProfileId to it.queryProfileId }.map { (profileIds, runs) ->
            val evaluations = runs.mapNotNull { it.judge.evaluation }
            ReportProfileSummary(
                indexProfileId = profileIds.first,
                queryProfileId = profileIds.second,
                runs = runs.size,
                successfulRuns = runs.count { it.status == ReportStatus.COMPLETED },
                completeAnswers = evaluations.count { it.verdict == AnswerVerdict.COMPLETE },
                incompleteAnswers = evaluations.count { it.verdict == AnswerVerdict.NOT_COMPLETE },
                incorrectAnswers = evaluations.count { it.verdict == AnswerVerdict.INCORRECT },
                judgeErrors = runs.count { it.judge.error != null },
                contextCharBudget = runs.mapNotNull { it.retrieval.contextCharBudget }.distinct().singleOrNull(),
                maxChunks = runs.mapNotNull { it.retrieval.maxChunks }.distinct().singleOrNull(),
                meanContextUsedChars = runs.mapNotNull { it.retrieval.contextUsedChars?.toDouble() }.averageOrNull(),
                meanEffectiveTopK = runs.mapNotNull { it.retrieval.effectiveTopK?.toDouble() }.averageOrNull(),
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
        }.sortedWith(compareBy(ReportProfileSummary::indexProfileId, ReportProfileSummary::queryProfileId))

    private fun recallAt(ranked: List<Long>, gold: Set<Long>, k: Int): Double =
        ranked.take(k).toSet().count { it in gold }.toDouble() / gold.size

    private fun reciprocalRank(ranked: List<Long>, gold: Set<Long>): Double {
        val rank = ranked.indexOfFirst { it in gold }
        return if (rank < 0) 0.0 else 1.0 / (rank + 1)
    }

    private fun ndcgAt(ranked: List<Long>, gold: Map<Long, Int>, k: Int): Double {
        val dcg = ranked.take(k).mapIndexed { index, chunkId ->
            gold.getOrDefault(chunkId, 0) / log2(index + 2)
        }.sum()
        val ideal = gold.values.sortedDescending().take(k).mapIndexed { index, relevance ->
            relevance / log2(index + 2)
        }.sum()
        return if (ideal == 0.0) 0.0 else dcg / ideal
    }

    private fun metric(gold: Map<Long, Int>, calculate: () -> Double): Double? =
        if (gold.isEmpty()) null else calculate()

    private fun log2(value: Int): Double = ln(value.toDouble()) / ln(2.0)

    private fun List<Double>.averageOrNull(): Double? = takeIf { it.isNotEmpty() }?.average()
}
