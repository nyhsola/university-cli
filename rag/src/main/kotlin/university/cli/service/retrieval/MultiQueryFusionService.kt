package university.cli.service.retrieval

import university.cli.model.RelevantVector

class MultiQueryFusionService {
    fun fuse(
        rankings: List<List<RelevantVector>>,
        rrfK: Int,
        limit: Int,
    ): List<RelevantVector> {
        require(rankings.isNotEmpty()) { "Multi-query rankings must not be empty" }
        require(rrfK > 0) { "Multi-query RRF k must be positive" }
        require(limit > 0) { "Multi-query result limit must be positive" }

        val matches = mutableMapOf<ChunkKey, RelevantVector>()
        val scores = mutableMapOf<ChunkKey, Double>()
        rankings.forEach { ranking ->
            ranking.forEachIndexed { index, match ->
                val key = ChunkKey(match.indexConfigurationId, match.id)
                val current = matches[key]
                if (current == null || match.distance < current.distance) {
                    matches[key] = match
                }
                scores[key] = scores.getOrDefault(key, 0.0) + 1.0 / (rrfK + index + 1)
            }
        }

        return matches.entries
            .sortedWith(
                compareByDescending<Map.Entry<ChunkKey, RelevantVector>> { scores.getValue(it.key) }
                    .thenBy { it.key.indexConfigurationId }
                    .thenBy { it.key.chunkId },
            )
            .take(limit)
            .map(Map.Entry<ChunkKey, RelevantVector>::value)
    }

    private data class ChunkKey(
        val indexConfigurationId: Long,
        val chunkId: Long,
    )
}
