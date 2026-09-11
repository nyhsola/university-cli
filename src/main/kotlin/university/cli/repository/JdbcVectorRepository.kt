package university.cli.repository

import university.cli.model.RelevantVector
import university.cli.model.Vector
import university.cli.model.VectorRecord
import university.cli.util.SqliteVectorUtil
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcVectorRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val DELETE_BY_CONFIGURATION = JdbcVectorRepository::class.loadResource("db/sql/vector/delete_by_configuration.sql")
        val INSERT = JdbcVectorRepository::class.loadResource("db/sql/vector/insert.sql")
        val FIND_TOP_RELEVANT = JdbcVectorRepository::class.loadResource("db/sql/vector/find_top_relevant.sql")
    }

    fun replace(indexConfigurationId: Long, vectors: List<VectorRecord>) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(DELETE_BY_CONFIGURATION).use { statement ->
                    statement.setLong(1, indexConfigurationId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(INSERT).use { statement ->
                    vectors.forEach { record ->
                        statement.setLong(1, record.id)
                        statement.setLong(2, record.indexConfigurationId)
                        statement.setBytes(3, SqliteVectorUtil.toFloat32Blob(record.vector))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
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

    fun deleteByConfiguration(indexConfigurationId: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(DELETE_BY_CONFIGURATION).use { statement ->
                statement.setLong(1, indexConfigurationId)
                statement.executeUpdate()
            }
        }
    }
}
