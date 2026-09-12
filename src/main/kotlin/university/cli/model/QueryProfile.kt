package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class QueryProfile(
    val id: Long,
    val parameters: Map<String, String>,
) {
    init {
        require(id > 0) { "Profile id must be positive" }
    }
}
