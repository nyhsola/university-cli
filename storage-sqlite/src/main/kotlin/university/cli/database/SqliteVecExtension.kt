package university.cli.database

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.Connection

class SqliteVecExtension {
    private val extensionPath: Path by lazy(::extractExtension)

    fun load(connection: Connection) {
        val escapedPath = extensionPath.toAbsolutePath().toString().replace("'", "''")
        connection.createStatement().use { statement ->
            statement.execute("SELECT load_extension('$escapedPath', 'sqlite3_vec_init')")
        }
    }

    private fun extractExtension(): Path {
        val fileName = extensionFileName()
        val resourcePath = "/native/sqlite-vec/$fileName"
        val resource = requireNotNull(javaClass.getResourceAsStream(resourcePath)) {
            "Missing sqlite-vec resource: $resourcePath"
        }
        val extractedFile = Files.createTempFile("sqlite-vec-", "-$fileName")
        resource.use {
            Files.copy(it, extractedFile, StandardCopyOption.REPLACE_EXISTING)
        }
        extractedFile.toFile().deleteOnExit()
        return extractedFile
    }

    private fun extensionFileName(): String {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("win") -> "vec0.dll"
            osName.contains("mac") -> "vec0.dylib"
            else -> "vec0.so"
        }
    }
}
