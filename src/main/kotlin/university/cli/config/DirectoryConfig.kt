package university.cli.config

import java.nio.file.Path

data class DirectoryConfig(
    val dataDirectory: Path,
    val chunksDirectory: Path,
) {
    companion object {
        private const val DATA_DIRECTORY = ".data"
        private const val CHUNKS_DIRECTORY = "chunks"

        fun fromWorkingDirectory(): DirectoryConfig {
            val dataDirectory = Path.of(System.getProperty("user.dir"), DATA_DIRECTORY)
                .toAbsolutePath()
                .normalize()

            return DirectoryConfig(dataDirectory, dataDirectory.resolve(CHUNKS_DIRECTORY))
        }
    }
}
