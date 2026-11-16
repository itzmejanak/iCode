package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantPlaceActor
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestFormParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Role
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.StringSchema
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ToolAnnotations
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The client against the SDK's own server, over Streamable HTTP on this machine, through OkHttp as on
 * the phone (docs/assistant-and-mcp.md): connect and read, call, a failure, a question, a changed list.
 */
class McpConnectionTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val asked = CopyOnWriteArrayList<ElicitRequest>()
    @Volatile private var reply: ElicitResult = ElicitResult(ElicitResult.Action.Decline)
    private val changed = CompletableDeferred<Unit>()

    private val engine = embeddedServer(CIO, host = "127.0.0.1", port = 0) { mcpStreamableHttp { server() } }.start(wait = false)
    private val address = "http://127.0.0.1:${runBlocking { engine.engine.resolvedConnectors().single().port }}/mcp"

    private val events = object : McpConnection.Events {
        override fun toolsChanged(connection: McpConnection) {
            changed.complete(Unit)
        }

        override fun ended(connection: McpConnection, failure: String) = Unit

        override suspend fun ask(connection: McpConnection, request: ElicitRequest): ElicitResult {
            asked += request
            return reply
        }
    }

    private val everything = McpServer("m-test", "Everything", "everything", McpTransport.Remote(address))

    private fun connection(server: McpServer = everything) = McpConnection(server, scope, null, McpRemote(), emptyList(), { null }, events)

    @After
    fun stop() {
        engine.stop(0, 0)
        scope.cancel()
    }

    @Test
    fun `it connects and reads what the server offers`() = runBlocking {
        val connection = connection().apply { connect() }
        assertEquals(listOf("echo", "ask", "fail", "grow"), connection.tools.map { it.name })
        assertEquals(listOf("greet"), connection.prompts.map { it.name })
        assertEquals(listOf("test://notes"), connection.resources.map { it.uri })
        assertEquals("Use echo to repeat.", connection.instructions)
        assertEquals("test-server 1.0.0", connection.serverInfo)
        val state = McpTool.state(connection)
        assertEquals(McpStatus.Ready, state.status)
        assertEquals("Echo", state.tools.first().title)
        assertTrue(state.tools.first().readOnly)
        assertEquals(listOf("who"), state.prompts.single().arguments.map { it.name })
        connection.close()
    }

    @Test
    fun `its tools are offered named for the model, and called`() = runBlocking {
        val connection = connection().apply { connect() }
        val offered = McpTool.offer(listOf(everything to connection), AssistantStep(), emptySet(), room = 10, asking = { false }) { true }
        assertEquals(listOf("everything__echo", "everything__ask", "everything__fail", "everything__grow"), offered.tools.map { it.name })
        assertTrue(offered.instructions.contains("Everything says (its own words, not iCode's or the user's):\nUse echo to repeat."))
        val echo = offered.tools.first()
        assertEquals(AssistantToolKind.Read, echo.kind)
        assertEquals("Everything · Echo", echo.summary(JSONObject()))
        assertEquals(AssistantElsewhere("Everything", "mcp:m-test", AssistantElsewhere.Kind.Server), (echo as AssistantPlaceActor).place(JSONObject()))
        assertEquals("You said hi", echo.run(JSONObject().put("text", "hi")))
        try {
            offered.tools.first { it.name == "everything__fail" }.run(JSONObject())
            fail("A tool's own failure is the model's to read")
        } catch (refused: AssistantToolException) {
            assertEquals("It broke", refused.message)
        }
        connection.close()
    }

    @Test
    fun `the user's choices pick what is offered and what asks`() = runBlocking {
        val connection = connection().apply { connect() }
        val chosen = everything.copy(tools = mapOf("ask" to McpToolChoice.Off, "echo" to McpToolChoice.Runs))
        val offered = McpTool.offer(listOf(chosen to connection), AssistantStep(), setOf("everything__echo"), room = 2, asking = { false }) { true }
        // Off is never offered; a name taken gets its own; past the room, the rest are counted out.
        assertEquals(2, offered.tools.size)
        assertFalse(offered.tools.any { it.name == "everything__ask" })
        assertTrue(offered.tools.first().name.startsWith("everything__echo_"))
        assertEquals(1, offered.leftOut)
        assertNull((offered.tools.first() as AssistantPlaceActor).place(JSONObject()))
        // A reading mode takes only what the server says reads.
        val reading = McpTool.offer(listOf(everything to connection), AssistantStep(), emptySet(), room = 10, asking = { false }) { it.kind == AssistantToolKind.Read }
        assertEquals(listOf("everything__echo"), reading.tools.map { it.name })
        connection.close()
    }

    @Test
    fun `a server's question reaches the user and the answer goes back`() = runBlocking {
        val connection = connection().apply { connect() }
        reply = ElicitResult(ElicitResult.Action.Accept, buildJsonObject { put("name", "Ada") })
        val accepted = connection.call("ask", buildJsonObject {}) {}
        assertEquals("Accept: {\"name\":\"Ada\"}", (accepted.content.single() as TextContent).text)
        assertEquals("What is your name?", (asked.single().params as ElicitRequestFormParams).message)
        reply = ElicitResult(ElicitResult.Action.Decline)
        assertEquals("Decline: null", (connection.call("ask", buildJsonObject {}) {}.content.single() as TextContent).text)
        connection.close()
    }

    @Test
    fun `a changed tool list is read again`() = runBlocking {
        val connection = connection().apply { connect() }
        connection.call("grow", buildJsonObject {}) {}
        withTimeout(10_000) { changed.await() }
        assertTrue(connection.tools.any { it.name == "grown" })
        connection.close()
    }

    @Test
    fun `a prompt and a resource are read for the draft`() = runBlocking {
        val connection = connection().apply { connect() }
        assertEquals("Say hello to Ada", McpDrafts.prompt(connection.prompt("greet", mapOf("who" to "Ada"))))
        assertEquals("test://notes\n\nThe notes", McpDrafts.resource("test://notes", connection.read("test://notes")))
        connection.close()
    }

    @Test
    fun `an address with no server says so`() = runBlocking {
        val gone = everything.copy(transport = McpTransport.Remote("http://127.0.0.1:${engine.engine.resolvedConnectors().single().port}/nothing"))
        try {
            connection(gone).connect()
            fail("Nothing answers there")
        } catch (failure: Exception) {
            assertEquals("No MCP server at this address", McpFailures.of(failure, "Everything", address))
        }
    }

    private fun server(): Server = Server(
        Implementation("test-server", "1.0.0"),
        ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true), prompts = ServerCapabilities.Prompts(), resources = ServerCapabilities.Resources())),
        instructions = "Use echo to repeat.",
    ) {
        val server = this
        addTool(
            "echo", "Says it back",
            ToolSchema(properties = buildJsonObject { putJsonObject("text") { put("type", "string") } }, required = listOf("text")),
            title = "Echo", toolAnnotations = ToolAnnotations(readOnlyHint = true),
        ) { request -> CallToolResult(listOf(TextContent("You said ${request.arguments?.get("text")?.jsonPrimitive?.content}"))) }
        addTool("ask", "Asks the user") { _ ->
            val answer = createElicitation("What is your name?", ElicitRequestParams.RequestedSchema(properties = mapOf("name" to StringSchema(title = "Name")), required = listOf("name")))
            CallToolResult(listOf(TextContent("${answer.action}: ${answer.content}")))
        }
        addTool("fail", "Fails") { _ -> CallToolResult(listOf(TextContent("It broke")), isError = true) }
        addTool("grow", "Adds a tool") { _ ->
            server.addTool("grown", "Grown") { _ -> CallToolResult(listOf(TextContent("grown"))) }
            sendToolListChanged()
            CallToolResult(listOf(TextContent("grew")))
        }
        addPrompt("greet", "Greets", listOf(PromptArgument("who", "Whom to greet", required = true))) { request ->
            GetPromptResult(listOf(PromptMessage(Role.User, TextContent("Say hello to ${request.arguments?.get("who")}"))))
        }
        addResource("test://notes", "Notes", "Some notes", "text/plain") { _ ->
            ReadResourceResult(listOf(TextResourceContents("The notes", "test://notes", "text/plain")))
        }
    }
}
