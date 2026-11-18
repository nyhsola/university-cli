package university.cli.model

data class SearchResult(
    val chunks: List<RelevantChunk>,
    val tokenUsage: TokenUsage,
    val contextStats: ContextSelectionStats,
)
