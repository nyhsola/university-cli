package university.cli.database

import org.flywaydb.core.Flyway
import university.cli.config.DirectoryConfig
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger
import javax.sql.DataSource

class FlywayMigrator(
    private val dataSource: DataSource,
    private val directoryConfig: DirectoryConfig,
) {
    fun migrate() {
        Files.createDirectories(directoryConfig.dataDirectory)
        val flywayLogger = Logger.getLogger("org.flywaydb")
        val previousLevel = flywayLogger.level
        flywayLogger.level = Level.WARNING
        try {
            Flyway.configure()
                .dataSource(dataSource)
                .load()
                .migrate()
        } finally {
            flywayLogger.level = previousLevel
        }
    }
}
