package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class DocumentChunk(
    val id: Long,
    val content: String,
)
