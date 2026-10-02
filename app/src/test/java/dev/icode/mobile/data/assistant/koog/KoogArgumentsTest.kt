package dev.icode.mobile.data.assistant.koog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogArgumentsTest {
    /** A long script cut at the output limit inside its string: escapes in it, no closing quote. */
    private val cut = "{\"command\":\"python3 - <<'EOF'\\nimport json\\nprint(\\\"F) Fix the docs"

    @Test fun `an object is sent as it is, none as an empty one, anything else carried in one`() {
        assertEquals("""{"path":"a.kt"}""", KoogArguments.readable("""{"path":"a.kt"}"""))
        assertEquals("{}", KoogArguments.readable(""))
        assertEquals("{}", KoogArguments.readable("  "))
        for (written in listOf(cut, "{not json", "[1,2]", "\"a.kt\"")) {
            val carried = KoogArguments.readable(written)
            assertTrue(carried, Json.parseToJsonElement(carried) is kotlinx.serialization.json.JsonObject)
            assertEquals(written, KoogArguments.unreadable(carried))
        }
        assertNull(KoogArguments.unreadable("""{"path":"a.kt"}"""))
        assertNull(KoogArguments.unreadable(cut))
    }

    @Test fun `the model is told the call did not run, how long it was and where it ended`() {
        val said = KoogArguments.refusal(cut)
        assertTrue(said, said.startsWith("The call did not run: its arguments were not a complete JSON object (${cut.length} characters, ending “"))
        assertTrue(said, said.contains("print(\\\"F) Fix the docs”"))
        assertTrue(said, said.contains("cut at the output limit"))
    }

    @Test fun `a whole reply's unreadable arguments are carried, the rest of it untouched`() {
        val body = """{"id":"gen-1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"finish_reason":"length",""" +
            """"message":{"role":"assistant","content":"Next:","tool_calls":[""" +
            """{"id":"c1","type":"function","function":{"name":"read_file","arguments":"{\"path\":\"a.kt\"}"}},""" +
            """{"id":"c2","type":"function","function":{"name":"run_command","arguments":${Json.encodeToString(kotlinx.serialization.json.JsonPrimitive.serializer(), kotlinx.serialization.json.JsonPrimitive(cut))}}},""" +
            """{"id":"c3","type":"function","function":{"name":"git_status","arguments":""}}]}}],"usage":{"prompt_tokens":1,"completion_tokens":2,"total_tokens":3}}"""
        val fixed = Json.parseToJsonElement(KoogArguments.readableResponse(body)).jsonObject
        val calls = fixed["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["tool_calls"]!!.jsonArray
        fun arguments(index: Int) = calls[index].jsonObject["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content
        assertEquals("""{"path":"a.kt"}""", arguments(0))
        assertEquals(cut, KoogArguments.unreadable(arguments(1)))
        // Koog reads an empty string as no arguments itself.
        assertEquals("", arguments(2))
        assertEquals("Next:", fixed["choices"]!!.jsonArray[0].jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("length", fixed["choices"]!!.jsonArray[0].jsonObject["finish_reason"]!!.jsonPrimitive.content)

        // Nothing to change: the body as it came.
        val clean = body.replace(Regex(""",\{"id":"c2".*?\}\}"""), "")
        assertSame(clean, KoogArguments.readableResponse(clean))
        assertSame("not json at all", KoogArguments.readableResponse("not json at all"))
    }
}
