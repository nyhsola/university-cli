package university.cli.service.evaluation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import university.cli.config.DirectoryConfig
import university.cli.model.EvaluationComparison
import university.cli.model.EvaluationReport
import university.cli.util.JsonUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReportWriter(
    directoryConfig: DirectoryConfig,
    private val json: Json = JsonUtil.json,
) {
    companion object {
        private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneId.systemDefault())
    }

    private val projectDirectory = checkNotNull(directoryConfig.dataDirectory.parent) {
        "Data directory must have a project parent"
    }

    fun write(
        startedAt: Instant,
        report: EvaluationReport,
        comparison: EvaluationComparison,
    ): WrittenReports {
        val timestamp = TIMESTAMP_FORMAT.format(startedAt)
        val directory = projectDirectory.resolve("reports/run-$timestamp")
        Files.createDirectories(directory)
        return WrittenReports(
            write(directory.resolve("report.json"), json.encodeToString(report)),
            write(directory.resolve("compare.json"), json.encodeToString(comparison)),
        )
    }

    fun relative(path: Path): String =
        projectDirectory.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/')

    private fun write(file: Path, content: String): Path {
        Files.writeString(file, content + System.lineSeparator(), StandardCharsets.UTF_8)
        return file
    }
}

data class WrittenReports(
    val reportFile: Path,
    val comparisonFile: Path,
)
