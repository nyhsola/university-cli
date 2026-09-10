package university.cli.util

import kotlinx.serialization.json.Json

object JsonUtil {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun objectOf(values: Map<String, String>): String = json.encodeToString(values)
}
