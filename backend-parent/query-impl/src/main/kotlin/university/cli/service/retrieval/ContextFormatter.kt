package university.cli.service.retrieval

import university.cli.model.RelevantChunk

class ContextFormatter {
    companion object {
        private const val SEPARATOR = "\n\n"
    }

    fun render(chunks: List<RelevantChunk>): String = chunks.joinToString(SEPARATOR, transform = ::renderChunk)

    fun renderChunk(chunk: RelevantChunk): String {
        val retrieval = buildList {
            if (chunk.denseRank != null) add("dense rank ${chunk.denseRank}")
            if (chunk.lexicalRank != null) add("lexical rank ${chunk.lexicalRank}")
        }.joinToString(", ")
        return "[Chunk ${chunk.chunkId}; file: ${chunk.fileName}; retrieved by: $retrieval]\n${chunk.content}"
    }
}
