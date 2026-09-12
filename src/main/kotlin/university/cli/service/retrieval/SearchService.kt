package university.cli.service.retrieval

import university.cli.model.IndexConfiguration
import university.cli.model.IndexingConfiguration
import university.cli.model.IndexingStatus
import university.cli.model.QueryProfile
import university.cli.model.RelevantChunk
import university.cli.model.RetrievalMode
import university.cli.model.SearchScope
import university.cli.repository.JdbcChunkRepository
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.configuration.IndexProfileService
import university.cli.service.llm.EmbedService
import university.cli.service.operation.OperationCancellationService

class SearchService(
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val configurationService: IndexProfileService,
    private val documentRepository: JdbcDocumentRepository,
    private val chunkRepository: JdbcChunkRepository,
    private val embedService: EmbedService,
    private val vectorService: VectorService,
    private val lexicalSearchService: LexicalSearchService,
    private val rankFusionService: RankFusionService,
    private val cancellationService: OperationCancellationService,
) {
    private companion object {
        const val RETRIEVAL_MODE_PARAMETER = "retrievalMode"
        const val DENSE_CANDIDATE_LIMIT_PARAMETER = "denseCandidateLimit"
        const val LEXICAL_CANDIDATE_LIMIT_PARAMETER = "lexicalCandidateLimit"
        const val RRF_K_PARAMETER = "rrfK"
        const val TOP_K_PARAMETER = "topK"
    }

    private data class SearchOptions(
        val mode: RetrievalMode,
        val denseCandidateLimit: Int,
        val lexicalCandidateLimit: Int,
        val rrfK: Int,
        val topK: Int,
    )

    fun search(
        scope: SearchScope,
        queryProfile: QueryProfile,
        query: String,
    ): List<RelevantChunk> {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val options = options(queryProfile)
        val configurations = configurations(scope)
        if (configurations.isEmpty()) return emptyList()

        val resources = configurations.associateWith(::resolveResourceConfiguration)
        val dense = if (options.mode != RetrievalMode.LEXICAL) {
            val models = resources.values.map(IndexConfiguration::embeddingModel).distinct()
            require(models.size == 1) {
                "Dense search across multiple embedding models is not supported; use --index or --file"
            }
            cancellationService.ensureActive()
            val queryVector = embedService.embedQuery(models.single(), query)
            configurations.flatMap { configuration ->
                cancellationService.ensureActive()
                vectorService.getTopRelevant(configuration.id, queryVector, options.denseCandidateLimit)
            }.sortedBy { it.distance }
        } else {
            emptyList()
        }
        val lexical = if (options.mode != RetrievalMode.DENSE) {
            configurations.flatMap { configuration ->
                cancellationService.ensureActive()
                lexicalSearchService.getTopRelevant(configuration.id, query, options.lexicalCandidateLimit)
            }.sortedBy { it.bm25Score }
        } else {
            emptyList()
        }

        val matches = when (options.mode) {
            RetrievalMode.DENSE -> dense.take(options.topK).mapIndexed { index, match ->
                RankFusionService.FusedMatch(
                    match.indexConfigurationId,
                    match.id,
                    match.distance,
                    null,
                    index + 1,
                    null,
                    0.0,
                )
            }

            RetrievalMode.LEXICAL -> lexical.take(options.topK).mapIndexed { index, match ->
                RankFusionService.FusedMatch(
                    match.indexConfigurationId,
                    match.chunkId,
                    null,
                    match.bm25Score,
                    null,
                    index + 1,
                    0.0,
                )
            }

            RetrievalMode.HYBRID -> rankFusionService.fuse(dense, lexical, options.rrfK, options.topK)
        }
        return resolveMatches(configurations, matches)
    }

    private fun configurations(scope: SearchScope): List<IndexingConfiguration> {
        val ready = configurationRepository.findAllByStatus(IndexingStatus.READY)
        return when (scope) {
            SearchScope.All -> ready
            is SearchScope.File -> {
                val documentIds = configurationRepository.findAllWithDocuments()
                    .filter { it.fileName == scope.fileName }
                    .map { it.documentId }
                    .toSet()
                ready.filter { it.documentId in documentIds }
            }

            is SearchScope.Index -> listOf(
                checkNotNull(configurationRepository.findById(scope.id)) {
                    "Index not found: ${scope.id}"
                }.also { configuration ->
                    require(configuration.status == IndexingStatus.READY) {
                        "Index ${scope.id} is ${configuration.status}"
                    }
                },
            )
        }
    }

    private fun resolveMatches(
        configurations: List<IndexingConfiguration>,
        matches: List<RankFusionService.FusedMatch>,
    ): List<RelevantChunk> {
        val configurationsById = configurations.associateBy(IndexingConfiguration::id)
        val chunksByConfiguration = mutableMapOf<Long, Map<Long, university.cli.model.DocumentChunk>>()
        val documentsById = mutableMapOf<Long, university.cli.model.Document>()

        return matches.map { match ->
            cancellationService.ensureActive()
            val configuration = checkNotNull(configurationsById[match.indexConfigurationId]) {
                "Index not found while resolving search result: ${match.indexConfigurationId}"
            }
            val document = documentsById.getOrPut(configuration.documentId) {
                checkNotNull(documentRepository.findById(configuration.documentId)) {
                    "Document not found: ${configuration.documentId}"
                }
            }
            val chunks = chunksByConfiguration.getOrPut(configuration.id) {
                chunkRepository.findByConfiguration(configuration.id)
            }
            val chunk = checkNotNull(chunks[match.chunkId]) {
                "Chunk ${match.chunkId} not found for index ${configuration.id}"
            }
            RelevantChunk(
                configuration.id,
                chunk.id,
                configuration.documentId,
                document.fileName,
                chunk.content,
                match.distance,
                match.bm25Score,
                match.denseRank,
                match.lexicalRank,
                match.rrfScore.takeIf { it > 0.0 },
            )
        }
    }

    private fun options(configuration: QueryProfile): SearchOptions {
        val parameters = configuration.parameters
        val modeValue = parameters[RETRIEVAL_MODE_PARAMETER]
        require(!modeValue.isNullOrBlank()) {
            "Query profile ${configuration.id} must contain $RETRIEVAL_MODE_PARAMETER"
        }
        val mode = RetrievalMode.from(modeValue)
        val topK = positiveParameter(configuration, TOP_K_PARAMETER)
        val denseLimit = if (mode != RetrievalMode.LEXICAL) {
            positiveParameter(configuration, DENSE_CANDIDATE_LIMIT_PARAMETER)
        } else {
            topK
        }
        val lexicalLimit = if (mode != RetrievalMode.DENSE) {
            positiveParameter(configuration, LEXICAL_CANDIDATE_LIMIT_PARAMETER)
        } else {
            topK
        }
        val rrfK = if (mode == RetrievalMode.HYBRID) {
            positiveParameter(configuration, RRF_K_PARAMETER)
        } else {
            60
        }
        require(denseLimit >= topK) { "$DENSE_CANDIDATE_LIMIT_PARAMETER must be at least $TOP_K_PARAMETER" }
        require(lexicalLimit >= topK) { "$LEXICAL_CANDIDATE_LIMIT_PARAMETER must be at least $TOP_K_PARAMETER" }
        return SearchOptions(mode, denseLimit, lexicalLimit, rrfK, topK)
    }

    private fun positiveParameter(configuration: QueryProfile, name: String): Int {
        val value = configuration.parameters[name]?.toIntOrNull()
        require(value != null && value > 0) {
            "Query profile ${configuration.id} must contain a positive $name parameter"
        }
        return value
    }

    private fun resolveResourceConfiguration(configuration: IndexingConfiguration): IndexConfiguration =
        checkNotNull(configurationService.findByHash(configuration.hash)) {
            "Index profile for index ${configuration.id} was changed or removed; reindex the document"
        }
}
