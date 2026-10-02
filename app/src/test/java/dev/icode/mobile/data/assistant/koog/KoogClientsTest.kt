package dev.icode.mobile.data.assistant.koog

import ai.koog.http.client.ktor.KtorKoogHttpClient
import com.sun.net.httpserver.HttpServer
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTurnRequest
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantToolKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.net.InetSocketAddress
import java.util.Collections
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Koog's own OpenAI client, as iCode makes it, over HTTP from a local server. */
class KoogClientsTest {
    private val bodies = Collections.synchronizedList(mutableListOf<String>())
    private val replies = mutableListOf<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            bodies += exchange.requestBody.readBytes().decodeToString()
            val reply = replies[minOf(bodies.size, replies.size) - 1].toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, reply.size.toLong())
            exchange.responseBody.use { it.write(reply) }
        }
        start()
    }

    @After fun stop() = server.stop(0)

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
        val seen = Collections.synchronizedList(mutableListOf<Any>())
        val events = object : AssistantEvents {
            override fun writing(reasoning: String, text: String) = Unit
            override fun replied(reply: AssistantMessage.Reply) { seen += reply }
            override fun calling(call: AssistantCall) = Unit
            override fun answered(result: AssistantResult) { seen += result }
            override fun used(tokens: Long) = Unit
        }
        val kind = AssistantProviderKind.OpenAICompatible
        val http = KtorKoogHttpClient.Factory(baseClient = HttpClient(OkHttp))
        val connection = AssistantConnection(kind, "http://127.0.0.1:${server.address.port}/v1", "sk-test")
        KoogAssistantEngine { KoogClients.create(it, http) }.run(
            AssistantTurnRequest(
                connection, KoogModels.model(kind, AssistantModelSpec("minimax/minimax-m3:free")), null,
                "You are iCode's assistant.", emptyList(), AssistantMessage.User("continue"), listOf(tool), 40,
            ),
            events,
        )

        assertTrue(ran.isEmpty())
        val reply = seen.filterIsInstance<AssistantMessage.Reply>().first()
        assertEquals("Next, G:", reply.text)
        assertEquals(cut, reply.calls.single().arguments)
        assertEquals(AssistantResult("call_1", "run_command", KoogArguments.refusal(cut), failed = true), seen.filterIsInstance<AssistantResult>().single())
        assertEquals(AssistantMessage.Reply("Done"), seen.last())
        // The next request carried the call as an object that holds what was written.
        val sent = JSONObject(bodies[1]).getJSONArray("messages").getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0)
        assertEquals(cut, KoogArguments.unreadable(sent.getJSONObject("function").getString("arguments")))
    }
}
