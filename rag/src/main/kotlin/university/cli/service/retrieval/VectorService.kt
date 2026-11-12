package university.cli.service.retrieval

import university.cli.model.RelevantVector
import university.cli.model.Vector
import university.cli.repository.JdbcVectorRepository

class VectorService(
    private val vectorRepository: JdbcVectorRepository,
) {
    fun getTopRelevant(indexConfigurationId: Long, query: Vector, limit: Int = 5): List<RelevantVector> {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        require(query.values.isNotEmpty()) { "Query vector must not be empty" }
        require(limit > 0) { "Limit must be positive" }
        return vectorRepository.findTopRelevant(indexConfigurationId, query, limit)
    }
}
