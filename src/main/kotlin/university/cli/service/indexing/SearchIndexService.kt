package university.cli.service.indexing

import university.cli.model.DocumentChunk
import university.cli.model.Vector
import university.cli.model.VectorRecord
import university.cli.repository.JdbcSearchIndexRepository

class SearchIndexService(
    private val repository: JdbcSearchIndexRepository,
) {
    fun replace(indexConfigurationId: Long, chunks: List<DocumentChunk>, vectors: List<Vector>) {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        require(chunks.size == vectors.size) { "Every chunk must have exactly one vector" }

        val dimensions = vectors.firstOrNull()?.values?.size
        require(dimensions == null || dimensions > 0) { "Vectors must not be empty" }
        require(dimensions == null || vectors.all { it.values.size == dimensions }) {
            "All vectors must have the same number of dimensions"
        }

        val records = chunks.zip(vectors) { chunk, vector ->
            VectorRecord(chunk.id, indexConfigurationId, vector)
        }
        repository.replace(indexConfigurationId, chunks, records)
    }
}
