package dev.icode.mobile.data.assistant.koog

import ai.koog.agents.core.tools.ToolParameterType
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/mcp-plan.md §6.2: each of iCode's parameter types as exactly one of Koog's, nested as it is. */
class KoogToolTest {
    @Test
    fun `primitives map one to one`() {
        assertEquals(ToolParameterType.String, KoogTool.type(AssistantParameterType.Text))
        assertEquals(ToolParameterType.Integer, KoogTool.type(AssistantParameterType.Integer))
        assertEquals(ToolParameterType.Boolean, KoogTool.type(AssistantParameterType.Boolean))
        assertEquals(ToolParameterType.Float, KoogTool.type(AssistantParameterType.Number))
        assertEquals(ToolParameterType.Null, KoogTool.type(AssistantParameterType.Null))
    }

    @Test
    fun `choices, lists, objects and unions keep their shape`() {
        assertArrayEquals(arrayOf("open", "closed"), (KoogTool.type(AssistantParameterType.Choice(listOf("open", "closed"))) as ToolParameterType.Enum).entries)
        assertEquals(ToolParameterType.List(ToolParameterType.Integer), KoogTool.type(AssistantParameterType.Items(AssistantParameterType.Integer)))
        val fields = KoogTool.type(
            AssistantParameterType.Fields(listOf(AssistantParameter("name", "Who", AssistantParameterType.Text, true), AssistantParameter("age", "", AssistantParameterType.Integer, false))),
        ) as ToolParameterType.Object
        assertEquals(listOf("name", "age"), fields.properties.map { it.name })
        assertEquals(listOf("name"), fields.requiredProperties)
        assertNull(fields.additionalProperties)
        val map = KoogTool.type(AssistantParameterType.Fields(emptyList(), more = AssistantParameterType.Number)) as ToolParameterType.Object
        assertEquals(true, map.additionalProperties)
        assertEquals(ToolParameterType.Float, map.additionalPropertiesType)
        val union = KoogTool.type(AssistantParameterType.OneOf(listOf(AssistantParameter("option", "", AssistantParameterType.Text), AssistantParameter("option", "", AssistantParameterType.Integer)))) as ToolParameterType.AnyOf
        assertEquals(listOf(ToolParameterType.String, ToolParameterType.Integer), union.types.map { it.type })
    }
}
