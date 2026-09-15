package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class QueryProfile(
    val id: Long,
    val name: String,
    val description: String,
    val parameters: Map<String, String>,
) {
    init {
        require(id > 0) { "Profile id must be positive" }
        require(name.isNotBlank()) { "Profile name must not be blank" }
        require(description.isNotBlank()) { "Profile description must not be blank" }
    }
}
