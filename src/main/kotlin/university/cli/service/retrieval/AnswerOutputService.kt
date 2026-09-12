package university.cli.service.retrieval

import university.cli.config.DirectoryConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class AnswerOutputService(
    directoryConfig: DirectoryConfig,
) {
    private val projectDirectory = checkNotNull(directoryConfig.dataDirectory.parent) {
        "Data directory must have a project parent"
    }

    fun write(fileName: String, answer: String): Path {
        require(fileName.isNotBlank()) { "Output file must not be blank" }
        val file = projectDirectory.resolve(fileName).toAbsolutePath().normalize()
        require(file.startsWith(projectDirectory)) { "Output file must be inside the project directory" }
        file.parent?.let(Files::createDirectories)
        Files.writeString(file, answer.trim() + System.lineSeparator(), StandardCharsets.UTF_8)
        return file
    }
}
