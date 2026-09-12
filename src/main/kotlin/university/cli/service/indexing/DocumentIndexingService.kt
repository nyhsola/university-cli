package university.cli.service.indexing

import university.cli.model.ChunkIndexingProgress
import university.cli.model.DocumentIndexingOutcome
import university.cli.model.DocumentIndexingResult
import university.cli.model.IndexConfiguration
import university.cli.model.IndexingStatus
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

    fun index(
        source: Path,
        relativeFileName: String,
        indexConfiguration: IndexConfiguration,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): DocumentIndexingResult {
        val file = source.toAbsolutePath().normalize()

        require(Files.isRegularFile(file)) { "File does not exist or is not a regular file: $file" }
        require(indexConfiguration.hash.isNotBlank()) { "Indexing configuration hash is missing" }

        val documentHash = FileUtil.sha256(file)
        val document = documentRepository.save(relativeFileName, documentHash)
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
            cancellationService.ensureActive()
            val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            val chunks = chunker.chunk(content, indexConfiguration.parameters)

            onChunkProgress(ChunkIndexingProgress(0, chunks.size))

            val vectors = chunks.mapIndexed { index, chunk ->
                cancellationService.ensureActive()
                val vector = embedService.embedDocument(indexConfiguration.embeddingModel, chunk.content)
                cancellationService.ensureActive()
                onChunkProgress(ChunkIndexingProgress(index + 1, chunks.size))
                vector
            }

            cancellationService.ensureActive()
            searchIndexService.replace(configuration.id, chunks, vectors)

            cancellationService.ensureActive()
            configurationRepository.markReady(configuration.id)
            DocumentIndexingResult(
                configuration.id,
                document.id,
                chunks.size,
                DocumentIndexingOutcome.INDEXED,
            )
        } catch (error: CancellationException) {
            configurationRepository.updateStatus(configuration.id, IndexingStatus.CANCELLED)
            throw error
        } catch (error: InterruptedException) {
            configurationRepository.updateStatus(configuration.id, IndexingStatus.CANCELLED)
            Thread.currentThread().interrupt()
            throw error
        } catch (error: Exception) {
            configurationRepository.updateStatus(configuration.id, IndexingStatus.FAILED)
            throw error
        }
    }
}
