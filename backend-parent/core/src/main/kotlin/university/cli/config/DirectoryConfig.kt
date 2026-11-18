package university.cli.config

import java.nio.file.Path

data class DirectoryConfig(
    val dataDirectory: Path,
) {
    companion object {
        private const val DATA_DIRECTORY = ".data"

        fun fromWorkingDirectory(): DirectoryConfig {
            val dataDirectory = Path.of(System.getProperty("user.dir"), DATA_DIRECTORY)
                .toAbsolutePath()
                .normalize()

            return DirectoryConfig(dataDirectory)
        }
    }
}
