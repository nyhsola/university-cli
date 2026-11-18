package university.cli.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class IndexConfiguration(
    val id: Long,
    val name: String,
    val description: String,
    val embeddingModel: String,
    val chunkingStrategy: ChunkingStrategy,
    val parameters: Map<String, String>,
    @Transient val hash: String = "",
) {
    init {
        require(id > 0) { "Configuration id must be positive" }
        require(name.isNotBlank()) { "Configuration name must not be blank" }
        require(description.isNotBlank()) { "Configuration description must not be blank" }
        require(embeddingModel.isNotBlank()) { "Embedding model must not be blank" }
    }
}
