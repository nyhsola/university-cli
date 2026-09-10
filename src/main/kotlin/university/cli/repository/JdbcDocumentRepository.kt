package university.cli.repository

import university.cli.model.Document
import javax.sql.DataSource

class JdbcDocumentRepository(
    private val dataSource: DataSource,
) {
    fun findById(id: Long): Document? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_ID).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toDocument() else null
            }
        }
    }

    fun findByHash(fileHash: String): Document? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_HASH).use { statement ->
            statement.setString(1, fileHash)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toDocument() else null
            }
        }
    }

    fun save(fileName: String, fileHash: String): Document = dataSource.connection.use { connection ->
        connection.prepareStatement(INSERT_OR_UPDATE).use { statement ->
            statement.setString(1, fileName)
            statement.setString(2, fileHash)
            statement.executeUpdate()
        }

        connection.prepareStatement(FIND_BY_HASH).use { statement ->
            statement.setString(1, fileHash)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "Document was not persisted" }
                resultSet.toDocument()
            }
        }
    }

    private fun java.sql.ResultSet.toDocument() = Document(
        getLong("id"),
        getString("fileName"),
        getString("fileHash"),
    )

    private companion object {
        const val FIND_BY_ID = "SELECT id, fileName, fileHash FROM document WHERE id = ?"
        const val FIND_BY_HASH = "SELECT id, fileName, fileHash FROM document WHERE fileHash = ?"
        const val INSERT_OR_UPDATE = """
            INSERT INTO document(fileName, fileHash) VALUES (?, ?)
            ON CONFLICT(fileHash) DO UPDATE SET fileName = excluded.fileName
        """
    }
}
