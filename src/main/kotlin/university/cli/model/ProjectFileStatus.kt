package university.cli.model

data class ProjectFileStatus(
    val fileName: String,
    val status: FileIndexStatus,
    val indexId: Long?,
    val indexProfileId: Long?,
)

enum class FileIndexStatus {
    NOT_INDEXED,
    CHANGED,
    PROCESSING,
    READY,
    FAILED,
    CANCELLED,
}
