package university.cli.model

data class AskResult(
    val answer: StructuredAnswer,
    val relevantChunks: List<RelevantChunk>,
    val tokenUsage: TokenUsage,
    val contextStats: ContextSelectionStats,
)
