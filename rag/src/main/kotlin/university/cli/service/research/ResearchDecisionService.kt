package university.cli.service.research

import kotlinx.serialization.Serializable
import university.cli.config.OllamaConfig
import university.cli.model.LlmResult
import university.cli.model.ResearchDecision
import university.cli.model.ResearchSource
import university.cli.model.ResearchStep
import university.cli.service.llm.OllamaService
import university.cli.util.JsonUtil
import university.cli.util.loadResource

class ResearchDecisionService(
    private val ollamaService: OllamaService,
    private val ollamaConfig: OllamaConfig,
) {
    private companion object {
        val PROMPT = ResearchDecisionService::class.loadResource("prompts/research_decision.txt")
    }

    fun decide(question: String, steps: List<ResearchStep>, sources: List<ResearchSource>, remainingSearches: Int): LlmResult<ResearchDecision> {
        val input = DecisionInput(question, steps, sources, remainingSearches)
        val prompt = PROMPT.replace("{{input}}", JsonUtil.json.encodeToString(input))
        return ollamaService.question<ResearchDecision>(ollamaConfig.questionModel, prompt)
    }
}

@Serializable
private data class DecisionInput(
    val question: String,
    val previousSearches: List<ResearchStep>,
    val sources: List<ResearchSource>,
    val remainingSearches: Int,
)
