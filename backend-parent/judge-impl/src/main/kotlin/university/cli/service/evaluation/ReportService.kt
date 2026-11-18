package university.cli.service.evaluation

import university.cli.database.FlywayMigrator
import university.cli.model.AnswerVerdict
import university.cli.model.EvaluationReport
import university.cli.model.IndexConfiguration
import university.cli.model.JudgeReport
import university.cli.model.ProjectIndexingResult
import university.cli.model.QueryProfile
import university.cli.model.ReportAnswerMode
import university.cli.model.ReportDatasetReference
import university.cli.model.ReportGenerationResult
import university.cli.model.ReportIndexing
import university.cli.model.ReportProfile
import university.cli.model.ReportQuestion
import university.cli.model.ReportQuestionResult
import university.cli.model.ReportStatus
import university.cli.model.RetrievalEvaluation
import university.cli.model.SearchScope
import university.cli.model.TokenUsage
import university.cli.service.configuration.IndexProfileService
import university.cli.service.configuration.QueryProfileService
import university.cli.service.indexing.ProjectIndexingService
import university.cli.service.operation.OperationCancellationService
import university.cli.service.operation.OperationLogService
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CancellationException

class ReportService(
    private val migrator: FlywayMigrator,
    private val datasetService: ReportDatasetService,
    private val indexProfileService: IndexProfileService,
    private val queryProfileService: QueryProfileService,
    private val projectIndexingService: ProjectIndexingService,
    private val answerService: ReportAnswerService,
    private val judgeService: JudgeService,
    private val metricsService: ReportMetricsService,
    private val comparisonService: ReportComparisonService,
    private val reportWriter: ReportWriter,
    private val operationLogService: OperationLogService,
    private val cancellationService: OperationCancellationService,
) {
    fun generate(datasetDirectory: String, onProgress: (String) -> Unit = {}): ReportGenerationResult {
        val startedAt = Instant.now()
        val loadedDatasets = datasetService.loadAll(datasetDirectory)
        val firstDataset = loadedDatasets.first().dataset
        val indexProfiles = firstDataset.indexProfileIds.map(indexProfileService::get)
        val queryProfiles = firstDataset.queryProfileIds.map(queryProfileService::get)

        migrator.migrate()
        cancellationService.ensureActive()

        val indexedDatasets = indexDatasets(loadedDatasets, indexProfiles, onProgress)
        val questionResults = evaluateQuestions(indexedDatasets, queryProfiles, onProgress)

        val completedAt = Instant.now()
        val indexings = indexedDatasets.map(IndexedReportDataset::indexing)
        val status = reportStatus(indexings, questionResults)
        val report = EvaluationReport(
            datasets = loadedDatasets.map { loaded ->
                ReportDatasetReference(
                    loaded.dataset.id,
                    loaded.dataset.name,
                    loaded.dataset.description,
                    reportWriter.relative(loaded.datasetFile),
                    loaded.relativeSourceFile,
                )
            },
            startedAt = startedAt.toString(),
            completedAt = completedAt.toString(),
            durationMs = Duration.between(startedAt, completedAt).toMillis(),
            status = status,
            indexProfiles = indexProfiles.map { ReportProfile(it.id, it.name, it.description, it.parameters) },
            queryProfiles = queryProfiles.map {
                ReportProfile(it.id, it.name, it.description, it.parameters, it.multiQuery)
            },
            indexing = indexings,
            summary = metricsService.summarize(questionResults),
            questions = questionResults,
            answerMode = firstDataset.answerMode,
        )
        val writtenReports = reportWriter.write(startedAt, report, comparisonService.create(report))
        val evaluations = questionResults.mapNotNull { it.judge.evaluation }
        return ReportGenerationResult(
            writtenReports.reportFile,
            writtenReports.comparisonFile,
            status,
            questionResults.size,
            evaluations.count { it.verdict == AnswerVerdict.COMPLETE },
            evaluations.count { it.verdict == AnswerVerdict.NOT_COMPLETE },
            evaluations.count { it.verdict == AnswerVerdict.INCORRECT },
            indexings.sumOf(ReportIndexing::failedFiles) +
                questionResults.count { it.error != null || it.judge.error != null },
        )
    }

    private fun indexDatasets(loadedDatasets: List<LoadedReportDataset>, indexProfiles: List<IndexConfiguration>, onProgress: (String) -> Unit): List<IndexedReportDataset> {
        val totalIndexings = loadedDatasets.size * indexProfiles.size
        var currentIndexing = 0
        return buildList {
            loadedDatasets.forEach { loaded ->
                indexProfiles.forEach { profile ->
                    currentIndexing++
                    onProgress(
                        "Indexing ${loaded.dataset.name} with index profile ${profile.id} " +
                            "($currentIndexing/$totalIndexings)",
                    )
                    add(IndexedReportDataset(loaded, index(loaded, profile.id, onProgress)))
                }
            }
        }
    }

    private fun evaluateQuestions(indexedDatasets: List<IndexedReportDataset>, queryProfiles: List<QueryProfile>, onProgress: (String) -> Unit): List<ReportQuestionResult> {
        val totalRuns = indexedDatasets.sumOf { it.loaded.dataset.questions.size } * queryProfiles.size
        var currentRun = 0
        return buildList {
            indexedDatasets.forEach { indexed ->
                val dataset = indexed.loaded.dataset
                dataset.questions.forEach { question ->
                    queryProfiles.forEach { queryProfile ->
                        cancellationService.ensureActive()
                        currentRun++
                        onProgress(
                            "Evaluating $currentRun/$totalRuns: dataset ${dataset.id}, question ${question.id}, " +
                                "index profile ${indexed.indexing.indexProfileId}, " +
                                "query profile ${queryProfile.id}",
                        )
                        val indexConfigurationId = indexed.indexing.indexConfigurationId
                        add(
                            if (indexConfigurationId == null) {
                                failedEvaluation(
                                    dataset.id,
                                    question,
                                    indexed.indexing.indexProfileId,
                                    queryProfile.id,
                                    indexed.indexing.error ?: "Indexing did not produce a ready index",
                                )
                            } else {
                                evaluate(
                                    dataset.id,
                                    question,
                                    indexed.indexing.indexProfileId,
                                    indexConfigurationId,
                                    queryProfile.id,
                                    dataset.answerMode,
                                    onProgress,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun index(loaded: LoadedReportDataset, indexProfileId: Long, onProgress: (String) -> Unit): ReportIndexing {
        val startedAt = Instant.now()
        val result = projectIndexingService.indexFile(
            loaded.sourceFile,
            indexProfileService.get(indexProfileId),
            { progress -> onProgress("Indexing file ${progress.path}") },
            { progress -> onProgress("Indexing chunks: ${progress.percentage}%") },
        )
        val logFile = operationLogService.writeIndexing(
            startedAt,
            "file:${loaded.relativeSourceFile}",
            indexProfileId,
            result,
        )
        val error = result.failedFiles.joinToString("; ") { "${it.path}: ${it.message}" }.ifBlank { null }
        val indexedFile = (result.indexedFiles + result.skippedFiles).singleOrNull()
        return ReportIndexing(
            datasetId = loaded.dataset.id,
            indexProfileId = indexProfileId,
            indexConfigurationId = indexedFile?.result?.configurationId,
            status = result.status(),
            durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
            indexedFiles = result.indexedFiles.size,
            skippedFiles = result.skippedFiles.size,
            failedFiles = result.failedFiles.size,
            inputTokens = result.tokenUsage().inputTokens,
            operationLog = reportWriter.relative(logFile),
            error = error,
        )
    }

    @Suppress("TooGenericExceptionCaught")
    private fun evaluate(
        datasetId: Long,
        question: ReportQuestion,
        indexProfileId: Long,
        indexConfigurationId: Long,
        queryProfileId: Long,
        answerMode: ReportAnswerMode,
        onProgress: (String) -> Unit,
    ): ReportQuestionResult {
        val startedAt = Instant.now()
        val scope = SearchScope.Index(indexConfigurationId)
        return try {
            val profile = queryProfileService.get(queryProfileId)
            val answer = answerService.answer(scope, profile, question.question, answerMode, onProgress)
            val logFile = operationLogService.write(
                if (answerMode == ReportAnswerMode.ASK) "ask" else "report-research",
                startedAt,
                scope,
                queryProfileId,
                question.question,
                answer.relevantChunks,
                answer.tokenUsage,
                answer.answer.answer,
            )
            val judge = judge(question, answer.answer.answer)
            ReportQuestionResult(
                datasetId = datasetId,
                questionId = question.id,
                indexProfileId = indexProfileId,
                queryProfileId = queryProfileId,
                question = question.question,
                expectedAnswer = question.expectedAnswer,
                actualAnswer = answer.answer.answer,
                tags = question.tags,
                status = if (judge.error == null) ReportStatus.COMPLETED else ReportStatus.COMPLETED_WITH_ERRORS,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputTokens = answer.tokenUsage.inputTokens,
                outputTokens = answer.tokenUsage.outputTokens,
                operationLog = reportWriter.relative(logFile),
                retrieval = metricsService.retrieval(
                    checkNotNull(question.goldChunksByIndexProfile[indexProfileId]),
                    answer.relevantChunks,
                    answer.contextStats,
                    rankedMetrics = answer.research == null,
                ),
                judge = judge,
                research = answer.research,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            failedEvaluation(
                datasetId,
                question,
                indexProfileId,
                queryProfileId,
                error.message ?: error.javaClass.simpleName,
                Duration.between(startedAt, Instant.now()).toMillis(),
            )
        }
    }

    private fun failedEvaluation(
        datasetId: Long,
        question: ReportQuestion,
        indexProfileId: Long,
        queryProfileId: Long,
        error: String,
        durationMs: Long = 0,
    ): ReportQuestionResult = ReportQuestionResult(
        datasetId = datasetId,
        questionId = question.id,
        indexProfileId = indexProfileId,
        queryProfileId = queryProfileId,
        question = question.question,
        expectedAnswer = question.expectedAnswer,
        tags = question.tags,
        status = ReportStatus.FAILED,
        durationMs = durationMs,
        error = error,
        retrieval = RetrievalEvaluation(),
        judge = JudgeReport(error = "Answer was not generated"),
    )

    @Suppress("TooGenericExceptionCaught")
    private fun judge(question: ReportQuestion, actualAnswer: String): JudgeReport {
        val startedAt = Instant.now()
        return try {
            val result = judgeService.evaluate(question.question, question.expectedAnswer, actualAnswer)
            JudgeReport(
                evaluation = result.value,
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                inputTokens = result.tokenUsage.inputTokens,
                outputTokens = result.tokenUsage.outputTokens,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            JudgeReport(
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                error = error.message ?: error.javaClass.simpleName,
            )
        }
    }

    private fun reportStatus(indexing: List<ReportIndexing>, questions: List<ReportQuestionResult>): ReportStatus = when {
        indexing.all { it.status == ReportStatus.FAILED } -> ReportStatus.FAILED
        indexing.any { it.status != ReportStatus.COMPLETED } -> ReportStatus.COMPLETED_WITH_ERRORS
        questions.any { it.status != ReportStatus.COMPLETED } -> ReportStatus.COMPLETED_WITH_ERRORS
        else -> ReportStatus.COMPLETED
    }

    private fun ProjectIndexingResult.status(): ReportStatus = when {
        failedFiles.isEmpty() -> ReportStatus.COMPLETED
        indexedFiles.isEmpty() && skippedFiles.isEmpty() -> ReportStatus.FAILED
        else -> ReportStatus.COMPLETED_WITH_ERRORS
    }

    private fun ProjectIndexingResult.tokenUsage(): TokenUsage = (indexedFiles + skippedFiles).fold(TokenUsage()) { total, file -> total + file.result.tokenUsage }
}

private data class IndexedReportDataset(
    val loaded: LoadedReportDataset,
    val indexing: ReportIndexing,
)
