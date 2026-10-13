package dev.icode.mobile.data.assistant.koog

import ai.koog.http.client.ktor.KtorKoogHttpClient
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Koog's own OpenAI client, as iCode makes it, over HTTP from a local server. */
class KoogClientsTest {
    private val replies = mutableListOf<String>()
    private val streams = mutableListOf<String>()
    private var answered = 0
    private var streamed = 0

    /**
     * A whole request answered with the next of [replies], a streamed one with the next of [streams];
     * past the last, the last again. With no [streams], a stream is refused as a server that does not
     * take `stream_options` refuses it.
     */
    private val server = LocalHttpServer { request ->
        when {
            !request.streams -> LocalHttpServer.Response(200, "application/json", replies[minOf(++answered, replies.size) - 1])
            streams.isEmpty() -> LocalHttpServer.Response(400, "application/json", """{"error":{"message":"Unrecognized request argument supplied: stream_options"}}""")
            else -> LocalHttpServer.Response(200, "text/event-stream", streams[minOf(++streamed, streams.size) - 1])
        }
    }

    private val LocalHttpServer.Request.streams: Boolean get() = JSONObject(body).optBoolean("stream")

    private val originalWriter = BackendLog.writer
    private val logged = Collections.synchronizedList(mutableListOf<String>())

    @Before fun log() {
        BackendLog.writer = { _, _, message -> logged += message }
    }

    @After fun stop() {
        server.close()
        BackendLog.writer = originalWriter
    }

    private val seen = Collections.synchronizedList(mutableListOf<Any>())
    private val events = object : AssistantEvents {
        override fun writing(reasoning: String, text: String) { seen += "writing:$reasoning|$text" }
        override fun replied(reply: AssistantMessage.Reply) { seen += reply }
        override fun calling(call: AssistantCall) = Unit
        override fun answered(result: AssistantResult) { seen += result }
        override fun used(tokens: Long) { seen += "used:$tokens" }
    }
    private val engine = KoogAssistantEngine { KoogClients.create(it, KtorKoogHttpClient.Factory(baseClient = HttpClient(OkHttp))) }

