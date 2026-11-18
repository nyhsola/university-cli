package university.cli.service.indexing

import university.cli.model.ChunkingStrategy
import university.cli.model.DocumentChunk

class RecursiveChunkerService : ChunkerService {
    companion object {
        private const val TARGET_SIZE = "targetSize"
        private const val MAX_SIZE = "maxSize"
        private const val OVERLAP = "overlap"

        private val SEPARATORS = listOf(
            Regex("\\r?\\n[\\t ]*\\r?\\n"),
            Regex("\\r?\\n"),
            Regex("(?<=[.!?])\\s+"),
            Regex("\\s+"),
        )
    }

    override val strategy = ChunkingStrategy.RECURSIVE

    override fun chunk(content: String, parameters: Map<String, String>): List<DocumentChunk> {
        val targetSize = parameters.requiredPositiveInt(TARGET_SIZE)
        val maxSize = parameters.requiredPositiveInt(MAX_SIZE)
        val overlap = parameters.requiredNonNegativeInt(OVERLAP)

        require(maxSize >= targetSize) { "$MAX_SIZE must be greater than or equal to $TARGET_SIZE" }
        require(overlap < targetSize) { "$OVERLAP must be less than $TARGET_SIZE" }
        require(targetSize.toLong() + overlap <= maxSize) {
            "$MAX_SIZE must be greater than or equal to $TARGET_SIZE + $OVERLAP"
        }

        if (content.isBlank()) {
            return emptyList()
        }

        val pieces = splitRecursively(content, 0, targetSize)
        val chunks = pack(pieces, targetSize)
        return addOverlap(chunks, overlap)
            .filter(String::isNotBlank)
            .mapIndexed { index, chunk -> DocumentChunk(index + 1L, chunk) }
    }

    private fun splitRecursively(content: String, separatorIndex: Int, targetSize: Int): List<String> {
        if (content.length <= targetSize) {
            return listOf(content)
        }
        if (separatorIndex == SEPARATORS.size) {
            return content.chunked(targetSize)
        }

        val pieces = splitKeepingSeparators(content, SEPARATORS[separatorIndex])
        if (pieces.size == 1) {
            return splitRecursively(content, separatorIndex + 1, targetSize)
        }

        return pieces.flatMap { piece ->
            if (piece.length <= targetSize) {
                listOf(piece)
            } else {
                splitRecursively(piece, separatorIndex + 1, targetSize)
            }
        }
    }

    private fun splitKeepingSeparators(content: String, separator: Regex): List<String> {
        val pieces = mutableListOf<String>()
        var start = 0

        separator.findAll(content).forEach { match ->
            val end = match.range.last + 1
            if (end > start) {
                pieces += content.substring(start, end)
            }
            start = end
        }
        if (start < content.length) {
            pieces += content.substring(start)
        }

        return pieces.ifEmpty { listOf(content) }
    }

    private fun pack(pieces: List<String>, targetSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        pieces.forEach { piece ->
            check(piece.length <= targetSize) { "Recursive split produced an oversized piece" }
            if (current.isNotEmpty() && current.length + piece.length > targetSize) {
                chunks += current.toString()
                current.clear()
            }
            current.append(piece)
        }
        if (current.isNotEmpty()) {
            chunks += current.toString()
        }

        return chunks
    }

    private fun addOverlap(chunks: List<String>, overlap: Int): List<String> {
        if (overlap == 0 || chunks.size < 2) {
            return chunks
        }

        return chunks.mapIndexed { index, chunk ->
            if (index == 0) {
                chunk
            } else {
                wordAlignedSuffix(chunks[index - 1], overlap) + chunk
            }
        }
    }

    private fun wordAlignedSuffix(content: String, maximumLength: Int): String {
        if (content.length <= maximumLength) {
            return content
        }

        val rawStart = content.length - maximumLength
        val boundary = (rawStart until content.length).firstOrNull { content[it].isWhitespace() }
            ?: return content.substring(rawStart)
        val start = (boundary until content.length).firstOrNull { !content[it].isWhitespace() }
            ?: content.length
        return content.substring(start)
    }

    private fun Map<String, String>.requiredPositiveInt(name: String): Int {
        val value = get(name)?.toIntOrNull()
        require(value != null && value > 0) { "$name must be positive integer" }
        return value
    }

    private fun Map<String, String>.requiredNonNegativeInt(name: String): Int {
        val value = get(name)?.toIntOrNull()
        require(value != null && value >= 0) { "$name must be non-negative integer" }
        return value
    }
}
