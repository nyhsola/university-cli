package university.cli.service.operation

import university.cli.config.DirectoryConfig
import university.cli.model.OperationLog
import university.cli.model.RelevantChunk
import university.cli.model.RetrievedChunkLog
import university.cli.model.SearchScope
import university.cli.util.JsonUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class OperationLogService(
    directoryConfig: DirectoryConfig,
) {
    private val logsDirectory = directoryConfig.dataDirectory.resolve("logs")

    fun write(
        command: String,
        startedAt: Instant,
        scope: SearchScope,
        queryProfileId: Long,
        query: String,
        chunks: List<RelevantChunk>,
        answer: String? = null,
        outputFile: String? = null,
    ): Path {
        val completedAt = Instant.now()
        val log = OperationLog(
            command,
            startedAt.toString(),
            completedAt.toString(),
            scope.description(),
            queryProfileId,
            query,
            chunks.map { chunk ->
                RetrievedChunkLog(
                    chunk.configurationId,
                    chunk.chunkId,
                    chunk.fileName,
                    chunk.denseRank,
                    chunk.distance,
                    chunk.lexicalRank,
                    chunk.bm25Score,
                    chunk.rrfScore,
                    chunk.content,
                )
            },
            answer,
            outputFile,
        )
        Files.createDirectories(logsDirectory)
        val timestamp = completedAt.toString().replace(":", "-")
        val file = logsDirectory.resolve("$timestamp-${log.command}-${UUID.randomUUID()}.json")
        Files.writeString(
            file,
            JsonUtil.json.encodeToString(log) + System.lineSeparator(),
            StandardCharsets.UTF_8,
        )
        return file
    }

    private fun SearchScope.description(): String = when (this) {
        SearchScope.All -> "all"
        is SearchScope.File -> "file:$fileName"
        is SearchScope.Index -> "index:$id"
    }
}
