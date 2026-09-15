package university.cli.repository

import university.cli.model.Document
import university.cli.util.loadResource
import javax.sql.DataSource

class JdbcDocumentRepository(
    private val dataSource: DataSource,
) {
    private companion object {
        val FIND_BY_ID = JdbcDocumentRepository::class.loadResource("db/sql/document/find_by_id.sql")
        val FIND_BY_FILE_NAME = JdbcDocumentRepository::class.loadResource("db/sql/document/find_by_file_name.sql")
        val INSERT = JdbcDocumentRepository::class.loadResource("db/sql/document/insert.sql")
        val DELETE_ALL = JdbcDocumentRepository::class.loadResource("db/sql/document/delete_all.sql")
        val DELETE_IF_UNINDEXED = JdbcDocumentRepository::class
            .loadResource("db/sql/document/delete_if_unindexed.sql")
    }

    fun findById(id: Long): Document? = dataSource.connection.use { connection ->
        connection.prepareStatement(FIND_BY_ID).use { statement ->
            statement.setLong(1, id)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.toDocument() else null
            }
        }
    }

    fun save(fileName: String): Document = dataSource.connection.use { connection ->
        connection.prepareStatement(INSERT).use { statement ->
            statement.setString(1, fileName)
            statement.executeUpdate()
        }

        connection.prepareStatement(FIND_BY_FILE_NAME).use { statement ->
            statement.setString(1, fileName)
            statement.executeQuery().use { resultSet ->
                check(resultSet.next()) { "Document was not persisted" }
                resultSet.toDocument()
            }
        }
    }

    fun deleteAll(): Int = dataSource.connection.use { connection ->
        connection.prepareStatement(DELETE_ALL).use { statement ->
            statement.executeUpdate()
        }
    }

    fun deleteIfUnindexed(fileName: String): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(DELETE_IF_UNINDEXED).use { statement ->
            statement.setString(1, fileName)
            statement.executeUpdate() == 1
        }
    }

    private fun java.sql.ResultSet.toDocument() = Document(
        getLong("id"),
        getString("fileName"),
    )
}
