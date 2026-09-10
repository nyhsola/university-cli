package university.cli.model

data class ChunkIndexingProgress(
    val current: Int,
    val total: Int,
) {
    val percentage: Int
        get() = if (total == 0) 100 else current * 100 / total

    init {
        require(current >= 0) { "Current chunk must not be negative" }
        require(total >= 0) { "Total chunks must not be negative" }
        require(current <= total) { "Current chunk must not exceed total chunks" }
    }
}
