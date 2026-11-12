package university.cli.repository

import university.cli.model.RelevantVector
import university.cli.model.Vector
import university.cli.util.SqliteVectorUtil
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcVectorRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val FIND_TOP_RELEVANT = JdbcVectorRepository::class.loadResource("db/sql/vector/find_top_relevant.sql")
    }

    fun findTopRelevant(indexConfigurationId: Long, query: Vector, limit: Int): List<RelevantVector> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_TOP_RELEVANT).use { statement ->
                statement.setBytes(1, SqliteVectorUtil.toFloat32Blob(query))
                statement.setLong(2, indexConfigurationId)
                statement.setInt(3, limit)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                RelevantVector(
                                    resultSet.getLong("id"),
                                    resultSet.getLong("indexConfigurationId"),
                                    SqliteVectorUtil.fromFloat32Blob(resultSet.getBytes("vector")),
                                    resultSet.getDouble("distance"),
                                ),
                            )
                        }
                    }
                }
            }
        }
}