    private fun turn(tool: AssistantTool, model: String = "free/deepseek-v4-pro-0813") = AssistantTurnRequest(
        AssistantConnection(AssistantProviderKind.OpenAICompatible, server.address("/v1"), "sk-test"),
        KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec(model)), null,
        "You are iCode's assistant.", emptyList(), AssistantMessage.User("What does a.kt do?"), listOf(tool), 40,
    )

    private val reads = mutableListOf<String>()
    private val readFile = object : AssistantTool {
        override val name = "read_file"
        override val description = "Reads a file"
        override val parameters = listOf(AssistantParameter("path", "The file"))
        override val kind = AssistantToolKind.Read
        override fun summary(arguments: JSONObject) = "Read ${arguments.optString("path")}"
        override suspend fun run(arguments: JSONObject): String = "fun main() {}".also { reads += arguments.getString("path") }
    }

    /** An OpenAI-compatible server's stream, chunk by chunk, then its end. */
    private fun sse(vararg chunks: String) = (chunks.toList() + "[DONE]").joinToString("") { "data: $it\n\n" }

    private fun chunk(delta: String, finish: String? = null) =
        """{"id":"c1","object":"chat.completion.chunk","created":1,"model":"free/deepseek-v4-pro-0813","choices":[{"index":0,"delta":$delta,"finish_reason":${finish?.let { "\"$it\"" } ?: "null"}}]}"""

    private fun usage(input: Int, output: Int) =
        """{"id":"c1","object":"chat.completion.chunk","created":1,"model":"free/deepseek-v4-pro-0813","choices":[],"usage":{"prompt_tokens":$input,"completion_tokens":$output,"total_tokens":${input + output}}}"""

    @Test fun `an OpenAI-compatible server's reply streams, its reasoning and its calls with it, and its usage read`() = runBlocking {
        streams += sse(
            chunk("""{"role":"assistant","reasoning_content":"Need "}"""),
            chunk("""{"reasoning_content":"a.kt."}"""),
            chunk("""{"content":"Read"}"""),
            chunk("""{"content":"ing"}"""),
            chunk("""{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_file","arguments":""}}]}"""),
            chunk("""{"tool_calls":[{"index":0,"function":{"arguments":"{\"path\":"}}]}"""),
            chunk("""{"tool_calls":[{"index":0,"function":{"arguments":"\"a.kt\"}"}}]}"""),
            chunk("{}", "tool_calls"),
            usage(1_200, 40),
        )
        streams += sse(chunk("""{"role":"assistant","content":"Do"}"""), chunk("""{"content":"ne"}"""), chunk("{}", "stop"), usage(1_300, 20))
        engine.run(turn(readFile), events)

        assertEquals(listOf("a.kt"), reads)
        val call = AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read)
        assertEquals(
            listOf(
                "used:1240",
                AssistantMessage.Reply("Reading", listOf(call), reasoning = "Need a.kt."),
                AssistantResult("call_1", "read_file", "fun main() {}", failed = false),
                "used:1320",
                AssistantMessage.Reply("Done"),
            ),
            seen.filterNot { it is String && it.startsWith("writing:") },
        )
        // Both requests streamed, asking for the usage, and none went whole.
        assertEquals(2, server.requests.size)
        assertTrue(server.requests.all { JSONObject(it.body).getJSONObject("stream_options").getBoolean("include_usage") })
        // The next request went on from the reply without its reasoning.
        val echoed = JSONObject(server.requests[1].body).getJSONArray("messages").getJSONObject(2)
        assertEquals("Reading", echoed.getString("content"))
        assertTrue(!echoed.has("reasoning_content"))
    }

    @Test fun `a stream laxer than OpenAI's is read whole — keep-alives, blank events, chunks short of fields`() = runBlocking {
        // The phone's RadoxAI turns: text came, then an event Koog's reader refused, mid-reply.
        streams += "data: ${chunk("""{"role":"assistant","content":"Starting the "}""")}\n\n" +
            "event: ping\ndata: {\"type\":\"ping\"}\n\n" +
            "data: \n\n" +
            // No id, object, created or model; a choice with no index.
            "data: {\"choices\":[{\"delta\":{\"content\":\"audit\"}}]}\n\n" +
            // A call, and the rest of its arguments, with no index.
            "data: ${chunk("""{"tool_calls":[{"id":"call_1","type":"function","function":{"name":"read_file","arguments":""}}]}""")}\n\n" +
            "data: ${chunk("""{"tool_calls":[{"function":{"arguments":"{\"path\":\"a.kt\"}"}}]}""")}\n\n" +
            "data: ${chunk("{}", "tool_calls")}\n\n" +
            // The usage alone, without choices.
            "data: {\"usage\":{\"prompt_tokens\":1200,\"completion_tokens\":40,\"total_tokens\":1240}}\n\n" +
            "data: [DONE]\n\n"
        streams += sse(chunk("""{"role":"assistant","content":"Done"}""", "stop"))
        engine.run(turn(readFile), events)

        assertEquals(listOf("a.kt"), reads)
        val call = AssistantCall("call_1", "read_file", """{"path":"a.kt"}""", "Read a.kt", AssistantToolKind.Read)
        assertEquals(
            listOf(
                "used:1240",
                AssistantMessage.Reply("Starting the audit", listOf(call)),
                AssistantResult("call_1", "read_file", "fun main() {}", failed = false),
                AssistantMessage.Reply("Done"),
            ),
            seen.filterNot { it is String && it.startsWith("writing:") },
        )
        assertEquals("both requests streamed", listOf(true, true), server.requests.map { it.streams })
    }

    @Test fun `a stream that breaks mid-reply says why, never that the address is wrong`() = runBlocking {
        // The provider's own failure, sent as OpenAI's API sends one.
        streams += sse(chunk("""{"role":"assistant","content":"Starting"}"""), """{"error":{"message":"Upstream overloaded","type":"server_error"}}""")
        val failed = runCatching { engine.run(turn(readFile), events) }.exceptionOrNull()
        assertEquals("The model's provider failed while writing the reply: Upstream overloaded", failed?.message)
        // A chunk no reading makes sense of.
        streams.clear()
        streams += sse(chunk("""{"role":"assistant","content":"Starting"}"""), chunk("""{"tool_calls":"not a list"}"""))
        val unreadable = runCatching { engine.run(turn(readFile), events) }.exceptionOrNull()
        assertEquals("The provider's reply could not be read — try again", unreadable?.message)
        // The provider failing before it wrote anything is no refusal to stream: no whole request, and the next streams.
        streams.clear()
        streams += sse("""{"error":{"message":"Rate limited"}}""")
        val first = runCatching { engine.run(turn(readFile), events) }.exceptionOrNull()
        assertEquals("The model's provider failed while writing the reply: Rate limited", first?.message)
        assertTrue("every request streamed", server.requests.all { it.streams })
        // The log says what broke by its types; never what the reply held.
        val said = logged.single { it.startsWith("A stream broke after it began: ") }
        assertTrue(said, "SerializationException" in said || "JsonDecodingException" in said)
        assertFalse(said, "not a list" in said || "Starting" in said)
    }

    @Test fun `a server that refuses a stream is answered whole, and not asked to stream again`() = runBlocking {
        replies += completion("""{"role":"assistant","content":"Done"}""", "stop")
        engine.run(turn(readFile), events)
        assertEquals(AssistantMessage.Reply("Done"), seen.last())
        assertEquals(listOf(true, false), server.requests.map { it.streams })
        engine.run(turn(readFile), events)
        assertEquals("the next turn goes whole at once", listOf(true, false, false), server.requests.map { it.streams })
    }

    private fun completion(message: String, finish: String) =
        """{"id":"gen-1","object":"chat.completion","created":1,"model":"m","choices":[{"index":0,"finish_reason":"$finish","message":$message}],""" +
            """"usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}"""

    @Test fun `a whole reply whose call was cut inside its arguments is read, the call refused and the turn goes on`() = runBlocking {
        // The phone's case: a long script cut at the output limit, escapes in it, no closing quote —
        // Koog's own reader threw "Unexpected EOF at path: $" and lost the reply.
        val cut = "{\"command\":\"python3 - <<'EOF'\\nimport json\\nprint(\\\"F) Fix the docs"
        val quoted = JSONObject.quote(cut)
        replies += completion("""{"role":"assistant","content":"Next, G:","tool_calls":[{"id":"call_1","type":"function","function":{"name":"run_command","arguments":$quoted}}]}""", "length")
        replies += completion("""{"role":"assistant","content":"Done"}""", "stop")
        val ran = mutableListOf<String>()
        val tool = object : AssistantTool {
            override val name = "run_command"
            override val description = "Runs a command"
            override val parameters = listOf(AssistantParameter("command", "The command"))
            override val kind = AssistantToolKind.Command
            override fun summary(arguments: JSONObject) = "Run `${arguments.optString("command")}`"
            override suspend fun run(arguments: JSONObject): String = "ran".also { ran += arguments.toString() }
        }
        // A server that does not stream: the whole reply is the one read.
        engine.run(turn(tool, model = "minimax/minimax-m3:free"), events)

        assertTrue(ran.isEmpty())
        val reply = seen.filterIsInstance<AssistantMessage.Reply>().first()
        assertEquals("Next, G:", reply.text)
        assertEquals(cut, reply.calls.single().arguments)
        assertEquals(AssistantResult("call_1", "run_command", KoogArguments.refusal(cut), failed = true), seen.filterIsInstance<AssistantResult>().single())
        assertEquals(AssistantMessage.Reply("Done"), seen.last())
        // The next request carried the call as an object that holds what was written.
        val sent = JSONObject(server.requests.last().body).getJSONArray("messages").getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0)
        assertEquals(cut, KoogArguments.unreadable(sent.getJSONObject("function").getString("arguments")))
    }
}
