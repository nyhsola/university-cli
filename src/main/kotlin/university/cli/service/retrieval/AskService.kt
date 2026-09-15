package university.cli.service.retrieval

import university.cli.config.OllamaConfig
import university.cli.model.AskResult
import university.cli.model.QueryProfile
import university.cli.model.SearchScope
import university.cli.model.StructuredAnswer
import university.cli.service.llm.OllamaService

class AskService(
    private val searchService: SearchService,
    private val ollamaService: OllamaService,
    private val ollamaConfig: OllamaConfig,
    private val contextFormatter: ContextFormatter,
) {
    fun ask(
        scope: SearchScope,
        queryProfile: QueryProfile,
        question: String,
        onContextReady: () -> Unit = {},
    ): AskResult {
        val searchResult = searchService.search(scope, queryProfile, question)
        val relevantChunks = searchResult.chunks
        require(relevantChunks.isNotEmpty()) { "No relevant chunks found" }

        val context = contextFormatter.render(relevantChunks)
        val prompt = """
            Answer the question using only the provided relevant chunks.
            The chunks may have been selected by dense vector search, lexical FTS5/BM25 search,
            or a fusion of both rankings. Treat retrieval metadata only as provenance, not as evidence.
            If the context does not contain the answer, say that you do not know.

            Relevant chunks:
            $context

            Question:
            $question
        """.trimIndent()

        onContextReady()
        val answer = ollamaService.question<StructuredAnswer>(ollamaConfig.questionModel, prompt)
        return AskResult(
            answer.value,
            relevantChunks,
            searchResult.tokenUsage + answer.tokenUsage,
            searchResult.contextStats,
        )
    }
}
