package university.cli.service.indexing

import university.cli.model.IndexConfiguration
import university.cli.repository.JdbcDocumentRepository
import university.cli.repository.JdbcIndexingConfigurationRepository
import java.nio.file.Path

class IndexRemovalService(
    private val projectFileService: ProjectFileService,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val documentRepository: JdbcDocumentRepository,
) {
    fun remove(file: Path, profile: IndexConfiguration?): Int {
        val fileName = projectFileService.relativeName(file)
        val removed = configurationRepository.deleteByDocument(
            fileName,
            profile?.hash,
        )
        documentRepository.deleteIfUnindexed(fileName)
        return removed
    }

    fun removeAll(): Int = documentRepository.deleteAll()
}
