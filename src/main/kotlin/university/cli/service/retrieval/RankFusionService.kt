package university.cli.service.retrieval

import university.cli.model.LexicalMatch
import university.cli.model.RelevantVector

class RankFusionService {
    data class FusedMatch(
        val indexConfigurationId: Long,
        val chunkId: Long,
        val distance: Double?,
        val bm25Score: Double?,
        val denseRank: Int?,
        val lexicalRank: Int?,
        val rrfScore: Double,
    )

    fun fuse(
        dense: List<RelevantVector>,
        lexical: List<LexicalMatch>,
        rrfK: Int,
        limit: Int,
    ): List<FusedMatch> {
        require(rrfK > 0) { "RRF k must be positive" }
        require(limit > 0) { "Limit must be positive" }

        val denseById = dense.withIndex().associate { (index, match) ->
            (match.indexConfigurationId to match.id) to (index + 1 to match)
        }
        val lexicalById = lexical.withIndex().associate { (index, match) ->
            (match.indexConfigurationId to match.chunkId) to (index + 1 to match)
        }

        return (denseById.keys + lexicalById.keys).map { key ->
            val denseEntry = denseById[key]
            val lexicalEntry = lexicalById[key]
            val score = listOfNotNull(denseEntry?.first, lexicalEntry?.first).sumOf { rank -> 1.0 / (rrfK + rank) }

            FusedMatch(
                key.first,
                key.second,
                denseEntry?.second?.distance,
                lexicalEntry?.second?.bm25Score,
                denseEntry?.first,
                lexicalEntry?.first,
                score,
            )
        }.sortedWith(
            compareByDescending<FusedMatch> { it.rrfScore }
                .thenBy { it.indexConfigurationId }
                .thenBy { it.chunkId },
        ).take(limit)
    }
}
