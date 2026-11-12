package university.cli.service.retrieval

import university.cli.model.ContextSelection
import university.cli.model.ContextSelectionStats
import university.cli.model.RelevantChunk

class ContextSelectionService(
    private val contextFormatter: ContextFormatter,
) {
    fun select(
        candidates: List<RelevantChunk>,
        contextCharBudget: Int,
        maxChunks: Int,
    ): ContextSelection {
        require(contextCharBudget > 0) { "Context character budget must be positive" }
        require(maxChunks > 0) { "Maximum context chunks must be positive" }

        var usedChars = 0
        val selected = mutableListOf<RelevantChunk>()
        for (candidate in candidates.take(maxChunks)) {
            val candidateContext = contextFormatter.render(selected + candidate)
            if (candidateContext.length > contextCharBudget) {
                break
            }
            selected += candidate
            usedChars = candidateContext.length
        }

        require(candidates.isEmpty() || selected.isNotEmpty()) {
            "Context character budget $contextCharBudget is too small for the highest-ranked chunk"
        }
        return ContextSelection(
            selected,
            ContextSelectionStats(
                contextCharBudget,
                maxChunks,
                usedChars,
                selected.size,
                candidates.size,
            ),
        )
    }
}
