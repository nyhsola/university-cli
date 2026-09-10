package university.cli.service.indexing

import kotlinx.serialization.decodeFromString
import university.cli.config.DirectoryConfig
import university.cli.model.DocumentChunk
import university.cli.util.JsonUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ChunkFileReaderService(
    private val directoryConfig: DirectoryConfig,
) {
    fun read(fileName: String): Map<Long, DocumentChunk> {
        val file = directoryConfig.chunksDirectory.resolve(fileName).normalize()
        require(file.startsWith(directoryConfig.chunksDirectory.normalize())) { "Invalid chunks file: $fileName" }
        require(Files.isRegularFile(file)) { "Chunks file does not exist: $file" }

        return Files.newBufferedReader(file, StandardCharsets.UTF_8).useLines { lines ->
            lines
                .filter(String::isNotBlank)
                .map { line -> JsonUtil.json.decodeFromString<DocumentChunk>(line) }
                .associateBy(DocumentChunk::id)
        }
    }
}
