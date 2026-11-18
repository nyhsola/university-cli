package university.cli.service.retrieval

import university.cli.model.LexicalMatch
import university.cli.repository.JdbcLexicalRepository

class LexicalSearchService(
    private val repository: JdbcLexicalRepository,
) {
    private companion object {
        val TERM_PATTERN = Regex("[\\p{L}\\p{N}_]+")
    }

    fun getTopRelevant(indexConfigurationId: Long, question: String, limit: Int): List<LexicalMatch> {
        require(indexConfigurationId > 0) { "Index configuration id must be positive" }
        require(question.isNotBlank()) { "Question must not be blank" }
        require(limit > 0) { "Limit must be positive" }

        val query = TERM_PATTERN.findAll(question)
            .map { it.value }
            .distinct()
            .joinToString(" OR ") { term -> "\"$term\"" }
        if (query.isBlank()) return emptyList()

        return repository.findTopRelevant(indexConfigurationId, query, limit)
    }
}
