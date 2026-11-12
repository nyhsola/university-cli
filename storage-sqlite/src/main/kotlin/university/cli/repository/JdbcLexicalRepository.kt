package university.cli.repository

import university.cli.model.LexicalMatch
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcLexicalRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val FIND_TOP_RELEVANT = JdbcLexicalRepository::class.loadResource("db/sql/lexical/find_top_relevant.sql")
    }

    fun findTopRelevant(indexConfigurationId: Long, query: String, limit: Int): List<LexicalMatch> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_TOP_RELEVANT).use { statement ->
                statement.setString(1, query)
                statement.setLong(2, indexConfigurationId)
                statement.setInt(3, limit)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                LexicalMatch(
                                    resultSet.getLong("chunkId"),
                                    resultSet.getLong("indexConfigurationId"),
                                    resultSet.getDouble("bm25Score"),
                                ),
                            )
                        }
                    }
                }
            }
        }
}
