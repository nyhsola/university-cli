package university.cli.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnswerEvaluation(
    val verdict: AnswerVerdict,
    val score: Int,
    val reason: String,
) {
    init {
        require(score == verdict.score) {
            "Score $score does not match verdict ${verdict.serialValue}"
        }
    }
}

@Serializable
enum class AnswerVerdict(
    val score: Int,
    val serialValue: String,
) {
    @SerialName("complete")
    COMPLETE(10, "complete"),

    @SerialName("not_complete")
    NOT_COMPLETE(5, "not_complete"),

    @SerialName("incorrect")
    INCORRECT(0, "incorrect"),
}
