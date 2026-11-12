package university.cli.util

import university.cli.model.SearchScope

data class QueryCommandRequest(
    val query: String,
    val scope: SearchScope,
    val profileId: Long?,
    val explain: Boolean
)

object QueryCommandParser {
    fun parse(arguments: List<String>, allowExplain: Boolean): QueryCommandRequest {

        val valueOptions = buildMap {
            put("--file", "file")
            put("--index", "index")
            put("--profile", "profile")
            put("-p", "profile")
        }

        val flagOptions = buildMap {
            put("--all", "all")
            if (allowExplain) put("--explain", "explain")
        }

        val parsed = CommandOptionParser.parse(arguments, valueOptions, flagOptions)
        val query = parsed.positionals.joinToString(" ").trim()
        require(query.isNotBlank()) { "Query must not be blank" }

        val scopeOptions = listOfNotNull(
            parsed.options["file"]?.let { SearchScope.File(it) },
            parsed.options["index"]?.let { value ->
                val id = value.toLongOrNull()
                require(id != null && id > 0) { "Index id must be positive" }
                SearchScope.Index(id)
            },
            SearchScope.All.takeIf { "all" in parsed.flags },
        )
        require(scopeOptions.size <= 1) { "Specify only one of --all, --file, or --index" }

        val profileId = parsed.options["profile"]?.toLongOrNull()
        require(parsed.options["profile"] == null || profileId != null && profileId > 0) {
            "Query profile id must be positive"
        }
        return QueryCommandRequest(
            query,
            scopeOptions.singleOrNull() ?: SearchScope.All,
            profileId,
            "explain" in parsed.flags
        )
    }
}
