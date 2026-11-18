package university.cli.service.research

import kotlinx.serialization.Serializable
import university.cli.config.OllamaConfig
import university.cli.model.LlmResult
import university.cli.model.ResearchAnswer
import university.cli.model.ResearchSource
import university.cli.model.ResearchStopReason
import university.cli.service.llm.OllamaService
import university.cli.util.JsonUtil
import university.cli.util.loadResource

class ResearchAnswerService(
    private val ollamaService: OllamaService,
    private val ollamaConfig: OllamaConfig,
) {
    private companion object {
        val PROMPT = ResearchAnswerService::class.loadResource("prompts/research_answer.txt")
    }

    fun generate(question: String, sources: List<ResearchSource>, stopReason: ResearchStopReason, correction: String = ""): LlmResult<ResearchAnswer> {
        val input = AnswerInput(question, sources, stopReason, correction)
        val prompt = PROMPT.replace("{{input}}", JsonUtil.json.encodeToString(input))
        return ollamaService.question<ResearchAnswer>(ollamaConfig.questionModel, prompt)
    }

    fun validationError(answer: ResearchAnswer, sources: List<ResearchSource>): String? {
        val sourceIds = sources.map(ResearchSource::id).toSet()
        return when {
            answer.claims.isEmpty() && answer.unresolved.isEmpty() -> "Provide supported claims or unresolved questions."

            answer.claims.any { it.text.isBlank() } -> "Every claim must have nonblank text."

            answer.claims.any { it.sourceIds.isEmpty() } -> "Every claim must cite at least one supplied source."

            answer.claims.any { claim -> claim.sourceIds.any { it !in sourceIds } } ->
                "Use only supplied source IDs: ${sourceIds.joinToString()}."

            answer.unresolved.any(String::isBlank) -> "Unresolved questions must not be blank."

            else -> null
        }
    }
}

@Serializable
private data class AnswerInput(
    val question: String,
    val sources: List<ResearchSource>,
    val stopReason: ResearchStopReason,
    val correction: String,
)
