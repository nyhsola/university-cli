package university.cli.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class IndexConfiguration(
    val id: Long,
    val embeddingModel: String,
    val strategy: ChunkingStrategy,
    val parameters: Map<String, String>,
    @Transient val hash: String = "",
) {
    init {
        require(id > 0) { "Configuration id must be positive" }
        require(embeddingModel.isNotBlank()) { "Embedding model must not be blank" }
    }
}
