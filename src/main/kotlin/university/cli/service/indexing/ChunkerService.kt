package university.cli.service.indexing

import university.cli.model.ChunkingStrategy
import university.cli.model.DocumentChunk

interface ChunkerService {
    val strategy: ChunkingStrategy

    fun chunk(content: String, parameters: Map<String, String>): List<DocumentChunk>
}

class FixedSizeChunkerService : ChunkerService {
    override val strategy = ChunkingStrategy.FIXED_SIZE

    private companion object {
        const val CHUNK_SIZE = "chunkSize"
    }

    override fun chunk(content: String, parameters: Map<String, String>): List<DocumentChunk> {
        val chunkSize = parameters.requiredPositiveInt(CHUNK_SIZE)
        return content.chunked(chunkSize).mapIndexed { index, chunk ->
            DocumentChunk(index + 1L, chunk)
        }
    }

    private fun Map<String, String>.requiredPositiveInt(name: String): Int {
        val value = get(name)?.toIntOrNull()
        require(value != null && value > 0) { "$name must be a positive integer" }
        return value
    }
}
