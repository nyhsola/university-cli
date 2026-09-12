package university.cli.model

data class IndexedFileInfo(
    val configurationId: Long,
    val documentId: Long,
    val fileName: String,
    val documentHash: String,
    val configurationHash: String,
    val status: IndexingStatus,
)
