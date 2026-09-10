package university.cli.service.indexing

import kotlinx.serialization.encodeToString
import university.cli.config.DirectoryConfig
import university.cli.model.DocumentChunk
import university.cli.util.JsonUtil
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ChunkFileWriterService(
    private val directoryConfig: DirectoryConfig,
) {
    fun write(configurationId: Long, chunks: List<DocumentChunk>): Path {
        Files.createDirectories(directoryConfig.chunksDirectory)
        val target = directoryConfig.chunksDirectory.resolve("chunks_$configurationId.jsonl")
        val temporary = Files.createTempFile(directoryConfig.chunksDirectory, "chunks_${configurationId}_", ".tmp")

        try {
            Files.newBufferedWriter(temporary, StandardCharsets.UTF_8).use { writer ->
                chunks.forEach { content ->
                    writer.appendLine(JsonUtil.json.encodeToString(content))
                }
            }
            move(temporary, target)
            return target
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    fun resolve(fileName: String): Path = directoryConfig.chunksDirectory.resolve(fileName)

    private fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
