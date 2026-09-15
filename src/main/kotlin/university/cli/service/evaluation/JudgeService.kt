package university.cli.service.evaluation

import kotlinx.serialization.Serializable
import university.cli.config.OllamaConfig
import university.cli.model.AnswerEvaluation
import university.cli.model.AnswerVerdict
import university.cli.model.LlmResult
import university.cli.service.llm.OllamaService
import university.cli.util.JsonUtil
import university.cli.util.loadResource

class JudgeService(
    private val ollamaService: OllamaService,
    private val ollamaConfig: OllamaConfig,
) {
    private companion object {
        val PROMPT = JudgeService::class.loadResource("prompts/answer_evaluation.txt")
    }

    fun evaluate(question: String, expectedAnswer: String, actualAnswer: String): LlmResult<AnswerEvaluation> {
        val input = JudgeInput(question, actualAnswer, expectedAnswer)
        val prompt = PROMPT.replace("{{input}}", JsonUtil.json.encodeToString(input))
        val result = ollamaService.question<JudgeResponse>(ollamaConfig.questionModel, prompt)
        return LlmResult(
            AnswerEvaluation.from(result.value.verdict, result.value.reason),
            result.tokenUsage,
        )
    }
}

@Serializable
private data class JudgeResponse(
    val verdict: AnswerVerdict,
    val reason: String,
)

@Serializable
private data class JudgeInput(
    val question: String,
    val candidateAnswer: String,
    val referenceAnswer: String,
)
