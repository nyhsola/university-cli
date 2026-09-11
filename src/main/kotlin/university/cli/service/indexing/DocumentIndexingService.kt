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
import university.cli.service.retrieval.VectorService
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
    private val chunkFileWriterService: ChunkFileWriterService,
    private val embedService: EmbedService,
    private val vectorService: VectorService,
    private val cancellationService: OperationCancellationService,
) {
    private val chunkersByStrategy = chunkers.associateBy(ChunkerService::strategy)

    fun index(
        fileName: String,
        indexConfiguration: IndexConfiguration,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): DocumentIndexingResult {
        val file = Path.of(fileName).toAbsolutePath().normalize()
        require(Files.isRegularFile(file)) { "File does not exist or is not a regular file: $file" }
        require(indexConfiguration.hash.isNotBlank()) { "Indexing configuration hash is missing" }

        val document = documentRepository.save(file.fileName.toString(), FileUtil.sha256(file))
        val existingConfiguration = configurationRepository.findByDocumentAndHash(document.id, indexConfiguration.hash)

        if (existingConfiguration?.status == IndexingStatus.READY) {
            onChunkProgress(ChunkIndexingProgress(1, 1))
            val chunksFile = existingConfiguration.chunkFile?.let(chunkFileWriterService::resolve)
            return DocumentIndexingResult(
                existingConfiguration.id,
                document.id,
                0,
                chunksFile,
                DocumentIndexingOutcome.SKIPPED,
            )
        }

        val chunker = checkNotNull(chunkersByStrategy[indexConfiguration.strategy]) {
            "Chunking strategy is not supported: ${indexConfiguration.strategy}"
        }
        val configuration = if (existingConfiguration == null) {
            configurationRepository.create(
                document.id,
                indexConfiguration.hash,
                IndexingStatus.PROCESSING,
                Instant.now(),
            )
        } else {
            vectorService.deleteVectors(existingConfiguration.id)
            configurationRepository.restart(existingConfiguration.id)
        }

        return try {
            cancellationService.ensureActive()
            val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            val chunks = chunker.chunk(content, indexConfiguration.parameters)
            val chunksFile = chunkFileWriterService.write(configuration.id, chunks)
            onChunkProgress(ChunkIndexingProgress(0, chunks.size))

            val vectors = chunks.mapIndexed { index, chunk ->
                cancellationService.ensureActive()
                val vector = embedService.embedDocument(indexConfiguration.embeddingModel, chunk.content)
                cancellationService.ensureActive()
                onChunkProgress(ChunkIndexingProgress(index + 1, chunks.size))
                vector
            }

            cancellationService.ensureActive()
            vectorService.insertVectors(configuration.id, vectors)
            cancellationService.ensureActive()
            configurationRepository.markReady(configuration.id, chunksFile.fileName.toString())
            DocumentIndexingResult(
                configuration.id,
                document.id,
                chunks.size,
                chunksFile,
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
