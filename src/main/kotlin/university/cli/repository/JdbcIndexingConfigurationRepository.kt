package university.cli.repository

import university.cli.model.IndexedFileInfo
import university.cli.model.IndexingConfiguration
import university.cli.model.IndexingStatus
import university.cli.util.loadResource
import java.time.Instant
import javax.sql.DataSource

class JdbcIndexingConfigurationRepository(private val dataSource: DataSource) {
    private companion object {
        val FIND_BY_ID = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/find_by_id.sql")
        val FIND_BY_DOCUMENT_AND_HASH = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/find_by_document_and_hash.sql")
        val FIND_BY_STATUS = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/find_by_status.sql")
        val FIND_ALL_WITH_DOCUMENTS = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/find_all_with_documents.sql")
        val INSERT = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/insert.sql")
        val LAST_INSERT_ID = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/last_insert_id.sql")
        val MARK_READY = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/mark_ready.sql")
        val UPDATE_STATUS = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/update_status.sql")
        val RESTART = JdbcIndexingConfigurationRepository::class
            .loadResource("db/sql/indexing_configuration/restart.sql")
    }

    fun findById(id: Long): IndexingConfiguration? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_ID).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toConfiguration() else null
            }
        }
    }

    fun findByDocumentAndHash(documentId: Long, hash: String): IndexingConfiguration? =
        dataSource.connection.use { connection ->
            connection.prepareStatement(FIND_BY_DOCUMENT_AND_HASH).use { statement ->
                statement.setLong(1, documentId)
                statement.setString(2, hash)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toConfiguration() else null
                }
            }
        }

    fun findAllByStatus(status: IndexingStatus): List<IndexingConfiguration> = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_STATUS).use { statement ->
            statement.setInt(1, status.code)
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) add(resultSet.toConfiguration())
                }
            }
        }
    }

    fun findAllWithDocuments(): List<IndexedFileInfo> = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_ALL_WITH_DOCUMENTS).use { statement ->
            statement.executeQuery().use { resultSet ->
                buildList {
                    while (resultSet.next()) {
                        add(
                            IndexedFileInfo(
                                resultSet.getLong("configurationId"),
                                resultSet.getLong("documentId"),
                                resultSet.getString("fileName"),
                                resultSet.getString("documentHash"),
                                resultSet.getString("configurationHash"),
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
        hash: String,
        documentHash: String,
        status: IndexingStatus,
        createdAt: Instant,
    ): IndexingConfiguration = dataSource.connection.use { connection ->
        connection.prepareStatement(INSERT).use { statement ->
            statement.setLong(1, documentId)
            statement.setString(2, hash)
            statement.setString(3, documentHash)
            statement.setInt(4, status.code)
            statement.setString(5, createdAt.toString())
            statement.executeUpdate()
        }

        val id = connection.createStatement().use { statement ->
            statement.executeQuery(LAST_INSERT_ID).use { resultSet ->
                check(resultSet.next()) { "Configuration id was not generated" }
                resultSet.getLong(1)
            }
        }
        IndexingConfiguration(id, documentId, hash, documentHash, status, createdAt)
    }

    fun markReady(id: Long) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(MARK_READY).use { statement ->
                statement.setInt(1, IndexingStatus.READY.code)
                statement.setLong(2, id)
                check(statement.executeUpdate() == 1) { "Configuration not found: $id" }
            }
        }
    }

    fun restart(id: Long, documentHash: String): IndexingConfiguration {
        dataSource.connection.use { connection ->
            connection.prepareStatement(RESTART).use { statement ->
                statement.setInt(1, IndexingStatus.PROCESSING.code)
                statement.setString(2, documentHash)
                statement.setLong(3, id)
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
        getString("hash"),
        getString("documentHash"),
        IndexingStatus.fromCode(getInt("status")),
        Instant.parse(getString("createdAt")),
    )
}
