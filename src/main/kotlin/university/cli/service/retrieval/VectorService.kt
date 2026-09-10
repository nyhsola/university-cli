package university.cli.service.retrieval

import university.cli.model.RelevantVector
import university.cli.model.Vector
import university.cli.model.VectorRecord
import university.cli.repository.JdbcVectorRepository

class VectorService(
    private val vectorRepository: JdbcVectorRepository,
) {
    fun insertVectors(indexConfigurationId: Long, vectors: List<Vector>): List<VectorRecord> {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        validateVectors(vectors)

        val records = vectors.mapIndexed { index, vector ->
            VectorRecord(index + 1L, indexConfigurationId, vector)
        }
        vectorRepository.replace(indexConfigurationId, records)
        return records
    }

    fun getTopRelevant(indexConfigurationId: Long, query: Vector, limit: Int = 5): List<RelevantVector> {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        require(query.values.isNotEmpty()) { "Query vector must not be empty" }
        require(limit > 0) { "Limit must be positive" }
        return vectorRepository.findTopRelevant(indexConfigurationId, query, limit)
    }

    fun deleteVectors(indexConfigurationId: Long) {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        vectorRepository.deleteByConfiguration(indexConfigurationId)
    }

    private fun validateVectors(vectors: List<Vector>) {
        if (vectors.isEmpty()) return

        val dimensions = vectors.first().values.size
        require(dimensions > 0) { "Vectors must not be empty" }
        require(vectors.all { it.values.size == dimensions }) {
            "All vectors must have the same number of dimensions"
        }
    }
}
