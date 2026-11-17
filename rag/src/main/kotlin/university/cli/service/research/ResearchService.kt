package university.cli.service.research

import university.cli.config.OllamaConfig
import university.cli.model.QueryProfile
import university.cli.model.SearchScope
import university.cli.model.ResearchAction
import university.cli.model.ResearchAnswer
import university.cli.model.ResearchDecision
import university.cli.model.ResearchDecisionLog
import university.cli.model.ResearchOperationLog
import university.cli.model.ResearchProgress
import university.cli.model.ResearchResult
import university.cli.model.ResearchStep
import university.cli.model.ResearchStopReason
import university.cli.model.ResearchTrace
import university.cli.model.TokenUsage
import university.cli.service.operation.OperationCancellationService
import university.cli.service.operation.OperationLogService
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CancellationException

class ResearchService(
    private val searchService: ResearchSearchService,
    private val decisionService: ResearchDecisionService,
    private val answerService: ResearchAnswerService,
    private val cancellationService: OperationCancellationService,
    private val operationLogService: OperationLogService,
    private val ollamaConfig: OllamaConfig,
) {
    private companion object {
        const val MAX_SEARCHES = 3
        const val MAX_QUERY_LENGTH = 1000
        const val MAX_QUESTION_LENGTH = 1000
        const val MAX_EMPTY_SEARCHES = 2
    }

    fun research(
        question: String,
        profile: QueryProfile,
        scope: SearchScope = SearchScope.All,
        onProgress: (ResearchProgress) -> Unit = {},
    ): ResearchResult {
        require(question.isNotBlank()) { "Question must not be blank" }
        require(question.length <= MAX_QUESTION_LENGTH) { "Question must be at most $MAX_QUESTION_LENGTH characters" }
        val startedAt = Instant.now()
        val run = ResearchRun(profile, ollamaConfig.questionModel, scope)
        var answer: ResearchAnswer? = null
        var status = "failed"
        var failure: Exception? = null
        try {
            cancellationService.ensureActive()
            val effectiveProfile = searchService.prepareProfile(profile, scope)
            run.retrievalMode = effectiveProfile.parameters.getValue("retrievalMode")
            if (run.retrievalMode != profile.parameters["retrievalMode"]) {
                onProgress(ResearchProgress(
                    "Preparing research...",
                    "Using lexical search across indexes with different embedding models.",
                    alwaysShow = true,
                ))
            }
            collect(question.trim(), effectiveProfile, run, onProgress)
            answer = generateAnswer(question.trim(), run, onProgress)
            cancellationService.ensureActive()
            status = "completed"
            return ResearchResult(answer, run.trace(), run.evidence.contextStats())
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            status = "cancelled"
            failure = error
            throw error
        } catch (error: CancellationException) {
            status = "cancelled"
            failure = error
            throw error
        } catch (error: Exception) {
            failure = error
            throw error
        } finally {
            val completedAt = Instant.now()
            val log = ResearchOperationLog(
                "research", startedAt.toString(), completedAt.toString(),
                Duration.between(startedAt, completedAt).toMillis(), question,
                status, run.trace(), answer, failure?.let { it.message ?: it.javaClass.simpleName },
            )
            val interrupted = Thread.interrupted()
            try {
                operationLogService.writeResearch(log)
            } catch (logError: Exception) {
                if (failure == null) throw logError
                failure.addSuppressed(logError)
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
        }
    }

    private fun collect(
        question: String,
        profile: QueryProfile,
        run: ResearchRun,
        onProgress: (ResearchProgress) -> Unit,
    ) {
        var decision = ResearchDecision(ResearchAction.SEARCH, question, "Find initial evidence")
        val queries = mutableSetOf<String>()
        var emptySearches = 0
        repeat(MAX_SEARCHES) { index ->
            cancellationService.ensureActive()
            val query = decision.query.trim().replace(Regex("\\s+"), " ")
            check(query.isNotBlank() && query.length <= MAX_QUERY_LENGTH) {
                "Research model must return a search query of 1 to $MAX_QUERY_LENGTH characters"
            }
            if (!queries.add(query.lowercase(Locale.ROOT))) {
                run.stopReason = ResearchStopReason.REPEATED_QUERY
                return
            }
            onProgress(ResearchProgress("Searching ${index + 1}/$MAX_SEARCHES...", "[${index + 1}/$MAX_SEARCHES] Search: $query"))
            val result = searchService.search(profile, query, run.scope)
            run.tokenUsage += result.tokenUsage
            cancellationService.ensureActive()
            val added = run.evidence.add(result.chunks)
            run.steps += ResearchStep(
                index + 1, query, decision.purpose, result.chunks.size, added.size,
                result.chunks.mapNotNull { chunk ->
                    run.evidence.sources.find { it.indexId == chunk.configurationId && it.chunkId == chunk.chunkId }?.id
                }.distinct(),
                result.tokenUsage,
            )
            onProgress(ResearchProgress("Reviewing evidence...", "Found ${result.chunks.size} chunks, ${added.size} new sources."))
            emptySearches = if (added.isEmpty()) emptySearches + 1 else 0
            if (emptySearches >= MAX_EMPTY_SEARCHES) {
                run.stopReason = ResearchStopReason.NO_PROGRESS
                return
            }
            if (index == MAX_SEARCHES - 1) {
                run.stopReason = ResearchStopReason.SEARCH_LIMIT
                return
            }
            val context = run.evidence.context()
            val next = decisionService.decide(question, run.steps.toList(), context, MAX_SEARCHES - index - 1)
            run.tokenUsage += next.tokenUsage
            run.decisions += ResearchDecisionLog(next.value, context.map { it.id }, next.tokenUsage)
            cancellationService.ensureActive()
            decision = next.value
            if (decision.action == ResearchAction.FINISH) {
                run.stopReason = ResearchStopReason.FINISHED
                return
            }
        }
    }

    private fun generateAnswer(
        question: String,
        run: ResearchRun,
        onProgress: (ResearchProgress) -> Unit,
    ): ResearchAnswer {
        val context = run.evidence.context()
        run.contextSourceIds = context.map { it.id }
        if (context.isEmpty()) {
            run.stopReason = ResearchStopReason.NO_EVIDENCE
            return ResearchAnswer(emptyList(), listOf("No supporting evidence was found within this search."))
        }
        onProgress(ResearchProgress("Generating answer..."))
        var correction = ""
        repeat(2) {
            cancellationService.ensureActive()
            val result = answerService.generate(question, context, run.stopReason, correction)
            run.tokenUsage += result.tokenUsage
            cancellationService.ensureActive()
            val error = answerService.validationError(result.value, context)
            if (error == null) return result.value
            correction = error
        }
        error("Research model returned an invalid answer: $correction")
    }

    private class ResearchRun(profile: QueryProfile, private val model: String, val scope: SearchScope) {
        private val queryProfileId = profile.id
        val evidence = ResearchEvidence()
        val decisions = mutableListOf<ResearchDecisionLog>()
        val steps = mutableListOf<ResearchStep>()
        var retrievalMode = profile.parameters["retrievalMode"].orEmpty()
        var contextSourceIds = emptyList<String>()
        var stopReason = ResearchStopReason.NO_EVIDENCE
        var tokenUsage = TokenUsage()

        fun trace(): ResearchTrace = ResearchTrace(
            queryProfileId, retrievalMode, model, decisions.toList(), steps.toList(),
            evidence.sources, contextSourceIds, stopReason, tokenUsage,
            scope = when (scope) {
                SearchScope.All -> "all"
                is SearchScope.Index -> "index:${scope.id}"
                is SearchScope.File -> "file:${scope.fileName}"
            },
        )
    }
}
