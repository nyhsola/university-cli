package university.cli.service.research

import university.cli.model.IndexingStatus
import university.cli.model.QueryProfile
import university.cli.model.SearchResult
import university.cli.model.SearchScope
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.configuration.IndexProfileService
import university.cli.service.operation.OperationCancellationService
import university.cli.service.retrieval.SearchService

class ResearchSearchService(
    private val searchService: SearchService,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val indexProfileService: IndexProfileService,
    private val cancellationService: OperationCancellationService,
) {
    fun prepareProfile(profile: QueryProfile, scope: SearchScope = SearchScope.All): QueryProfile {
        val ready = configurationRepository.findAllByStatus(IndexingStatus.READY)
        val configurations = when (scope) {
            SearchScope.All -> ready

            is SearchScope.Index -> ready.filter { it.id == scope.id }

            is SearchScope.File -> {
                val documentIds = configurationRepository.findAllWithDocuments()
                    .filter { it.fileName == scope.fileName }.map { it.documentId }.toSet()
                ready.filter { it.documentId in documentIds }
            }
        }
        require(configurations.isNotEmpty()) { "No ready indexes found. Run /index before /research." }
        val models = configurations.map { configuration ->
            cancellationService.ensureActive()
            val resource = indexProfileService.findByHash(configuration.hash)
            requireNotNull(resource) {
                "Index ${configuration.id} no longer matches an index profile. Reindex the affected file."
            }.embeddingModel
        }.distinct()
        val mode = if (models.size > 1) "lexical" else profile.parameters.getValue("retrievalMode")
        return profile.copy(
            parameters = profile.parameters + mapOf(
                "retrievalMode" to mode,
                "contextCharBudget" to "5500",
                "maxChunks" to "8",
            ),
            multiQuery = null,
        )
    }

    fun search(profile: QueryProfile, query: String, scope: SearchScope = SearchScope.All): SearchResult = searchService.search(scope, profile, query)
}
