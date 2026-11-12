package university.cli.model

sealed interface SearchScope {
    data object All : SearchScope

    data class File(
        val fileName: String,
    ) : SearchScope

    data class Index(
        val id: Long,
    ) : SearchScope
}
