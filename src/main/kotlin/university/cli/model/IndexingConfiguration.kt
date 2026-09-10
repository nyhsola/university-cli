package university.cli.model

import java.time.Instant

data class IndexingConfiguration(
    val id: Long,
    val documentId: Long,
    val embeddingModel: String,
    val strategy: ChunkingStrategy,
    val status: IndexingStatus,
    val parameters: String,
    val hash: String,
    val chunkFile: String?,
    val createdAt: Instant,
)

enum class ChunkingStrategy(val code: Int) {
    FIXED_SIZE(0);

    companion object {
        fun fromCode(code: Int): ChunkingStrategy = entries.firstOrNull { it.code == code }
            ?: error("Unknown chunking strategy code: $code")
    }
}

enum class IndexingStatus(val code: Int) {
    PROCESSING(0),
    READY(1),
    FAILED(2),
    CANCELLED(3);

    companion object {
        fun fromCode(code: Int): IndexingStatus = entries.firstOrNull { it.code == code }
            ?: error("Unknown indexing status code: $code")
    }
}
