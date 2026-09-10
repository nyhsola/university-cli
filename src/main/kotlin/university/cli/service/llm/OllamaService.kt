package university.cli.service.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import university.cli.config.OllamaConfig
import university.cli.model.Answer
import university.cli.model.Vector
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

    fun embed(model: String, text: String): Vector {
        require(model.isNotBlank()) { "Ollama model must not be blank" }
        require(text.isNotBlank()) { "Embedding text must not be blank" }

        val response = json.decodeFromString<EmbedResponse>(
            post(EMBED_ENDPOINT, EmbedRequest(model, text))
        )
        val values = response.embeddings.firstOrNull() ?: error("Ollama returned no embeddings for model: $model")
        return Vector(values)
    }

    fun question(model: String, text: String): Answer {
        require(model.isNotBlank()) { "Ollama model must not be blank" }
        require(text.isNotBlank()) { "Question text must not be blank" }

        val response = json.decodeFromString<GenerateResponse>(
            post(GENERATE_ENDPOINT, GenerateRequest(model, text, false, false)),
        )
        check(response.response.isNotBlank()) {
            "Ollama returned an empty response (doneReason=${response.doneReason ?: "unknown"})"
        }
        return Answer(response.response)
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
)

@Serializable
private data class GenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean,
    val think: Boolean,
)

@Serializable
private data class GenerateResponse(
    val response: String = "",
    val thinking: String = "",
    @SerialName("done_reason")
    val doneReason: String? = null,
)
