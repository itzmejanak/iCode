package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A tool's JSON Schema as iCode's parameter types (docs/assistant-and-mcp.md), which Koog carries to every
 * provider. A local `$ref` is inlined and a network one refused (the spec's rule); unions become one-of,
 * `allOf` objects one object; what Koog has no field for — formats, patterns, bounds, defaults — is
 * written into the description, so the model still reads it. Bounded in depth and size.
 */
internal object McpSchema {
    /** Why a tool's schema cannot be read: the tool is left out, with this said on its server's page. */
    class Unreadable(message: String) : Exception(message)

    /** [schema]'s fields. Throws [Unreadable]. */
    fun parameters(schema: ToolSchema): List<AssistantParameter> {
        val reader = Reader(schema.defs)
        val required = schema.required.orEmpty().toSet()
        return schema.properties.orEmpty().map { (name, body) -> reader.parameter(name, body, name in required, 0) }
    }

    private class Reader(private val defs: JsonObject?) {
        private var count = 0
        private val resolving = ArrayDeque<String>()

        fun parameter(name: String, element: JsonElement, required: Boolean, depth: Int): AssistantParameter {
            val body = element as? JsonObject ?: JsonObject(emptyMap())
            val (type, notes) = type(body, depth)
            val said = listOfNotNull(body.text("description") ?: body.text("title"), notes).joinToString(". ")
            return AssistantParameter(name, said, type, required)
        }

        /** [body]'s type, and what it says that the type cannot. */
        private fun type(body: JsonObject, depth: Int): Pair<AssistantParameterType, String?> {
            if (++count > MAX_SUBSCHEMAS || depth > MAX_DEPTH) throw Unreadable("its schema is too large to read")
            body.text("\$ref")?.let { ref -> return reference(ref, body, depth) }
            val notes = notes(body)
            body["const"]?.let { return AssistantParameterType.Choice(listOf(it.plain())) to notes }
            (body["enum"] as? JsonArray)?.let { values ->
                val strings = values.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }
                return if (strings.size == values.size && strings.isNotEmpty()) AssistantParameterType.Choice(strings) to notes
                else AssistantParameterType.Text to join("One of: ${values.joinToString { it.plain() }}", notes)
            }
            ((body["anyOf"] ?: body["oneOf"]) as? JsonArray)?.let { return union(it, depth) to notes }
            (body["allOf"] as? JsonArray)?.let { return merged(it, depth) to notes }
            if (body["if"] != null && (body["then"] != null || body["else"] != null)) {
                return union(JsonArray(listOfNotNull(body["then"], body["else"])), depth) to notes
            }
            return when (val type = body["type"]) {
                is JsonArray -> union(JsonArray(type.map { JsonObject(mapOf("type" to it)) }), depth) to notes
                is JsonPrimitive -> of(type.contentOrNull, body, depth) to notes
                else -> when {
                    body["properties"] != null -> of("object", body, depth) to notes
                    body["items"] != null -> of("array", body, depth) to notes
                    else -> AssistantParameterType.Text to join("Any JSON value", notes)
                }
            }
        }

        private fun of(type: String?, body: JsonObject, depth: Int): AssistantParameterType = when (type) {
            "string" -> AssistantParameterType.Text
            "integer" -> AssistantParameterType.Integer
            "number" -> AssistantParameterType.Number
            "boolean" -> AssistantParameterType.Boolean
            "null" -> AssistantParameterType.Null
            "array" -> AssistantParameterType.Items((body["items"] as? JsonObject)?.let { type(it, depth + 1).first } ?: AssistantParameterType.Text)
            "object" -> fields(body, depth)
            else -> throw Unreadable("its schema names a type iCode does not know: $type")
        }

        private fun fields(body: JsonObject, depth: Int): AssistantParameterType.Fields {
            val required = (body["required"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty().toSet()
            val fields = (body["properties"] as? JsonObject).orEmpty().map { (name, element) -> parameter(name, element, name in required, depth + 1) }
            val more = when (val extra = body["additionalProperties"]) {
                is JsonObject -> type(extra, depth + 1).first
                is JsonPrimitive -> AssistantParameterType.Text.takeIf { extra.booleanOrNull == true }
                else -> AssistantParameterType.Text.takeIf { fields.isEmpty() }
            }
            return AssistantParameterType.Fields(fields, more)
        }

        private fun union(options: JsonArray, depth: Int): AssistantParameterType {
            val parameters = options.mapNotNull { it as? JsonObject }.map { parameter("option", it, true, depth + 1) }
            return parameters.singleOrNull()?.type ?: AssistantParameterType.OneOf(parameters)
        }

        /** `allOf`: its objects as one; anything else in it, the first part's type. */
        private fun merged(parts: JsonArray, depth: Int): AssistantParameterType {
            val types = parts.mapNotNull { it as? JsonObject }.map { type(it, depth + 1).first }
            val objects = types.filterIsInstance<AssistantParameterType.Fields>()
            if (objects.size < types.size) return types.firstOrNull() ?: AssistantParameterType.Text
            return AssistantParameterType.Fields(objects.flatMap { it.fields }.distinctBy { it.name }, objects.firstNotNullOfOrNull { it.more })
        }

        private fun reference(ref: String, body: JsonObject, depth: Int): Pair<AssistantParameterType, String?> {
            if (!ref.startsWith("#")) throw Unreadable("its schema refers to $ref, and MCP clients never fetch a schema from the network")
            // A schema that refers to itself has no end: past its first time, it is any JSON of that shape.
            if (ref in resolving) return AssistantParameterType.Text to "JSON of the same shape as the field it is in"
            val name = ref.removePrefix("#/\$defs/")
            val target = (defs?.get(name) as? JsonObject)?.takeIf { ref.startsWith("#/\$defs/") }
                ?: throw Unreadable("its schema refers to $ref, which iCode cannot read")
            resolving.addLast(ref)
            try {
                val (type, notes) = type(target, depth + 1)
                return type to join(listOfNotNull(target.text("description"), notes).joinToString(". ").ifEmpty { null }, notes(body))
            } finally {
                resolving.removeLast()
            }
        }

        /** What JSON Schema says of a value that Koog has no field for, in plain words. */
        private fun notes(body: JsonObject): String? = listOfNotNull(
            body.text("format")?.let { "format $it" },
            body.text("pattern")?.let { "matching $it" },
            body.plain("minimum")?.let { "at least $it" },
            body.plain("exclusiveMinimum")?.let { "more than $it" },
            body.plain("maximum")?.let { "at most $it" },
            body.plain("exclusiveMaximum")?.let { "less than $it" },
            body.plain("minLength")?.let { "at least $it characters" },
            body.plain("maxLength")?.let { "at most $it characters" },
            body.plain("minItems")?.let { "at least $it items" },
            body.plain("maxItems")?.let { "at most $it items" },
            body["default"]?.let { "default ${it.plain()}" },
            (body["examples"] as? JsonArray)?.firstOrNull()?.let { "for example ${it.plain()}" },
        ).joinToString(", ").ifEmpty { null }

        private fun join(first: String?, second: String?): String? = listOfNotNull(first, second).joinToString(". ").ifEmpty { null }
    }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)
    private fun JsonObject.plain(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun JsonElement.plain(): String = if (this is JsonPrimitive && isString) content else if (this is JsonNull) "null" else toString()

    private const val MAX_DEPTH = 16
    private const val MAX_SUBSCHEMAS = 512
}
