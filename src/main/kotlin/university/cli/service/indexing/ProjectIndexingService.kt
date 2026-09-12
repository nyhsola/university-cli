package university.cli.service.indexing

import university.cli.model.ChunkIndexingProgress
import university.cli.model.DocumentIndexingOutcome
import university.cli.model.FailedFile
import university.cli.model.IndexConfiguration
import university.cli.model.IndexedFile
import university.cli.model.ProjectIndexingProgress
import university.cli.model.ProjectIndexingResult
import java.nio.file.Path
import java.util.concurrent.CancellationException

class ProjectIndexingService(
    private val projectFileService: ProjectFileService,
    private val documentIndexingService: DocumentIndexingService,
) {
    fun indexAll(
        indexConfiguration: IndexConfiguration,
        onFileProgress: (ProjectIndexingProgress) -> Unit,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): ProjectIndexingResult = index(projectFileService.findAll(), indexConfiguration, onFileProgress, onChunkProgress)

    fun indexFile(
        file: Path,
        indexConfiguration: IndexConfiguration,
        onFileProgress: (ProjectIndexingProgress) -> Unit,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): ProjectIndexingResult = index(listOf(file), indexConfiguration, onFileProgress, onChunkProgress)

    private fun index(
        files: List<Path>,
        indexConfiguration: IndexConfiguration,
        onFileProgress: (ProjectIndexingProgress) -> Unit,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): ProjectIndexingResult {
        val indexedFiles = mutableListOf<IndexedFile>()
        val skippedFiles = mutableListOf<IndexedFile>()
        val failedFiles = mutableListOf<FailedFile>()

        files.forEachIndexed { index, file ->
            val relativeFileName = projectFileService.relativeName(file)
            val relativePath = Path.of(relativeFileName)
            onFileProgress(ProjectIndexingProgress(index + 1, files.size, relativePath))
            try {
                val result = documentIndexingService.index(
                    file,
                    relativeFileName,
                    indexConfiguration,
                    onChunkProgress,
                )
                val indexedFile = IndexedFile(relativePath, result)
                when (result.outcome) {
                    DocumentIndexingOutcome.INDEXED -> indexedFiles += indexedFile
                    DocumentIndexingOutcome.SKIPPED -> skippedFiles += indexedFile
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            } catch (error: Exception) {
                failedFiles += FailedFile(relativePath, error.message ?: error.javaClass.simpleName)
            }
        }

        return ProjectIndexingResult(indexedFiles, skippedFiles, failedFiles)
    }
}
