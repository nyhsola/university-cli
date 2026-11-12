package university.cli.service.retrieval

import kotlinx.serialization.Serializable
import university.cli.model.LlmResult
import university.cli.model.TokenUsage
import university.cli.service.llm.OllamaService
import university.cli.util.JsonUtil
import university.cli.util.loadResource

class QueryExpansionService(
    private val ollamaService: OllamaService,
) {
    private companion object {
        val PROMPT = QueryExpansionService::class.loadResource("prompts/query_expansion.txt")
    }

    private val cache = mutableMapOf<CacheKey, LlmResult<List<String>>>()

    fun expand(question: String, model: String, rewriteCount: Int): LlmResult<List<String>> {
        require(question.isNotBlank()) { "Question must not be blank" }
        require(model.isNotBlank()) { "Multi-query model must not be blank" }
        require(rewriteCount > 0) { "Multi-query rewrite count must be positive" }

        val key = CacheKey(question, model, rewriteCount)
        return synchronized(cache) {
            cache[key] ?: generate(question, model, rewriteCount).also { cache[key] = it }
        }
    }

    private fun generate(question: String, model: String, rewriteCount: Int): LlmResult<List<String>> {
        val input = QueryExpansionInput(question, rewriteCount)
        val prompt = PROMPT.replace("{{input}}", JsonUtil.json.encodeToString(input))
        val result = ollamaService.question<QueryExpansionResponse>(model, prompt)
        val rewrites = result.value.queries
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.equals(question, ignoreCase = true) }
            .distinctBy(String::lowercase)
            .take(rewriteCount)
        require(rewrites.isNotEmpty()) { "Multi-query model returned no usable query rewrites" }
        return LlmResult(rewrites, result.tokenUsage)
    }

    private data class CacheKey(
        val question: String,
        val model: String,
        val rewriteCount: Int,
    )
}

@Serializable
private data class QueryExpansionInput(
    val question: String,
    val rewriteCount: Int,
)

@Serializable
private data class QueryExpansionResponse(
    val queries: List<String>,
)
