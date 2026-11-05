package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.BuildConfig
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ClientCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequest
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListPromptsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListResourcesRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.Method
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Prompt
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequest
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.Resource
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolListChangedNotification
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject

/**
 * One server, connected (docs/mcp-plan.md §5): the SDK's client over the runtime's stdio or HTTP, the
 * server's tools, prompts and resources read page by page, its tool list read again when it says it
 * changed, its questions handed to the user, and the end of a local server's process noticed.
 */
internal class McpConnection(
    val server: McpServer,
    private val scope: CoroutineScope,
    private val local: McpLocal,
    private val remote: McpRemote,
    /** Its values with their secrets: a local server's environment, a remote one's headers. */
    private val values: List<McpValue>,
    /** A signed-in server's `Authorization` value (M5); null otherwise. */
    private val authorization: suspend () -> String?,
    private val events: Events,
) {
    /** What a connection tells the servers' owner as it happens. */
    interface Events {
        fun toolsChanged(connection: McpConnection)

        /** A local server's process ended, not by [close]: [failure] says why. */
        fun ended(connection: McpConnection, failure: String)

        /** The server asks the user something mid-call (M4); suspends until answered. */
        suspend fun ask(connection: McpConnection, request: ElicitRequest): ElicitResult
    }

    private var client: Client = client()
    private var started: McpLocal.Started? = null
    @Volatile private var closing = false

    @Volatile var tools: List<Tool> = emptyList()
        private set
    @Volatile var prompts: List<Prompt> = emptyList()
        private set
    @Volatile var resources: List<Resource> = emptyList()
        private set

    val instructions: String? get() = client.serverInstructions
    val serverInfo: String? get() = client.serverVersion?.let { "${it.title ?: it.name} ${it.version}" }

    /** Starts the server or reaches it, and reads what it offers, within [START_MILLIS]. Throws. */
    suspend fun connect() = withTimeout(START_MILLIS.milliseconds) {
        when (val transport = server.transport) {
            is McpTransport.Local -> {
                val begun = local.start(server.id, transport, values, scope).also { started = it }
                scope.launch {
                    val code = begun.ended.await()
                    if (!closing) events.ended(this@McpConnection, McpFailures.of(Exception("exit $code"), server.name, log = local.tail(server.id)))
                }
                client.connect(begun.transport)
            }
            is McpTransport.Remote -> {
                val headers = values.mapNotNull { value -> value.value?.let { value.name to it } }.toMap() +
                    listOfNotNull(authorization()?.let { "Authorization" to it })
                try {
                    client.connect(remote.streamable(transport.url, headers))
                } catch (failure: Exception) {
                    if (!remote.speaksOlder(failure)) throw failure
                    client = client()
                    client.connect(remote.older(transport.url, headers))
                }
            }
        }
        read()
    }

    /** Reads the server's tools, and its prompts and resources where it has them. */
    suspend fun read() {
        tools = pages { cursor -> client.listTools(ListToolsRequest(PaginatedRequestParams(cursor))).let { it.tools to it.nextCursor } }
        val capabilities = client.serverCapabilities
        prompts = if (capabilities?.prompts == null) emptyList()
        else pages { cursor -> client.listPrompts(ListPromptsRequest(PaginatedRequestParams(cursor))).let { it.prompts to it.nextCursor } }
        resources = if (capabilities?.resources == null) emptyList()
        else pages { cursor -> client.listResources(ListResourcesRequest(PaginatedRequestParams(cursor))).let { it.resources to it.nextCursor } }
    }

    /**
     * Calls [tool] with [arguments]. The SDK's own deadline is [CALL_CEILING]: the tool's own time is
     * [McpTool]'s, paused while the user is asked. Cancelling the caller cancels the call on the server.
     */
    suspend fun call(tool: String, arguments: JsonObject, onProgress: (String) -> Unit): CallToolResult =
        client.callTool(
            CallToolRequest(CallToolRequestParams(tool, arguments)),
            RequestOptions(onProgress = { onProgress(McpResults.progress(it)) }, timeout = CALL_CEILING),
        )

    suspend fun prompt(name: String, arguments: Map<String, String>): GetPromptResult =
        client.getPrompt(GetPromptRequest(GetPromptRequestParams(name, arguments)))

    suspend fun read(uri: String): ReadResourceResult = client.readResource(ReadResourceRequest(ReadResourceRequestParams(uri)))

    /** Stops a local server as the spec does; a remote one is let go. */
    suspend fun close() {
        closing = true
        runCatching { client.close() }
        started?.stop()
    }

    private fun client(): Client = Client(
        Implementation(name = "iCode", version = BuildConfig.VERSION_NAME),
        // Elicitation alone: Roots, Sampling and Logging are deprecated (§2.1, D5).
        ClientOptions(ClientCapabilities(elicitation = ClientCapabilities.Elicitation(form = JsonObject(emptyMap()), url = JsonObject(emptyMap())))),
    ).apply {
        setRequestHandler<ElicitRequest>(Method.Defined.ElicitationCreate) { request, _ -> events.ask(this@McpConnection, request) }
        setNotificationHandler<ToolListChangedNotification>(Method.Defined.NotificationsToolsListChanged) {
            scope.launch { runCatching { read() }.onSuccess { events.toolsChanged(this@McpConnection) } }
            CompletableDeferred(Unit)
        }
    }

    /** Every page of a list, at most [MAX_LISTED] items: a server is never read without end. */
    private suspend fun <T> pages(page: suspend (String?) -> Pair<List<T>, String?>): List<T> {
        val all = mutableListOf<T>()
        var cursor: String? = null
        do {
            val (items, next) = page(cursor)
            all += items
            cursor = next
        } while (cursor != null && all.size < MAX_LISTED)
        return all.take(MAX_LISTED)
    }

    companion object {
        /** A server's start and first reads (§7.3). */
        const val START_MILLIS = 60_000L
        private val CALL_CEILING = 30.minutes
        private const val MAX_LISTED = 1_000
    }
}
