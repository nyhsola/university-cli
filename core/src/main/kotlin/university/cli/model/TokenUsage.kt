package university.cli.model

data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
) {
    val totalTokens: Long
        get() = inputTokens + outputTokens

    operator fun plus(other: TokenUsage): TokenUsage =
        TokenUsage(inputTokens + other.inputTokens, outputTokens + other.outputTokens)
}

data class LlmResult<T>(
    val value: T,
    val tokenUsage: TokenUsage,
)
