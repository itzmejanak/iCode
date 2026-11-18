package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.McpCapabilities
import com.agentclientprotocol.model.McpServer as AcpMcpServer
import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.mcp.McpConnection
import dev.icode.mobile.data.assistant.mcp.McpRemote
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** iCode's tools served to the agents: by token, only while a turn works, and through the bridge for agents without HTTP. */
class AgentToolServerTest {
    @get:Rule val temp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val bridge by lazy { File(temp.root, "home/.icode/agents/icode-mcp-bridge.cjs") }
    private val server by lazy { AgentToolServer({ listOf(run) }, bridge to bridge.path, "1.2.3") }
    private val operations = ChatOperations("claude-acp", "/w/app" to "c1", {})

    /** Stands for one of iCode's tools: it runs where it is told, or refuses. */
    private val run = object : AssistantTool {
        override val name = "run_android_app"
        override val description = "Builds and runs the app"
        override val parameters = listOf(AssistantParameter("target", "Where to run it"))
        override val kind = AssistantToolKind.Command

        override fun summary(arguments: JSONObject) = "Run the app"

        override suspend fun run(arguments: JSONObject): String {
            if (arguments.optString("target") == "nowhere") throw AssistantToolException("There is no such device")
            return "Ran on ${arguments.optString("target")}"
        }
    }

    private val events = object : McpConnection.Events {
        override fun toolsChanged(connection: McpConnection) = Unit

        override fun ended(connection: McpConnection, failure: String) = Unit

        override suspend fun ask(connection: McpConnection, request: ElicitRequest) = ElicitResult(ElicitResult.Action.Decline)
    }

    @After fun stop() = scope.cancel()

    private fun served(http: Boolean): AcpMcpServer =
        runBlocking { server.serversFor(AgentCapabilities(mcpCapabilities = McpCapabilities(http = http)), operations) }.single()

    private fun connection(url: String, authorization: String?) =
        McpConnection(McpServer("t", "iCode", "icode", McpTransport.Remote(url)), scope, null, McpRemote(), emptyList(), { authorization }, events)

    private fun text(result: io.modelcontextprotocol.kotlin.sdk.types.CallToolResult) = (result.content.single() as TextContent).text

    @Test(timeout = 60_000) fun `with its token a session lists the tools it was offered, and runs one only while a turn works`() = runBlocking {
        val http = served(http = true) as AcpMcpServer.Http
        assertEquals("icode", http.name)
        val connection = connection(http.url, http.headers.single { it.name == "Authorization" }.value).apply { connect() }
        assertEquals(listOf("run_android_app"), connection.tools.map { it.name })
        assertEquals(setOf("target"), connection.tools.single().inputSchema.properties?.keys)

        val idle = connection.call("run_android_app", buildJsonObject { put("target", JsonPrimitive("phone")) }) {}
        assertEquals(true, idle.isError)
        assertEquals("iCode's tools run only while you answer a message in iCode", text(idle))

        operations.tools = listOf(run)
        assertEquals("Ran on phone", text(connection.call("run_android_app", buildJsonObject { put("target", JsonPrimitive("phone")) }) {}))
        val refused = connection.call("run_android_app", buildJsonObject { put("target", JsonPrimitive("nowhere")) }) {}
        assertEquals(true, refused.isError)
        assertEquals("There is no such device", text(refused))
        connection.close()
    }

    @Test(timeout = 120_000) fun `without the session's token, or once it is forgotten, nothing is served`() = runBlocking {
        val http = served(http = true) as AcpMcpServer.Http
        val token = http.headers.single().value
        assertTrue(runCatching { connection(http.url, null).connect() }.isFailure)
        assertTrue(runCatching { connection(http.url, "Bearer not-the-token").connect() }.isFailure)
        server.forget { it === operations }
        assertTrue(runCatching { connection(http.url, token).connect() }.isFailure)
    }

    @Test(timeout = 60_000) fun `an agent that takes no server over HTTP is given the bridge, which Node runs against the server`() {
        val stdio = served(http = false) as AcpMcpServer.Stdio
        assertEquals("node", stdio.command)
        assertEquals(listOf(bridge.path), stdio.args)
        assertTrue(bridge.isFile)
        val node = listOf("/opt/node22/bin/node", "/usr/bin/node", "/usr/local/bin/node").firstOrNull { File(it).canExecute() }
        assumeTrue("Node.js runs the bridge", node != null)
        val process = ProcessBuilder(node, bridge.path).apply { environment().putAll(stdio.env.associate { it.name to it.value }) }.start()
        operations.tools = listOf(run)
        process.outputStream.bufferedWriter().apply {
            write("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"agent","version":"1"}}}""" + "\n")
            flush()
        }
        val lines = process.inputStream.bufferedReader()
        assertTrue(lines.readLine().contains("\"id\":1"))
        process.outputStream.bufferedWriter().apply {
            write("""{"jsonrpc":"2.0","method":"notifications/initialized"}""" + "\n")
            write("""{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"run_android_app","arguments":{"target":"phone"}}}""" + "\n")
            flush()
        }
        val called = lines.readLine()
        assertTrue(called, called.contains("\"id\":2") && called.contains("Ran on phone"))
        process.destroy()
        process.waitFor(5, TimeUnit.SECONDS)
    }
}
