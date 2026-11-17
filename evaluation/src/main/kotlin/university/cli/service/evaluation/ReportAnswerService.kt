package university.cli.service.evaluation

import university.cli.model.ContextSelectionStats
import university.cli.model.QueryProfile
import university.cli.model.RelevantChunk
import university.cli.model.ReportAnswerMode
import university.cli.model.ResearchTrace
import university.cli.model.SearchScope
import university.cli.model.StructuredAnswer
import university.cli.model.TokenUsage
import university.cli.service.research.ResearchService
import university.cli.service.retrieval.AskService

class ReportAnswerService(
    private val askService: AskService,
    private val researchService: ResearchService,
) {
    fun answer(
        scope: SearchScope,
        profile: QueryProfile,
        question: String,
        mode: ReportAnswerMode,
        onProgress: (String) -> Unit,
    ): ReportAnswer {
        if (mode == ReportAnswerMode.ASK) {
            val result = askService.ask(scope, profile, question)
            return ReportAnswer(result.answer, result.relevantChunks, result.tokenUsage, result.contextStats)
        }
        val result = researchService.research(question, profile, scope) { onProgress(it.status) }
        val sourcesById = result.trace.sources.associateBy { it.id }
        val sources = result.trace.contextSourceIds.map(sourcesById::getValue)
        val chunks = sources.map { source ->
            RelevantChunk(source.indexId, source.chunkId, source.documentId, source.fileName, source.content)
        }
        val answer = buildList {
            addAll(result.answer.claims.map { "${it.text.trim()} [${it.sourceIds.distinct().joinToString(", ")}]" })
            if (result.answer.unresolved.isNotEmpty()) {
                add("Unresolved:")
                addAll(result.answer.unresolved)
            }
        }.joinToString("\n")
        return ReportAnswer(
            StructuredAnswer(answer), chunks, result.trace.tokenUsage,
            result.contextStats,
            result.trace,
        )
    }
}

data class ReportAnswer(
    val answer: StructuredAnswer,
    val relevantChunks: List<RelevantChunk>,
    val tokenUsage: TokenUsage,
    val contextStats: ContextSelectionStats,
    val research: ResearchTrace? = null,
)
