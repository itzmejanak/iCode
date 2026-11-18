package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.model.AgentCapabilities
import com.agentclientprotocol.model.EnvVariable
import com.agentclientprotocol.model.HttpHeader
import com.agentclientprotocol.model.McpServer
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.mcp.McpSchema
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

/**
 * iCode's tools and the user's MCP servers, served to the coding agents (docs/assistant-and-mcp.md): one MCP
 * server on 127.0.0.1 over Streamable HTTP, each agent session given its own token, since other apps on the
 * phone can reach local ports. A session lists the tools its chat is offered; a call runs only while the agent
 * answers one of the chat's messages, through the gate the assistant's own calls pass, the servers' keys and
 * sign-ins staying in iCode (owner's choices, 2026-11-18). An agent that takes no MCP server over HTTP reaches
 * it through a bridge Node.js runs, written to [bridge]. What passes through it is never logged.
 */
internal class AgentToolServer(
    /** The tools a session in [chat]'s place is offered, for their names and schemas. */
    private val offered: suspend (chat: Pair<String?, String>) -> List<AssistantTool>,
    /** The bridge, as the phone sees it and as its path in the runtime. */
    private val bridge: Pair<File, String>,
    private val version: String,
) {
    private val tokens = ConcurrentHashMap<String, ChatOperations>()
    private val lock = Mutex()
    private var engine: EmbeddedServer<*, *>? = null
    private var port = 0

    /** The server [operations]' new session is given: over HTTP where the agent takes it, else through the bridge; none with no tool to offer. */
    suspend fun serversFor(capabilities: AgentCapabilities, operations: ChatOperations): List<McpServer> {
        val tools = offered(operations.chat).takeIf { it.isNotEmpty() } ?: return emptyList()
        operations.offered = tools
        val token = ByteArray(TOKEN_BYTES).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        tokens[token] = operations
        val url = "http://$HOST:${started()}/mcp"
        return listOf(
            if (capabilities.mcpCapabilities.http) McpServer.Http(NAME, url, listOf(HttpHeader(HttpHeaders.Authorization, "Bearer $token")))
            else McpServer.Stdio(NAME, "node", listOf(bridge.second), listOf(EnvVariable(URL, url), EnvVariable(TOKEN, token))),
        )
    }

    /** The sessions [ended] says have: their tokens no longer open the server. */
    fun forget(ended: (ChatOperations) -> Boolean) {
        tokens.values.removeIf(ended)
    }

    private suspend fun started(): Int = lock.withLock {
        if (engine == null) withContext(Dispatchers.IO) {
            bridge.first.parentFile?.mkdirs()
            bridge.first.writeText(BRIDGE)
            val server = served().start(wait = false)
            port = server.engine.resolvedConnectors().single().port
            engine = server
        }
        port
    }

    /** The server, outside any coroutine: one started within one would be its child, and never let it end. */
    private fun served(): EmbeddedServer<*, *> = embeddedServer(CIO, host = HOST, port = 0) {
        // No token, no server: not even its list of tools.
        intercept(ApplicationCallPipeline.Plugins) {
            if (operationsOf(call) == null) {
                call.respond(HttpStatusCode.Unauthorized)
                finish()
            }
        }
        mcpStreamableHttp { serverFor(checkNotNull(operationsOf(call))) }
    }

    private fun operationsOf(call: ApplicationCall): ChatOperations? =
        call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.let(tokens::get)

    /** A connection's server: the tools its session was offered, each run by the turn at work. */
    private fun serverFor(operations: ChatOperations): Server =
        Server(Implementation(NAME, version, "iCode"), ServerOptions(ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = false)))).apply {
            operations.offered.forEach { tool -> addTool(tool.name, tool.description, McpSchema.schemaOf(tool.parameters)) { request -> answer(operations, tool.name, request.arguments) } }
        }

    /** [name] run with [arguments] by the turn the agent answers now, through its gate; an error the agent reads otherwise. */
    private suspend fun answer(operations: ChatOperations, name: String, arguments: JsonObject?): CallToolResult {
        val tool = operations.tools.firstOrNull { it.name == name } ?: return failed(if (operations.tools.isEmpty()) IDLE else "$name is not offered now")
        return try {
            CallToolResult(listOf(TextContent(tool.run(JSONObject(arguments?.toString() ?: "{}")))))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (refused: AssistantToolException) {
            failed(refused.message ?: "Refused")
        } catch (broken: Exception) {
            failed(broken.message ?: broken.javaClass.simpleName)
        }
    }

    private fun failed(why: String) = CallToolResult(listOf(TextContent(why)), isError = true)

    companion object {
        /** The server's name, as agents prefix its tools. */
        const val NAME = "icode"
        private const val HOST = "127.0.0.1"
        private const val TOKEN_BYTES = 32
        private const val URL = "ICODE_MCP_URL"
        private const val TOKEN = "ICODE_MCP_TOKEN"
        private const val IDLE = "iCode's tools run only while you answer a message in iCode"

        /** Where the bridge is kept in the runtime's home. */
        const val BRIDGE_PATH = ".icode/agents/icode-mcp-bridge.cjs"

        /**
         * Each line the agent writes, a JSON-RPC message, posted to the server with the session's token; each
         * answer, plain or as server-sent events, written back as a line. A message it cannot deliver is
         * answered with an error, and nothing that passes is written anywhere else.
         */
        val BRIDGE = """
            const url = process.env.$URL, token = process.env.$TOKEN;
            let session = null;
            const out = (message) => process.stdout.write(JSON.stringify(message) + "\n");
            require("readline").createInterface({ input: process.stdin }).on("line", async (line) => {
              if (!line.trim()) return;
              const headers = { "content-type": "application/json", accept: "application/json, text/event-stream", authorization: "Bearer " + token };
              if (session) headers["mcp-session-id"] = session;
              try {
                const response = await fetch(url, { method: "POST", headers, body: line });
                session = response.headers.get("mcp-session-id") || session;
                const text = await response.text();
                if ((response.headers.get("content-type") || "").includes("text/event-stream")) {
                  for (const event of text.split(/\r?\n\r?\n/)) {
                    const data = event.split(/\r?\n/).filter((l) => l.startsWith("data:")).map((l) => l.slice(5).trimStart()).join("\n");
                    if (data) out(JSON.parse(data));
                  }
                } else if (text.trim()) out(JSON.parse(text));
              } catch (failure) {
                const message = JSON.parse(line);
                if (message.id !== undefined) out({ jsonrpc: "2.0", id: message.id, error: { code: -32000, message: "iCode's tools cannot be reached" } });
              }
            });
        """.trimIndent() + "\n"
    }
}
