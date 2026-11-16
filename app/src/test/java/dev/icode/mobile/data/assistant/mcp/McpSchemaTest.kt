package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** docs/assistant-and-mcp.md: a tool's JSON Schema as iCode's parameter types, each case, and what is refused. */
class McpSchemaTest {
    private fun schema(json: String): ToolSchema {
        val body = Json.parseToJsonElement(json).jsonObject
        return ToolSchema(properties = body["properties"] as? JsonObject, required = (body["required"] as? JsonArray)?.map { it.jsonPrimitive.content }, defs = body["\$defs"] as? JsonObject)
    }

    private fun read(json: String): List<AssistantParameter> = McpSchema.parameters(schema(json))

    @Test
    fun `primitive fields keep their type, description and whether they are required`() {
        val read = read(
            """{"properties": {
                "title": {"type": "string", "description": "The issue's title"},
                "count": {"type": "integer"}, "ratio": {"type": "number"}, "draft": {"type": "boolean"}
            }, "required": ["title"]}""",
        )
        assertEquals(AssistantParameter("title", "The issue's title", AssistantParameterType.Text, required = true), read[0])
        assertEquals(listOf(AssistantParameterType.Integer, AssistantParameterType.Number, AssistantParameterType.Boolean), read.drop(1).map { it.type })
        assertTrue(read.drop(1).none { it.required })
    }

    @Test
    fun `an enum is a choice, and a const is a choice of one`() {
        val read = read("""{"properties": {"state": {"enum": ["open", "closed"]}, "kind": {"const": "bug"}, "mixed": {"enum": [1, "a"]}}}""")
        assertEquals(AssistantParameterType.Choice(listOf("open", "closed")), read[0].type)
        assertEquals(AssistantParameterType.Choice(listOf("bug")), read[1].type)
        // A choice Koog cannot carry is text, its values said.
        assertEquals(AssistantParameterType.Text, read[2].type)
        assertEquals("One of: 1, a", read[2].description)
    }

    @Test
    fun `arrays and nested objects keep their shape`() {
        val read = read(
            """{"properties": {
                "labels": {"type": "array", "items": {"type": "string"}},
                "author": {"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]},
                "meta": {"type": "object", "additionalProperties": {"type": "integer"}}
            }}""",
        )
        assertEquals(AssistantParameterType.Items(AssistantParameterType.Text), read[0].type)
        assertEquals(AssistantParameterType.Fields(listOf(AssistantParameter("name", "", AssistantParameterType.Text, true))), read[1].type)
        assertEquals(AssistantParameterType.Fields(emptyList(), more = AssistantParameterType.Integer), read[2].type)
    }

    @Test
    fun `unions, type lists and allOf are read`() {
        val read = read(
            """{"properties": {
                "id": {"anyOf": [{"type": "string"}, {"type": "integer"}]},
                "maybe": {"type": ["string", "null"]},
                "both": {"allOf": [{"properties": {"a": {"type": "string"}}}, {"properties": {"b": {"type": "integer"}}}]}
            }}""",
        )
        assertEquals(listOf(AssistantParameterType.Text, AssistantParameterType.Integer), (read[0].type as AssistantParameterType.OneOf).options.map { it.type })
        assertEquals(listOf(AssistantParameterType.Text, AssistantParameterType.Null), (read[1].type as AssistantParameterType.OneOf).options.map { it.type })
        assertEquals(listOf("a", "b"), (read[2].type as AssistantParameterType.Fields).fields.map { it.name })
    }

    @Test
    fun `what Koog has no field for is written into the description`() {
        val read = read("""{"properties": {"email": {"type": "string", "description": "Who", "format": "email", "maxLength": 80, "default": "a@b.c"}}}""")
        assertEquals("Who. format email, at most 80 characters, default a@b.c", read.single().description)
    }

    @Test
    fun `a local reference is inlined, and one to itself ends`() {
        val read = read(
            """{"properties": {"user": {"${'$'}ref": "#/${'$'}defs/User"}},
                "${'$'}defs": {"User": {"type": "object", "description": "A user", "properties": {"name": {"type": "string"}, "manager": {"${'$'}ref": "#/${'$'}defs/User"}}}}}""",
        )
        val user = read.single()
        assertEquals("A user", user.description)
        val fields = (user.type as AssistantParameterType.Fields).fields
        assertEquals(listOf("name", "manager"), fields.map { it.name })
        assertEquals(AssistantParameterType.Text, fields[1].type)
        assertTrue(fields[1].description.contains("same shape"))
    }

    @Test
    fun `a reference to the network, or to nothing, leaves the tool out`() {
        listOf(
            """{"properties": {"a": {"${'$'}ref": "https://example.com/schema.json"}}}""",
            """{"properties": {"a": {"${'$'}ref": "#/${'$'}defs/Missing"}}}""",
            """{"properties": {"a": {"type": "date"}}}""",
        ).forEach { json ->
            try {
                read(json)
                fail("$json cannot be read")
            } catch (refused: McpSchema.Unreadable) {
                assertTrue(refused.message!!.startsWith("its schema"))
            }
        }
    }

    @Test
    fun `a schema too deep to read is refused`() {
        val deep = (1..20).fold("""{"type": "string"}""") { inner, _ -> """{"type": "array", "items": $inner}""" }
        try {
            read("""{"properties": {"a": $deep}}""")
            fail("Too deep")
        } catch (refused: McpSchema.Unreadable) {
            assertEquals("its schema is too large to read", refused.message)
        }
    }
}
