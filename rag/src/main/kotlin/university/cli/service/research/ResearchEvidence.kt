package university.cli.service.research

import university.cli.model.RelevantChunk
import university.cli.model.ContextSelectionStats
import university.cli.model.ResearchSource
import university.cli.util.JsonUtil

internal class ResearchEvidence {
    private companion object {
        const val CONTEXT_CHAR_BUDGET = 12000
        const val MAX_CONTEXT_SOURCES = 12
    }

    private val sourcesByChunk = linkedMapOf<Pair<Long, Long>, ResearchSource>()
    private val groups = mutableListOf<List<ResearchSource>>()

    val sources: List<ResearchSource>
        get() = sourcesByChunk.values.toList()

    fun add(chunks: List<RelevantChunk>): List<ResearchSource> {
        val added = chunks.mapNotNull { chunk ->
            val key = chunk.configurationId to chunk.chunkId
            if (key in sourcesByChunk) return@mapNotNull null
            ResearchSource("S${sourcesByChunk.size + 1}", key.first, key.second, chunk.fileName, chunk.content, chunk.documentId)
                .also { sourcesByChunk[key] = it }
        }
        groups += added
        return added
    }

    fun context(): List<ResearchSource> {
        val selected = mutableListOf<ResearchSource>()
        repeat(groups.maxOfOrNull(List<ResearchSource>::size) ?: 0) { rank ->
            groups.forEach { group ->
                val source = group.getOrNull(rank) ?: return@forEach
                if (selected.size < MAX_CONTEXT_SOURCES &&
                    JsonUtil.json.encodeToString(selected + source).length <= CONTEXT_CHAR_BUDGET
                ) {
                    selected += source
                }
            }
        }
        return selected
    }

    fun contextStats(): ContextSelectionStats {
        val selected = context()
        return ContextSelectionStats(
            CONTEXT_CHAR_BUDGET, MAX_CONTEXT_SOURCES,
            JsonUtil.json.encodeToString(selected).length, selected.size, sourcesByChunk.size,
        )
    }
}
