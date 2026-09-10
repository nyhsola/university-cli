package university.cli.service.indexing

import university.cli.model.ChunkIndexingProgress
import university.cli.model.DocumentIndexingOutcome
import university.cli.model.DocumentIndexingResult
import university.cli.model.IndexParameters
import university.cli.model.IndexingStatus
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.llm.OllamaService
import university.cli.service.operation.OperationCancellationService
import university.cli.service.retrieval.VectorService
import university.cli.util.FileUtil
import university.cli.util.JsonUtil
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
    private val ollamaService: OllamaService,
    private val vectorService: VectorService,
    private val cancellationService: OperationCancellationService,
) {
    private val chunkersByStrategy = chunkers.associateBy(ChunkerService::strategy)

    fun index(
        fileName: String,
        indexParameters: IndexParameters,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): DocumentIndexingResult {
        val file = Path.of(fileName).toAbsolutePath().normalize()
        require(Files.isRegularFile(file)) { "File does not exist or is not a regular file: $file" }

        val document = documentRepository.save(file.fileName.toString(), FileUtil.sha256(file))
        val parametersJson = JsonUtil.objectOf(indexParameters.parameters.toSortedMap())
        val configurationIdentity = mapOf(
            "documentId" to document.id.toString(),
            "embeddingModel" to indexParameters.model,
            "strategy" to indexParameters.strategy.code.toString(),
            "parameters" to parametersJson,
        )
        val configurationHash = FileUtil.sha256(JsonUtil.objectOf(configurationIdentity.toSortedMap()))
        val existingConfiguration = configurationRepository.findByHash(configurationHash)

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

        val chunker = checkNotNull(chunkersByStrategy[indexParameters.strategy]) {
            "Chunking strategy is not registered: ${indexParameters.strategy}"
        }
        val configuration = if (existingConfiguration == null) {
            configurationRepository.create(
                document.id,
                indexParameters.model,
                indexParameters.strategy,
                IndexingStatus.PROCESSING,
                parametersJson,
                configurationHash,
                Instant.now(),
            )
        } else {
            vectorService.deleteVectors(existingConfiguration.id)
            configurationRepository.restart(existingConfiguration.id)
        }

        return try {
            cancellationService.ensureActive()
            val content = String(Files.readAllBytes(file), StandardCharsets.UTF_8)
            val chunks = chunker.chunk(content, indexParameters.parameters)
            val chunksFile = chunkFileWriterService.write(configuration.id, chunks)
            onChunkProgress(ChunkIndexingProgress(0, chunks.size))

            val vectors = chunks.mapIndexed { index, chunk ->
                cancellationService.ensureActive()
                val vector = ollamaService.embed(configuration.embeddingModel, chunk.content)
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
