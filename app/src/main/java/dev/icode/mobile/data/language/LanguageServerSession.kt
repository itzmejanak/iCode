package dev.icode.mobile.data.language

import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.language.jsonrpc.JsonRpcConnection
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.domain.LanguageServiceConfiguration
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * One language server in one workspace (docs/lsp-plan.md §5.2): its process, its connection, what it
 * said it can do and the documents it has open. `initialize` → `initialized` → running →
 * `shutdown` → `exit`; anything it asks of iCode goes to [client].
 */
internal class LanguageServerSession(
    val config: LanguageServiceConfiguration,
    /** The workspace's top, a host location; the server's root. */
    val workspace: String,
    private val stdio: StdioPrograms,
    private val uris: LspUris,
    private val scope: CoroutineScope,
    private val client: Client,
) {
    /** What the server asks and tells, for the owner to act on. */
    interface Client {
        fun diagnostics(session: LanguageServerSession, params: JsonObject)
        fun log(session: LanguageServerSession, line: String)
        fun message(session: LanguageServerSession, type: Int, text: String)
        fun progress(session: LanguageServerSession, words: String?)
        fun capabilitiesChanged(session: LanguageServerSession)
        fun refreshDiagnostics(session: LanguageServerSession)
        suspend fun applyEdit(session: LanguageServerSession, edit: JsonObject): Boolean
    }

    @Volatile var capabilities = LspCapabilities(JsonObject(emptyMap()))
        private set
    val documents = LspDocuments { method, params -> notify(method, params) }
    private val settings: JsonObject = LanguageJson.objectOf(config.settings)
    private val progress = LspProgress()
    private var program: StdioProgram? = null
    private var connection: JsonRpcConnection? = null
    private val stopping = AtomicBoolean(false)

    /** The process's exit code, once it has ended. */
    val ended = CompletableDeferred<Int>()

    /** Whether iCode asked it to stop: an exit after that is not a crash. */
    val stopRequested: Boolean get() = stopping.get()

    val pid: Int? get() = program?.pid

    /** Starts the server and completes its handshake; throws a [LanguageFailure] or the connection's failure. */
    suspend fun start(argv: List<String>, variables: Map<String, String>, folders: List<String>) {
        stdio.missing(argv.first(), variables)?.let { throw LanguageFailure.Missing(it) }
        val guestTop = uris.guestPathOf(workspace) ?: throw LanguageFailure.NotStarted("Open a workspace in iCode's storage first")
        val started = stdio.start(config.id, argv, variables, guestTop, scope)
        program = started
        scope.launch { ended.complete(started.ended.await()) }
        val rpc = JsonRpcConnection(started.output, started.input, scope, handler)
        connection = rpc
        rpc.start()
        val result = rpc.request(
            "initialize",
            LspMessages.initialize(
                guestTop, uris.uriOfGuest(guestTop),
                folders.mapNotNull { folder -> uris.uriOf(folder)?.let { it to folder.substringAfterLast('/') } },
                LanguageJson.objectOf(config.initializationOptions).takeIf { it.isNotEmpty() },
                BuildConfig.VERSION_NAME, Locale.getDefault().toLanguageTag(),
            ),
            INITIALIZE_MILLIS,
        )
        capabilities = LspCapabilities((result as? JsonObject)?.get("capabilities") as? JsonObject ?: JsonObject(emptyMap()))
        documents.capabilities = capabilities
        rpc.notify("initialized", JsonObject(emptyMap()))
        if (settings.isNotEmpty()) rpc.notify("workspace/didChangeConfiguration", buildJsonObject { put("settings", settings) })
    }

    suspend fun request(method: String, params: JsonElement?, timeoutMillis: Long): JsonElement =
        (connection ?: throw LanguageFailure.NotStarted("The language server has not started")).request(method, params, timeoutMillis)

    fun notify(method: String, params: JsonElement?) {
        connection?.notify(method, params)
    }

    /** `shutdown`, then `exit`, then the process's own stop (§5.2); never waits past its deadlines. */
    suspend fun stop() {
        if (!stopping.compareAndSet(false, true)) return
        val rpc = connection
        val running = program
        if (rpc != null && !rpc.closed.isCompleted) {
            withTimeoutOrNull(SHUTDOWN_MILLIS) { runCatching { rpc.request("shutdown", null, SHUTDOWN_MILLIS) } }
            rpc.notify("exit", null)
        }
        running?.stop { rpc?.close() }
        documents.clear()
    }

    /** At once: iCode is going and nothing may outlive it. */
    fun kill() {
        stopping.set(true)
        program?.kill()
    }

    private val handler = object : JsonRpcConnection.Handler {
        override suspend fun request(method: String, params: JsonElement?): JsonElement? = when (method) {
            "workspace/configuration" -> LspMessages.configuration(settings, params)
            "workspace/workspaceFolders" -> buildJsonArray {
                uris.uriOf(workspace)?.let { uri -> add(buildJsonObject { put("uri", uri); put("name", workspace.substringAfterLast('/')) }) }
            }
            "client/registerCapability" -> JsonNull.also { register(params, add = true) }
            "client/unregisterCapability" -> JsonNull.also { register(params, add = false) }
            "window/workDoneProgress/create" -> JsonNull
            "window/showMessageRequest" -> JsonNull.also { message(params) }
            "workspace/diagnostic/refresh" -> JsonNull.also { client.refreshDiagnostics(this@LanguageServerSession) }
            "workspace/applyEdit" -> buildJsonObject {
                val edit = (params as? JsonObject)?.get("edit") as? JsonObject
                put("applied", edit != null && client.applyEdit(this@LanguageServerSession, edit))
            }
            else -> throw JsonRpcConnection.JsonRpcException(JsonRpcConnection.METHOD_NOT_FOUND, "iCode does not handle $method")
        }

        override fun notification(method: String, params: JsonElement?) {
            val session = this@LanguageServerSession
            when (method) {
                "textDocument/publishDiagnostics" -> (params as? JsonObject)?.let { client.diagnostics(session, it) }
                "window/logMessage" -> LanguageJson.string(params, "message")?.let { client.log(session, it) }
                "window/showMessage" -> message(params)
                "\$/progress" -> client.progress(session, progress.take(params))
            }
        }
    }

    private fun message(params: JsonElement?) {
        val type = ((params as? JsonObject)?.get("type") as? JsonPrimitive)?.intOrNull ?: return
        LanguageJson.string(params, "message")?.let { client.message(this, type, it) }
    }

    private fun register(params: JsonElement?, add: Boolean) {
        val key = if (add) "registrations" else "unregisterations"
        val entries = (params as? JsonObject)?.get(key) as? JsonArray ?: return
        var next = capabilities
        entries.forEach { entry ->
            val method = LanguageJson.string(entry, "method") ?: return@forEach
            next = if (add) next.withRegistration(method, (entry as? JsonObject)?.get("registerOptions") as? JsonObject ?: JsonObject(emptyMap()))
            else next.withoutRegistration(method)
        }
        capabilities = next
        documents.capabilities = next
        client.capabilitiesChanged(this)
    }

    companion object {
        /** The handshake's bound: JVM servers (kotlin-lsp, jdtls) index under PRoot for minutes before they answer. */
        const val INITIALIZE_MILLIS = 300_000L
        const val SHUTDOWN_MILLIS = 3_000L
    }
}
