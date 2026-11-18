package university.cli.model

import java.nio.file.Path

data class ProjectIndexingResult(
    val indexedFiles: List<IndexedFile>,
    val skippedFiles: List<IndexedFile>,
    val failedFiles: List<FailedFile>,
)

data class IndexedFile(
    val path: Path,
    val result: DocumentIndexingResult,
)

data class FailedFile(
    val path: Path,
    val message: String,
)

data class ProjectIndexingProgress(
    val current: Int,
    val total: Int,
    val path: Path,
)
