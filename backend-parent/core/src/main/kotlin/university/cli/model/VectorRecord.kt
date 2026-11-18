package university.cli.model

data class VectorRecord(
    val id: Long,
    val indexConfigurationId: Long,
    val vector: Vector,
)

data class RelevantVector(
    val id: Long,
    val indexConfigurationId: Long,
    val vector: Vector,
    val distance: Double,
)
