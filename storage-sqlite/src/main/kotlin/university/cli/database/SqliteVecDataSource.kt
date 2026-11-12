package university.cli.database

import org.sqlite.SQLiteConfig
import org.sqlite.SQLiteDataSource
import java.sql.Connection
import java.sql.ConnectionBuilder
import java.sql.ShardingKeyBuilder
import javax.sql.DataSource

class SqliteVecDataSource(
    jdbcUrl: String,
    private val sqliteVecExtension: SqliteVecExtension,
    private val delegate: SQLiteDataSource = createDataSource(jdbcUrl),
) : DataSource by delegate {

    override fun getConnection(): Connection =
        delegate.connection.also(sqliteVecExtension::load)

    override fun getConnection(username: String, password: String): Connection =
        delegate.getConnection(username, password).also(sqliteVecExtension::load)

    override fun createConnectionBuilder(): ConnectionBuilder =
        delegate.createConnectionBuilder()

    override fun createShardingKeyBuilder(): ShardingKeyBuilder =
        delegate.createShardingKeyBuilder()

    companion object {
        private fun createDataSource(jdbcUrl: String): SQLiteDataSource {
            val sqliteConfig = SQLiteConfig().apply {
                enableLoadExtension(true)
                enforceForeignKeys(true)
            }
            return SQLiteDataSource(sqliteConfig).apply {
                url = jdbcUrl
            }
        }
    }
}
