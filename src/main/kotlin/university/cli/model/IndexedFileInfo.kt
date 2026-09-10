package university.cli.model

data class IndexedFileInfo(
    val configurationId: Long,
    val documentId: Long,
    val fileName: String,
    val status: IndexingStatus,
)
