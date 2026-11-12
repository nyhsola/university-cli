package university.cli.repository

import university.cli.model.DocumentChunk
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcChunkRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val FIND_BY_CONFIGURATION = JdbcChunkRepository::class.loadResource("db/sql/chunk/find_by_configuration.sql")
    }

    fun findByConfiguration(indexConfigurationId: Long): Map<Long, DocumentChunk> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_BY_CONFIGURATION).use { statement ->
                statement.setLong(1, indexConfigurationId)
                statement.executeQuery().use { resultSet ->
                    buildMap {
                        while (resultSet.next()) {
                            val chunk = DocumentChunk(
                                resultSet.getLong("chunkId"),
                                resultSet.getString("content"),
                            )
                            put(chunk.id, chunk)
                        }
                    }
                }
            }
        }
}
