package university.cli.util

import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object JsonSchemaUtil {
    fun from(descriptor: SerialDescriptor): JsonObject {
        val schema = schema(descriptor)
        check(schema is JsonObject) { "Root JSON schema must describe an object: ${descriptor.serialName}" }
        return schema
    }

    private fun schema(descriptor: SerialDescriptor): JsonElement {
        val schema = when (descriptor.kind) {
            StructureKind.CLASS, StructureKind.OBJECT -> objectSchema(descriptor)
            StructureKind.LIST -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("array"),
                    "items" to schema(descriptor.getElementDescriptor(0)),
                ),
            )
            StructureKind.MAP -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("object"),
                    "additionalProperties" to schema(descriptor.getElementDescriptor(1)),
                ),
            )
            SerialKind.ENUM -> JsonObject(
                mapOf(
                    "type" to JsonPrimitive("string"),
                    "enum" to JsonArray(
                        List(descriptor.elementsCount) { JsonPrimitive(descriptor.getElementName(it)) },
                    ),
                ),
            )
            PrimitiveKind.BOOLEAN -> primitiveSchema("boolean")
            PrimitiveKind.BYTE,
            PrimitiveKind.SHORT,
            PrimitiveKind.INT,
            PrimitiveKind.LONG,
            -> primitiveSchema("integer")
            PrimitiveKind.FLOAT,
            PrimitiveKind.DOUBLE,
            -> primitiveSchema("number")
            PrimitiveKind.CHAR,
            PrimitiveKind.STRING,
            -> primitiveSchema("string")
            else -> error("Unsupported serial kind ${descriptor.kind} for ${descriptor.serialName}")
        }
        if (!descriptor.isNullable) return schema
        return JsonObject(
            mapOf(
                "anyOf" to JsonArray(
                    listOf(
                        schema,
                        JsonObject(mapOf("type" to JsonPrimitive("null"))),
                    ),
                ),
            ),
        )
    }

    private fun objectSchema(descriptor: SerialDescriptor): JsonObject {
        val properties = LinkedHashMap<String, JsonElement>()
        val required = mutableListOf<JsonElement>()
        repeat(descriptor.elementsCount) { index ->
            val name = descriptor.getElementName(index)
            val elementDescriptor = descriptor.getElementDescriptor(index)
            properties[name] = schema(elementDescriptor)
            if (!descriptor.isElementOptional(index)) {
                required += JsonPrimitive(name)
            }
        }
        return JsonObject(
            buildMap {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(properties))
                put("required", JsonArray(required))
                put("additionalProperties", JsonPrimitive(false))
            },
        )
    }

    private fun primitiveSchema(type: String): JsonObject = JsonObject(
        mapOf("type" to JsonPrimitive(type)),
    )
}
