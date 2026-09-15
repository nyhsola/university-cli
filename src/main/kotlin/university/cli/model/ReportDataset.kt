package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class ReportDataset(
    val id: Long,
    val name: String,
    val description: String = "",
    val indexProfileIds: List<Long>,
    val queryProfileIds: List<Long>,
    val file: String,
    val questions: List<ReportQuestion>,
) {
    init {
        require(id > 0) { "Dataset id must be positive" }
        require(name.isNotBlank()) { "Dataset name must not be blank" }
        require(indexProfileIds.isNotEmpty()) { "At least one index profile id is required" }
        require(indexProfileIds.all { it > 0 }) { "Index profile ids must be positive" }
        require(indexProfileIds.distinct().size == indexProfileIds.size) { "Index profile ids must be unique" }
        require(queryProfileIds.isNotEmpty()) { "At least one query profile id is required" }
        require(queryProfileIds.all { it > 0 }) { "Query profile ids must be positive" }
        require(queryProfileIds.distinct().size == queryProfileIds.size) { "Query profile ids must be unique" }
        require(file.isNotBlank()) { "Dataset file must not be blank" }
        require(questions.isNotEmpty()) { "At least one question is required" }
        require(questions.map(ReportQuestion::id).distinct().size == questions.size) {
            "Question ids must be unique"
        }

        val profileIds = indexProfileIds.toSet()
        questions.forEach { question ->
            require(question.goldChunksByIndexProfile.keys == profileIds) {
                "Question ${question.id} must define gold chunks for index profiles ${indexProfileIds.sorted()}"
            }
        }
    }
}

@Serializable
data class ReportQuestion(
    val id: String,
    val question: String,
    val expectedAnswer: String,
    val tags: Set<String> = emptySet(),
    val goldChunksByIndexProfile: Map<Long, List<GoldChunk>>,
) {
    init {
        require(id.isNotBlank()) { "Question id must not be blank" }
        require(question.isNotBlank()) { "Question must not be blank" }
        require(expectedAnswer.isNotBlank()) { "Expected answer must not be blank" }
        goldChunksByIndexProfile.forEach { (profileId, chunks) ->
            require(profileId > 0) { "Gold chunk index profile id must be positive for question $id" }
            require(chunks.map(GoldChunk::chunkId).distinct().size == chunks.size) {
                "Gold chunk ids must be unique for question $id and index profile $profileId"
            }
        }
    }
}

@Serializable
data class GoldChunk(
    val chunkId: Long,
    val relevance: Int = 1,
    val description: String = "",
) {
    init {
        require(chunkId > 0) { "Gold chunk id must be positive" }
        require(relevance > 0) { "Gold chunk relevance must be positive" }
    }
}
