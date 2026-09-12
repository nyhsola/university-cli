package university.cli.service.llm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import university.cli.config.OllamaConfig
import university.cli.model.LlmResult
import university.cli.model.TokenUsage
import university.cli.model.Vector
import university.cli.util.JsonSchemaUtil
import university.cli.util.JsonUtil
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class OllamaService(
    private val config: OllamaConfig,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val json: Json = JsonUtil.json,
) {
    private companion object {
        const val EMBED_ENDPOINT = "api/embed"
        const val GENERATE_ENDPOINT = "api/generate"
        const val CONTENT_TYPE = "application/json"
    }

    internal fun embed(model: String, text: String): LlmResult<Vector> {
        require(model.isNotBlank()) { "Ollama model must not be blank" }
        require(text.isNotBlank()) { "Embedding text must not be blank" }

        val response = json.decodeFromString<EmbedResponse>(
            post(EMBED_ENDPOINT, EmbedRequest(model, text)),
        )
        val values = response.embeddings.firstOrNull() ?: error("Ollama returned no embeddings for model: $model")

        return LlmResult(Vector(values), TokenUsage(inputTokens = response.promptEvalCount))
    }

    inline fun <reified T> question(model: String, text: String): LlmResult<T> =
        question(model, text, serializer())

    @PublishedApi
    internal fun <T> question(model: String, text: String, serializer: KSerializer<T>): LlmResult<T> {
        require(model.isNotBlank()) { "Ollama model must not be blank" }
        require(text.isNotBlank()) { "Question text must not be blank" }

        val response = json.decodeFromString<GenerateResponse>(
            post(
                GENERATE_ENDPOINT,
                GenerateRequest(model, text, false, false, JsonSchemaUtil.from(serializer.descriptor)),
            ),
        )
        check(response.response.isNotBlank()) {
            "Ollama returned an empty response (doneReason=${response.doneReason ?: "unknown"})"
        }
        return LlmResult(
            json.decodeFromString(serializer, response.response),
            TokenUsage(response.promptEvalCount, response.evalCount),
        )
    }

    private inline fun <reified T> post(endpoint: String, payload: T): String {
        val request = HttpRequest.newBuilder(config.endpoint(endpoint))
            .timeout(config.requestTimeout)
            .header("Content-Type", CONTENT_TYPE)
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(payload)))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() !in 200..299) {
            throw OllamaException(response.statusCode(), errorMessage(response.body()))
        }
        return response.body()
    }

    private fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull() ?: body.take(500)
}

class OllamaException(
    statusCode: Int,
    message: String,
) : RuntimeException("Ollama request failed with HTTP $statusCode: $message")

@Serializable
private data class EmbedRequest(
    val model: String,
    val input: String,
)

@Serializable
private data class EmbedResponse(
    val embeddings: List<List<Float>> = emptyList(),
    @SerialName("prompt_eval_count")
    val promptEvalCount: Long = 0,
)

@Serializable
private data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val think: Boolean,
    val format: JsonObject,
)

@Serializable
private data class GenerateResponse(
    val response: String = "",
    val thinking: String = "",
    @SerialName("done_reason")
    val doneReason: String? = null,
    @SerialName("prompt_eval_count")
    val promptEvalCount: Long = 0,
    @SerialName("eval_count")
    val evalCount: Long = 0,
)
