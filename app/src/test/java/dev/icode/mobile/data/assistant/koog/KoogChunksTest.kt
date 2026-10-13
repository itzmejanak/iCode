package dev.icode.mobile.data.assistant.koog

import dev.icode.mobile.data.assistant.AssistantTurnException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class KoogChunksTest {
    private val empty = """{"id":"","object":"chat.completion.chunk","created":0,"model":"","choices":[]}"""

    @Test fun `a chunk as OpenAI sends it is read as it came`() {
        val chunk = """{"id":"c1","object":"chat.completion.chunk","created":1,"model":"m","choices":[{"index":0,"delta":{"content":"Hi"},"finish_reason":null}]}"""
        assertSame(chunk, KoogChunks.readable(chunk))
        val usage = """{"id":"c1","object":"chat.completion.chunk","created":1,"model":"m","choices":[],"usage":{"prompt_tokens":3}}"""
        assertSame(usage, KoogChunks.readable(usage))
    }

    @Test fun `an event with no chunk in it is an empty one`() {
        listOf("", "  ", "ping", "[1,2]").forEach { assertEquals(it, empty, KoogChunks.readable(it)) }
        // A keep-alive of another kind: a chunk with no choices.
        val ping = JSONObject(KoogChunks.readable("""{"type":"ping"}"""))
        assertEquals(0, ping.getJSONArray("choices").length())
        assertEquals("chat.completion.chunk", ping.getString("object"))
    }

    @Test fun `what a chunk lacks is given a value that says nothing, and what it says is kept`() {
        val read = JSONObject(KoogChunks.readable("""{"created":"soon","choices":[{"delta":{"content":"Hi","tool_calls":[{"id":"t1","function":{"name":"read_file"}},{"index":1,"function":{"arguments":"{}"}}]}},{"index":1}],"usage":{"prompt_tokens":3}}"""))
        assertEquals("", read.getString("id"))
        assertEquals("chat.completion.chunk", read.getString("object"))
        assertEquals(0, read.getLong("created"))
        assertEquals("", read.getString("model"))
        val first = read.getJSONArray("choices").getJSONObject(0)
        assertEquals(0, first.getInt("index"))
        assertEquals("Hi", first.getJSONObject("delta").getString("content"))
        val calls = first.getJSONObject("delta").getJSONArray("tool_calls")
        assertEquals(listOf(0, 1), (0 until calls.length()).map { calls.getJSONObject(it).getInt("index") })
        assertEquals("t1", calls.getJSONObject(0).getString("id"))
        val second = read.getJSONArray("choices").getJSONObject(1)
        assertEquals(1, second.getInt("index"))
        assertEquals(0, second.getJSONObject("delta").length())
        assertEquals(3, read.getJSONObject("usage").getInt("prompt_tokens"))
    }

    @Test fun `an event that says the provider failed is that failure, in its words`() {
        fun said(event: String): String? = try {
            KoogChunks.readable(event)
            fail("expected the failure")
            null
        } catch (failed: AssistantTurnException) {
            failed.message
        }
        assertEquals("The model's provider failed while writing the reply: Overloaded", said("""{"error":{"message":" Overloaded ","code":529}}"""))
        assertEquals("The model's provider failed while writing the reply: rate limited", said("""{"error":"rate limited"}"""))
        assertEquals(KoogFailures.PROVIDER_FAILED, said("""{"error":{"code":500}}"""))
        // An error field that says nothing is none: a chunk with no reply in it.
        assertEquals(0, JSONObject(KoogChunks.readable("""{"error":false,"choices":[]}""")).getJSONArray("choices").length())
        assertEquals(0, JSONObject(KoogChunks.readable("""{"error":""}""")).getJSONArray("choices").length())
        // A reply with an error field beside its choices is a reply.
        val chunk = """{"id":"c1","object":"chat.completion.chunk","created":1,"model":"m","choices":[{"index":0,"delta":{}}],"error":null}"""
        assertSame(chunk, KoogChunks.readable(chunk))
    }
}
