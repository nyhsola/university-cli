package university.cli.model

data class ContextSelection(
    val chunks: List<RelevantChunk>,
    val stats: ContextSelectionStats,
)

data class ContextSelectionStats(
    val contextCharBudget: Int,
    val maxChunks: Int,
    val contextUsedChars: Int,
    val effectiveTopK: Int,
    val availableCandidates: Int,
)
