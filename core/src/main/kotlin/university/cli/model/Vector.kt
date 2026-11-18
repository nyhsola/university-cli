package university.cli.model

import kotlinx.serialization.Serializable

@Serializable
data class Vector(
    val values: List<Float>,
)
