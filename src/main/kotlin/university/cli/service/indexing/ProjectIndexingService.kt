package university.cli.service.indexing

import university.cli.config.DirectoryConfig
import university.cli.model.ChunkIndexingProgress
import university.cli.model.DocumentIndexingOutcome
import university.cli.model.FailedFile
import university.cli.model.IndexConfiguration
import university.cli.model.IndexedFile
import university.cli.model.ProjectIndexingProgress
import university.cli.model.ProjectIndexingResult
import university.cli.service.operation.OperationCancellationService
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.relativeTo

class ProjectIndexingService(
    private val directoryConfig: DirectoryConfig,
    private val documentIndexingService: DocumentIndexingService,
) {
    private val projectDirectory = checkNotNull(directoryConfig.dataDirectory.parent) { "Data directory must have a project parent" }

    fun index(
        indexConfiguration: IndexConfiguration,
        onFileProgress: (ProjectIndexingProgress) -> Unit,
        onChunkProgress: (ChunkIndexingProgress) -> Unit,
    ): ProjectIndexingResult {
        val files = findProjectFiles()
        val indexedFiles = mutableListOf<IndexedFile>()
        val skippedFiles = mutableListOf<IndexedFile>()
        val failedFiles = mutableListOf<FailedFile>()

        files.forEachIndexed { index, file ->
            val relativePath = file.relativeTo(projectDirectory)
            onFileProgress(ProjectIndexingProgress(index + 1, files.size, relativePath))
            try {
                val result = documentIndexingService.index(file.toString(), indexConfiguration, onChunkProgress)
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

    private fun findProjectFiles(): List<Path> = Files.walk(projectDirectory).use { paths ->
        paths
            .filter(Files::isRegularFile)
            .filter { path -> !path.startsWith(directoryConfig.dataDirectory) }
            .filter { path -> path.fileName.toString().endsWith(".txt", ignoreCase = true) }
            .sorted(compareBy { it.toString() })
            .toList()
    }
}
