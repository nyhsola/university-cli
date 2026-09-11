package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class QuestionConfiguration(
    val id: Long,
    val parameters: Map<String, String>,
) {
    init {
        require(id > 0) { "Configuration id must be positive" }
    }
}
