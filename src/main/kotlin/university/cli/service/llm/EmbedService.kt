package university.cli.service.llm

import university.cli.model.LlmResult
import university.cli.model.Vector
import university.cli.util.loadResource

class EmbedService(
    private val ollamaService: OllamaService,
) {
    private companion object {
        val QWEN3_QUERY_INSTRUCTION = EmbedService::class
            .loadResource("prompts/qwen3_embedding_query.txt")
            .trim()
    }

    fun embedDocument(model: String, text: String): LlmResult<Vector> = ollamaService.embed(model, text)

    fun embedQuery(model: String, text: String): LlmResult<Vector> {
        require(text.isNotBlank()) { "Embedding query must not be blank" }
        val input = if (model.contains("qwen3-embedding", ignoreCase = true)) {
            "Instruct: $QWEN3_QUERY_INSTRUCTION\nQuery: $text"
        } else {
            text
        }
        return ollamaService.embed(model, input)
    }
}
