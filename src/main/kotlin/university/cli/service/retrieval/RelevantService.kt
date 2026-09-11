package university.cli.service.retrieval

import university.cli.model.IndexConfiguration
import university.cli.model.IndexingConfiguration
import university.cli.model.IndexingStatus
import university.cli.model.RelevantChunk
import university.cli.model.Vector
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.indexing.ChunkFileReaderService
import university.cli.service.configuration.IndexConfigurationService
import university.cli.service.llm.EmbedService
import university.cli.service.operation.OperationCancellationService

class RelevantService(
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val configurationService: IndexConfigurationService,
    private val documentRepository: JdbcDocumentRepository,
    private val chunkFileReaderService: ChunkFileReaderService,
    private val embedService: EmbedService,
    private val vectorService: VectorService,
    private val cancellationService: OperationCancellationService,
) {
    fun getTopRelevant(configurationId: Long, question: String, limit: Int = 3): List<RelevantChunk> {
        require(question.isNotBlank()) { "Question must not be blank" }
        val configuration = checkNotNull(configurationRepository.findById(configurationId)) {
            "Index configuration not found: $configurationId"
        }
        require(configuration.status == IndexingStatus.READY) {
            "Index configuration $configurationId is ${configuration.status}"
        }
        val resourceConfiguration = resolveResourceConfiguration(configuration)

        cancellationService.ensureActive()
        val query = embedService.embedQuery(resourceConfiguration.embeddingModel, question)
        return findRelevant(configuration, query, limit)
    }

    fun getTopRelevant(question: String, limit: Int = 3): List<RelevantChunk> {
        require(question.isNotBlank()) { "Question must not be blank" }
        val configurations = configurationRepository.findAllByStatus(IndexingStatus.READY)
        val queriesByModel = mutableMapOf<String, Vector>()

        return configurations
            .mapNotNull { configuration ->
                configurationService.findByHash(configuration.hash)?.let { configuration to it }
            }
            .flatMap { (configuration, resourceConfiguration) ->
                cancellationService.ensureActive()
                val model = resourceConfiguration.embeddingModel
                val query = queriesByModel.getOrPut(model) {
                    embedService.embedQuery(model, question)
                }
                findRelevant(configuration, query, limit)
            }
            .sortedBy(RelevantChunk::distance)
            .take(limit)
    }

    private fun resolveResourceConfiguration(configuration: IndexingConfiguration): IndexConfiguration =
        checkNotNull(configurationService.findByHash(configuration.hash)) {
            "Configuration resource for index ${configuration.id} was changed or removed; reindex the document"
        }

    private fun findRelevant(
        configuration: IndexingConfiguration,
        query: Vector,
        limit: Int,
    ): List<RelevantChunk> {
        val chunkFile = checkNotNull(configuration.chunkFile) {
            "Chunk file is not set for configuration ${configuration.id}"
        }
        val document = checkNotNull(documentRepository.findById(configuration.documentId)) {
            "Document not found: ${configuration.documentId}"
        }
        val chunksById = chunkFileReaderService.read(chunkFile)

        return vectorService.getTopRelevant(configuration.id, query, limit).map { relevant ->
            val chunk = checkNotNull(chunksById[relevant.id]) {
                "Chunk ${relevant.id} not found for configuration ${configuration.id}"
            }
            RelevantChunk(
                configuration.id,
                chunk.id,
                configuration.documentId,
                document.fileName,
                chunk.content,
                relevant.distance,
            )
        }
    }
}
