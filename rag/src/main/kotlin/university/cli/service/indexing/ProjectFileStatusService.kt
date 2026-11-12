package university.cli.service.indexing

import university.cli.model.FileIndexStatus
import university.cli.model.IndexedFileInfo
import university.cli.model.IndexingStatus
import university.cli.model.ProjectFileStatus
import university.cli.repository.JdbcIndexingConfigurationRepository
import university.cli.service.configuration.IndexProfileService
import university.cli.util.FileUtil

class ProjectFileStatusService(
    private val projectFileService: ProjectFileService,
    private val configurationRepository: JdbcIndexingConfigurationRepository,
    private val configurationService: IndexProfileService,
) {
    fun getAll(indexProfileId: Long? = null): List<ProjectFileStatus> {
        val profilesByHash = configurationService.getAll().values.associateBy { it.hash }
        val selectedHash = indexProfileId?.let(configurationService::get)?.hash
        val indexesByFile = configurationRepository.findAllWithDocuments()
            .filter { selectedHash == null || it.configurationHash == selectedHash }
            .groupBy(IndexedFileInfo::fileName)

        return projectFileService.findAll().flatMap { file ->
            val fileName = projectFileService.relativeName(file)
            val fileHash = FileUtil.sha256(file)
            val indexes = indexesByFile[fileName].orEmpty()
            if (indexes.isEmpty()) {
                listOf(ProjectFileStatus(fileName, FileIndexStatus.NOT_INDEXED, null, indexProfileId))
            } else {
                indexes.map { index ->
                    ProjectFileStatus(
                        fileName,
                        status(index, fileHash),
                        index.configurationId,
                        profilesByHash[index.configurationHash]?.id,
                    )
                }
            }
        }.sortedWith(compareBy(ProjectFileStatus::fileName, ProjectFileStatus::indexId))
    }

    private fun status(index: IndexedFileInfo, currentHash: String): FileIndexStatus {
        if (index.documentHash != currentHash) return FileIndexStatus.CHANGED
        return when (index.status) {
            IndexingStatus.PROCESSING -> FileIndexStatus.PROCESSING
            IndexingStatus.READY -> FileIndexStatus.READY
            IndexingStatus.FAILED -> FileIndexStatus.FAILED
            IndexingStatus.CANCELLED -> FileIndexStatus.CANCELLED
        }
    }
}
