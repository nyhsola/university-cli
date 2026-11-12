package university.cli.repository

import university.cli.model.DocumentChunk
import university.cli.model.VectorRecord
import university.cli.util.SqliteVectorUtil
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcSearchIndexRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val DELETE_CHUNKS = JdbcSearchIndexRepository::class.loadResource("db/sql/search_index/delete_chunks.sql")
        val DELETE_VECTORS = JdbcSearchIndexRepository::class.loadResource("db/sql/search_index/delete_vectors.sql")
        val INSERT_CHUNK = JdbcSearchIndexRepository::class.loadResource("db/sql/search_index/insert_chunk.sql")
        val INSERT_VECTOR = JdbcSearchIndexRepository::class.loadResource("db/sql/search_index/insert_vector.sql")
    }

    fun replace(
        indexConfigurationId: Long,
        chunks: List<DocumentChunk>,
        vectors: List<VectorRecord>,
    ) {
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(DELETE_VECTORS).use { statement ->
                    statement.setLong(1, indexConfigurationId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(DELETE_CHUNKS).use { statement ->
                    statement.setLong(1, indexConfigurationId)
                    statement.executeUpdate()
                }
                connection.prepareStatement(INSERT_VECTOR).use { statement ->
                    vectors.forEach { record ->
                        statement.setLong(1, record.id)
                        statement.setLong(2, record.indexConfigurationId)
                        statement.setBytes(3, SqliteVectorUtil.toFloat32Blob(record.vector))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(INSERT_CHUNK).use { statement ->
                    chunks.forEach { chunk ->
                        statement.setString(1, chunk.content)
                        statement.setLong(2, indexConfigurationId)
                        statement.setLong(3, chunk.id)
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
}
