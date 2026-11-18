package university.cli.service.indexing

import university.cli.model.ChunkIndexingProgress
import university.cli.model.DocumentIndexingOutcome
import university.cli.model.DocumentIndexingResult
import university.cli.model.IndexConfiguration
import university.cli.model.IndexingStatus
import university.cli.model.TokenUsage
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.llm.EmbedService
import university.cli.service.operation.OperationCancellationService
import university.cli.util.FileUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CancellationException

class DocumentIndexingService(
    chunkers: List<ChunkerService>,
    private val documentRepository: JdbcDocumentRepository,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val embedService: EmbedService,
    private val searchIndexService: SearchIndexService,
    private val cancellationService: OperationCancellationService,
) {
    private val chunkersByStrategy = chunkers.associateBy(ChunkerService::strategy)

    @Suppress("TooGenericExceptionCaught")
    fun index(source: Path, relativeFileName: String, indexConfiguration: IndexConfiguration, onChunkProgress: (ChunkIndexingProgress) -> Unit): DocumentIndexingResult {
        val file = source.toAbsolutePath().normalize()

        require(Files.isRegularFile(file)) { "File does not exist or is not a regular file: $file" }
        require(indexConfiguration.hash.isNotBlank()) { "Indexing configuration hash is missing" }

        val documentHash = FileUtil.sha256(file)
        val document = documentRepository.save(relativeFileName)
        val existingConfiguration = configurationRepository.findByDocumentAndHash(document.id, indexConfiguration.hash)

        if (existingConfiguration?.status == IndexingStatus.READY &&
            existingConfiguration.documentHash == documentHash
        ) {
            onChunkProgress(ChunkIndexingProgress(1, 1))
            return DocumentIndexingResult(
                existingConfiguration.id,
                document.id,
                0,
                DocumentIndexingOutcome.SKIPPED,
            )
        }

        val chunker = checkNotNull(chunkersByStrategy[indexConfiguration.chunkingStrategy]) {
            "Chunking strategy is not supported: ${indexConfiguration.chunkingStrategy}"
        }
        val configuration = if (existingConfiguration == null) {
            configurationRepository.create(
                document.id,
                indexConfiguration.hash,
                documentHash,
                IndexingStatus.PROCESSING,
                Instant.now(),
            )
        } else {
            configurationRepository.restart(existingConfiguration.id, documentHash)
        }

        return try {
            rebuild(file, document.id, configuration.id, indexConfiguration, chunker, onChunkProgress)
        } catch (error: Exception) {
            val status = when (error) {
                is CancellationException, is InterruptedException -> IndexingStatus.CANCELLED
                else -> IndexingStatus.FAILED
            }
            try {
                configurationRepository.updateStatus(configuration.id, status)
            } finally {
                if (error is InterruptedException) Thread.currentThread().interrupt()
            }
            throw error
        }
    }

    private fun rebuild(
        file: Path,
        documentId: Long,
        configurationId: Long,
        indexConfiguration: IndexConfiguration,
        chunker: ChunkerService,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): DocumentIndexingResult {
        cancellationService.ensureActive()
        val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
        val chunks = chunker.chunk(content, indexConfiguration.parameters)

        onChunkProgress(ChunkIndexingProgress(0, chunks.size))

        var tokenUsage = TokenUsage()
        val vectors = chunks.mapIndexed { index, chunk ->
            cancellationService.ensureActive()
            val embedding = embedService.embedDocument(indexConfiguration.embeddingModel, chunk.content)
            tokenUsage += embedding.tokenUsage
            cancellationService.ensureActive()
            onChunkProgress(ChunkIndexingProgress(index + 1, chunks.size))
            embedding.value
        }

        cancellationService.ensureActive()
        searchIndexService.replace(configurationId, chunks, vectors)

        cancellationService.ensureActive()
        configurationRepository.markReady(configurationId)
        return DocumentIndexingResult(
            configurationId,
            documentId,
            chunks.size,
            DocumentIndexingOutcome.INDEXED,
            tokenUsage,
        )
    }
}
