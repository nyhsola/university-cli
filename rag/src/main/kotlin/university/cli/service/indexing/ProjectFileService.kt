package university.cli.service.indexing

import university.cli.config.DirectoryConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.relativeTo

class ProjectFileService(
    directoryConfig: DirectoryConfig,
) {
    val projectDirectory: Path = checkNotNull(directoryConfig.dataDirectory.parent) {
        "Data directory must have a project parent"
    }
    private val dataDirectory = directoryConfig.dataDirectory

    fun findAll(): List<Path> = Files.walk(projectDirectory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { path -> !path.startsWith(dataDirectory) }
            .filter(::isSupported)
            .sorted(compareBy { it.toString() })
            .toList()
    }

    fun resolve(fileName: String): Path {
        require(fileName.isNotBlank()) { "File path must not be blank" }
        val file = projectDirectory.resolve(fileName).toAbsolutePath().normalize()
        require(file.startsWith(projectDirectory)) { "File must be inside the project directory: $fileName" }
        require(!file.startsWith(dataDirectory)) { "Files inside .data cannot be indexed" }
        require(Files.isRegularFile(file)) { "File does not exist or is not a regular file: $fileName" }
        require(isSupported(file)) { "Only .txt files can be indexed: $fileName" }
        return file
    }

    fun relativeName(file: Path): String = file.relativeTo(projectDirectory).toString().replace('\\', '/')

    private fun isSupported(path: Path): Boolean = path.fileName.toString().endsWith(".txt", ignoreCase = true)
}
