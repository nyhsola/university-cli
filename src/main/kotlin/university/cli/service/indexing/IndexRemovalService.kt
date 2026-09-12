package university.cli.service.indexing

import university.cli.model.IndexConfiguration
import university.cli.repository.JdbcIndexingConfigurationRepository
import java.nio.file.Path

class IndexRemovalService(
    private val projectFileService: ProjectFileService,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
) {
    fun remove(file: Path, profile: IndexConfiguration?): Int =
        configurationRepository.deleteByDocument(
            projectFileService.relativeName(file),
            profile?.hash,
        )
}
