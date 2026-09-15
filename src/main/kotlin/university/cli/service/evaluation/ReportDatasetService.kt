package university.cli.service.evaluation

import kotlinx.serialization.json.Json
import university.cli.config.DirectoryConfig
import university.cli.model.ReportDataset
import university.cli.service.indexing.ProjectFileService
import university.cli.util.JsonUtil
import java.nio.file.Files
import java.nio.file.Path

class ReportDatasetService(
    directoryConfig: DirectoryConfig,
    private val projectFileService: ProjectFileService,
    private val json: Json = JsonUtil.json,
) {
    companion object {
        private val DATASET_FILE_PATTERN = Regex("dataset-[a-z0-9]+(?:-[a-z0-9]+)*\\.json")
    }

    private val projectDirectory = checkNotNull(directoryConfig.dataDirectory.parent) {
        "Data directory must have a project parent"
    }.toAbsolutePath().normalize()
    private val dataDirectory = directoryConfig.dataDirectory.toAbsolutePath().normalize()

    fun loadAll(directoryName: String): List<LoadedReportDataset> {
        require(directoryName.isNotBlank()) { "Dataset directory must not be blank" }
        val directory = projectDirectory.resolve(directoryName).toAbsolutePath().normalize()
        require(directory.startsWith(projectDirectory)) { "Dataset directory must be inside the project directory" }
        require(!directory.startsWith(dataDirectory)) { "Dataset directory must not be inside .data" }
        require(Files.isDirectory(directory)) { "Dataset directory does not exist: $directoryName" }

        val loaded = Files.list(directory).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .filter { DATASET_FILE_PATTERN.matches(it.fileName.toString()) }
                .sorted()
                .map(::load)
                .toList()
        }
        require(loaded.isNotEmpty()) {
            "Dataset directory contains no files named dataset-<lowercase-bookname>.json: $directoryName"
        }
        require(loaded.map { it.dataset.id }.distinct().size == loaded.size) {
            "Dataset ids must be unique within $directoryName"
        }

        val expectedIndexProfiles = loaded.first().dataset.indexProfileIds
        val expectedQueryProfiles = loaded.first().dataset.queryProfileIds
        loaded.drop(1).forEach { candidate ->
            require(candidate.dataset.indexProfileIds == expectedIndexProfiles) {
                "Dataset ${candidate.datasetFile.fileName} must use index profiles $expectedIndexProfiles"
            }
            require(candidate.dataset.queryProfileIds == expectedQueryProfiles) {
                "Dataset ${candidate.datasetFile.fileName} must use query profiles $expectedQueryProfiles"
            }
        }
        return loaded
    }

    private fun load(datasetFile: Path): LoadedReportDataset {
        val dataset = json.decodeFromString<ReportDataset>(Files.readString(datasetFile))
        val sourceFile = projectFileService.resolve(
            datasetFile.parent.resolve(dataset.file).normalize().toString(),
        )
        return LoadedReportDataset(
            dataset,
            datasetFile,
            sourceFile,
            projectFileService.relativeName(sourceFile),
        )
    }
}

data class LoadedReportDataset(
    val dataset: ReportDataset,
    val datasetFile: Path,
    val sourceFile: Path,
    val relativeSourceFile: String,
)
