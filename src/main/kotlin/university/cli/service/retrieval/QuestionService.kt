package university.cli.service.retrieval

import university.cli.config.OllamaConfig
import university.cli.model.StructuredAnswer
import university.cli.service.llm.OllamaService

class QuestionService(
    private val relevantService: RelevantService,
    private val ollamaService: OllamaService,
    private val ollamaConfig: OllamaConfig,
) {
    private companion object {
        const val CONTEXT_CHUNKS = 3
    }

    fun question(
        configurationId: Long,
        question: String,
        onContextReady: () -> Unit = {},
    ): StructuredAnswer {
        val relevantChunks = relevantService.getTopRelevant(configurationId, question, CONTEXT_CHUNKS)
        require(relevantChunks.isNotEmpty()) { "No relevant chunks found" }

        val context = relevantChunks.joinToString("\n\n") { chunk ->
            "[Chunk ${chunk.chunkId}]\n${chunk.content}"
        }
        val prompt = """
            Answer the question using only the provided context.
            If the context does not contain the answer, say that you do not know.

            Context:
            $context

            Question:
            $question
        """.trimIndent()

        onContextReady()
        return ollamaService.question<StructuredAnswer>(ollamaConfig.questionModel, prompt)
    }
}
