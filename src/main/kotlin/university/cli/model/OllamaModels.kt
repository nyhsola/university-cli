package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class Vector(
    val values: List<Float>,
)

@Serializable
data class Answer(
    val text: String,
)
