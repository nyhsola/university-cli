package university.cli.service.operation

import university.cli.config.DirectoryConfig
import university.cli.model.IndexedFile
import university.cli.model.IndexedFileLog
import university.cli.model.IndexingFileLogStatus
import university.cli.model.IndexingOperationLog
import university.cli.model.OperationLog
import university.cli.model.ProjectIndexingResult
import university.cli.model.RelevantChunk
import university.cli.model.RetrievedChunkLog
import university.cli.model.SearchScope
import university.cli.model.TokenUsage
import university.cli.util.JsonUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
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
        tokenUsage: TokenUsage,
        answer: String? = null,
    ): Path {
        val completedAt = Instant.now()
        val log = OperationLog(
            command,
            startedAt.toString(),
            completedAt.toString(),
            Duration.between(startedAt, completedAt).toMillis(),
            tokenUsage.inputTokens,
            tokenUsage.outputTokens,
            tokenUsage.totalTokens,
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
            answer
        )
        return write(log.command, completedAt, JsonUtil.json.encodeToString(log))
    }

    fun writeIndexing(
        startedAt: Instant,
        scope: String,
        indexProfileId: Long,
        result: ProjectIndexingResult,
    ): Path {
        val completedAt = Instant.now()
        val tokenUsage = (result.indexedFiles + result.skippedFiles)
            .fold(TokenUsage()) { total, file -> total + file.result.tokenUsage }
        val files = buildList {
            addAll(result.indexedFiles.map { it.toLog(IndexingFileLogStatus.INDEXED) })
            addAll(result.skippedFiles.map { it.toLog(IndexingFileLogStatus.SKIPPED) })
            addAll(
                result.failedFiles.map { file ->
                    IndexedFileLog(
                        fileName = file.path.toString(),
                        status = IndexingFileLogStatus.FAILED,
                        error = file.message,
                    )
                },
            )
        }
        val log = IndexingOperationLog(
            command = "index",
            startedAt = startedAt.toString(),
            completedAt = completedAt.toString(),
            durationMs = Duration.between(startedAt, completedAt).toMillis(),
            inputTokens = tokenUsage.inputTokens,
            outputTokens = tokenUsage.outputTokens,
            totalTokens = tokenUsage.totalTokens,
            scope = scope,
            indexProfileId = indexProfileId,
            files = files,
        )
        return write(log.command, completedAt, JsonUtil.json.encodeToString(log))
    }

    private fun IndexedFile.toLog(status: IndexingFileLogStatus): IndexedFileLog =
        IndexedFileLog(
            fileName = path.toString(),
            status = status,
            indexId = result.configurationId,
            documentId = result.documentId,
            chunksCount = result.chunksCount,
            inputTokens = result.tokenUsage.inputTokens,
            outputTokens = result.tokenUsage.outputTokens,
            totalTokens = result.tokenUsage.totalTokens,
        )

    private fun write(command: String, completedAt: Instant, content: String): Path {
        Files.createDirectories(logsDirectory)
        val timestamp = completedAt.toString()
            .replace(":", "-")
            .replace("T", "_")
            .replace("Z", "")
            .replace(".", "-")
        val file = logsDirectory.resolve("$timestamp-$command-${UUID.randomUUID()}.json")
        Files.writeString(file, content + System.lineSeparator(), StandardCharsets.UTF_8)
        return file
    }

    private fun SearchScope.description(): String = when (this) {
        SearchScope.All -> "all"
        is SearchScope.File -> "file:$fileName"
        is SearchScope.Index -> "index:$id"
    }
}
