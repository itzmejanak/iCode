package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogStreamTest {
    @Test fun `deltas make the reply, the completed frames repeating them add nothing`() {
        val stream = KoogStream()
        assertTrue(stream.add(StreamFrame.ReasoningDelta(text = "Look at ")))
        assertTrue(stream.add(StreamFrame.ReasoningDelta(text = "a.kt")))
        assertFalse(stream.add(StreamFrame.ReasoningComplete(id = null, content = listOf("Look at a.kt"))))
        assertFalse(stream.add(StreamFrame.TextDelta("")))
        assertTrue(stream.add(StreamFrame.TextDelta("It starts ")))
        assertTrue(stream.add(StreamFrame.TextDelta("the app.")))
        assertFalse(stream.add(StreamFrame.TextComplete("It starts the app.")))
        assertEquals("Look at a.kt" to "It starts the app.", stream.shown())
        val usage = ResponseMetaInfo.Empty.copy(inputTokensCount = 10, outputTokensCount = 5)
        stream.add(StreamFrame.End("stop", usage))

        val reply = stream.reply()
        assertEquals(listOf("Look at a.kt"), (reply.parts[0] as MessagePart.Reasoning).content)
        assertEquals("It starts the app.", (reply.parts[1] as MessagePart.Text).text)
        assertEquals("stop", reply.finishReason)
        assertEquals(usage, reply.metaInfo)
        // The prompt goes on without the reasoning.
        assertEquals(listOf(MessagePart.Text("It starts the app.")), stream.echo().parts)
    }

    @Test fun `a call's arguments are kept as sent, blank ones as an empty object`() {
        val stream = KoogStream()
        stream.add(StreamFrame.ToolCallDelta("c1", "list_files", null))
        stream.add(StreamFrame.ToolCallComplete("c1", "list_files", ""))
        stream.add(StreamFrame.ToolCallComplete("c2", "read_file", "{not json"))
        stream.add(StreamFrame.End("tool_calls"))
        val calls = stream.echo().parts.filterIsInstance<MessagePart.Tool.Call>()
        assertEquals(listOf("{}", "{not json"), calls.map { it.args })
        assertEquals(listOf("c1", "c2"), calls.map { it.id })
        assertEquals("tool_calls", stream.echo().finishReason)
    }

    @Test fun `a think block written into the text is shown as reasoning, an unclosed one all of it`() {
        val stream = KoogStream()
        stream.add(StreamFrame.TextDelta("<think>\nCheck a.kt"))
        assertEquals("Check a.kt" to "", stream.shown())
        stream.add(StreamFrame.TextDelta(" first.\n</think>\n\nIt starts"))
        assertEquals("Check a.kt first." to "It starts", stream.shown())
    }
}
