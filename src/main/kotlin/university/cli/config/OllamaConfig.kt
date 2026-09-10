package university.cli.config

import java.net.URI
import java.time.Duration

data class OllamaConfig(
    val baseUrl: URI,
    val requestTimeout: Duration,
    val questionModel: String,
) {
    companion object {
        fun localhost() = OllamaConfig(
            URI.create("http://localhost:11434"),
            Duration.ofHours(1),
            "qwen3.5:9b",
        )
    }

    fun endpoint(path: String): URI = URI.create("${baseUrl.toString().trimEnd('/')}/${path.trimStart('/')}")
}
