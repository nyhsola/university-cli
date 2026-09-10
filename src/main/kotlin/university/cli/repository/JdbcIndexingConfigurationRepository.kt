package university.cli.repository

import university.cli.model.ChunkingStrategy
import university.cli.model.IndexingConfiguration
import university.cli.model.IndexingStatus
import university.cli.model.IndexedFileInfo
import java.time.Instant
import javax.sql.DataSource

class JdbcIndexingConfigurationRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        const val SELECT_COLUMNS = """
            id, documentId, embeddingModel, strategy, status, parameters, hash, chunkFile, createdAt
        """
        const val FIND_BY_ID = "SELECT $SELECT_COLUMNS FROM indexing_configuration WHERE id = ?"
        const val FIND_BY_HASH = "SELECT $SELECT_COLUMNS FROM indexing_configuration WHERE hash = ?"
        const val FIND_BY_STATUS = """
            SELECT $SELECT_COLUMNS
            FROM indexing_configuration
            WHERE status = ?
            ORDER BY id
        """
        const val LIST_FILES = """
            SELECT
                configuration.id AS configurationId,
                document.id AS documentId,
                document.fileName,
                configuration.status
            FROM indexing_configuration configuration
            JOIN document ON document.id = configuration.documentId
            ORDER BY configuration.id
        """
        const val INSERT = """
            INSERT INTO indexing_configuration(
                documentId, embeddingModel, strategy, status, parameters, hash, chunkFile, createdAt
            ) VALUES (?, ?, ?, ?, ?, ?, NULL, ?)
        """
        const val MARK_READY = """
            UPDATE indexing_configuration SET status = ?, chunkFile = ? WHERE id = ?
        """
        const val UPDATE_STATUS = "UPDATE indexing_configuration SET status = ? WHERE id = ?"
        const val RESTART = """
            UPDATE indexing_configuration
            SET status = ?, chunkFile = NULL
            WHERE id = ?
        """
    }

    fun findById(id: Long): IndexingConfiguration? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_ID).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toConfiguration() else null
            }
        }
    }

    fun findByHash(hash: String): IndexingConfiguration? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_HASH).use { statement ->
            statement.setString(1, hash)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toConfiguration() else null
            }
        }
    }

    fun findAllByStatus(status: IndexingStatus): List<IndexingConfiguration> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_BY_STATUS).use { statement ->
                statement.setInt(1, status.code)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) add(resultSet.toConfiguration())
                    }
                }
            }
        }

    fun listFiles(): List<IndexedFileInfo> = dataSource.connection.use { connection ->
        connection.prepareStatement(LIST_FILES).use { statement ->
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            IndexedFileInfo(
                                resultSet.getLong("configurationId"),
                                resultSet.getLong("documentId"),
                                resultSet.getString("fileName"),
                                IndexingStatus.fromCode(resultSet.getInt("status")),
                            ),
                        )
                    }
                }
            }
        }
    }

    fun create(
        documentId: Long,
        embeddingModel: String,
        strategy: ChunkingStrategy,
        status: IndexingStatus,
        parameters: String,
        hash: String,
        createdAt: Instant,
    ): IndexingConfiguration = dataSource.connection.use { connection ->
        connection.prepareStatement(INSERT).use { statement ->
            statement.setLong(1, documentId)
            statement.setString(2, embeddingModel)
            statement.setInt(3, strategy.code)
            statement.setInt(4, status.code)
            statement.setString(5, parameters)
            statement.setString(6, hash)
            statement.setString(7, createdAt.toString())
            statement.executeUpdate()
        }

        val id = connection.createStatement().use { statement ->
            statement.executeQuery("SELECT last_insert_rowid()").use { resultSet ->
                check(resultSet.next()) { "Configuration id was not generated" }
                resultSet.getLong(1)
            }
        }

        IndexingConfiguration(id, documentId, embeddingModel, strategy, status, parameters, hash, null, createdAt)
    }

    fun markReady(id: Long, chunkFile: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(MARK_READY).use { statement ->
                statement.setInt(1, IndexingStatus.READY.code)
                statement.setString(2, chunkFile)
                statement.setLong(3, id)
                check(statement.executeUpdate() == 1) { "Configuration not found: $id" }
            }
        }
    }

    fun restart(id: Long): IndexingConfiguration {
        dataSource.connection.use { connection ->
            connection.prepareStatement(RESTART).use { statement ->
                statement.setInt(1, IndexingStatus.PROCESSING.code)
                statement.setLong(2, id)
                check(statement.executeUpdate() == 1) { "Configuration not found: $id" }
            }
        }
        return checkNotNull(findById(id)) { "Configuration not found after restart: $id" }
    }

    fun updateStatus(id: Long, status: IndexingStatus) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(UPDATE_STATUS).use { statement ->
                statement.setInt(1, status.code)
                statement.setLong(2, id)
                check(statement.executeUpdate() == 1) { "Configuration not found: $id" }
            }
        }
    }

    private fun java.sql.ResultSet.toConfiguration() = IndexingConfiguration(
        getLong("id"),
        getLong("documentId"),
        getString("embeddingModel"),
        ChunkingStrategy.fromCode(getInt("strategy")),
        IndexingStatus.fromCode(getInt("status")),
        getString("parameters"),
        getString("hash"),
        getString("chunkFile"),
        Instant.parse(getString("createdAt")),
    )
}
