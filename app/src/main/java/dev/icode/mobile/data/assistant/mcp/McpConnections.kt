package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpTransport
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The MCP servers' connections (docs/assistant-and-mcp.md): each started when it is wanted — once, all who
 * want it waiting on the same start — and stopped when asked, or when local ones idle; how each is,
 * for [McpServers] to show ([changed]). What a connected server does on its own — its tools changed,
 * its end, a question for the user — is heard here.
 */
internal class McpConnections(
    private val scope: CoroutineScope,
    private val store: McpServerStore,
    /** The Linux runtime local servers start in; null where there is none (the JVM's tests). */
    private val local: McpLocal?,
    private val remote: McpRemote,
    private val workspaces: McpWorkspaceServers,
    private val signIn: McpSignIn,
    private val questions: McpQuestions,
    /** The Linux runtime is installed: local servers can start. */
    private val runtimeInstalled: () -> Boolean,
    /** A server's state changed. */
    private val changed: () -> Unit,
) : McpConnection.Events {
    private val lock = Any()
    private val connections = HashMap<String, McpConnection>()
    private val starting = HashMap<String, Deferred<McpConnection?>>()
    private val states = HashMap<String, McpServerState>()
    private var idling: Job? = null

    fun state(id: String): McpServerState = synchronized(lock) { states[id] ?: McpServerState() }

    fun connected(id: String): Boolean = synchronized(lock) { id in connections }

    /** [server]'s connection, started if it is not: null when it cannot be. */
    fun ensure(server: McpServer): Deferred<McpConnection?> = synchronized(lock) {
        connections[server.id]?.let { return CompletableDeferred(it) }
        starting[server.id] ?: scope.async { connect(server) }.also { starting[server.id] = it }
    }

    /** Connects [server]; a 401 renews its sign-in once, if it has one, before it asks for one. */
    private suspend fun connect(server: McpServer, renewed: Boolean = false): McpConnection? {
        fun failed(words: String?) = done(server.id, null, McpServerState(McpStatus.Failed, words))
        if (!workspaces.trusted(server)) return done(server.id, null, McpServerState(McpStatus.Trust, "Trust it to start it: it comes from this workspace's .mcp.json"))
        if (server.transport is McpTransport.Local && !runtimeInstalled()) return failed("Needs the Linux runtime")
        val (ready, values) = try {
            workspaces.resolve(server, store.reveal(server))
        } catch (unset: IllegalArgumentException) {
            return failed(unset.message)
        }
        values.firstOrNull { it.value == null }?.let { return failed("Enter a value for ${it.name}") }
        set(server.id, McpServerState(McpStatus.Starting))
        val connection = McpConnection(ready, scope, local, remote, values, { signIn.authorization(server) }, this)
        return try {
            connection.connect()
            done(server.id, connection, McpTool.state(connection))
        } catch (stopped: CancellationException) {
            connection.close()
            synchronized(lock) { starting.remove(server.id) }
            throw stopped
        } catch (failure: Exception) {
            connection.close()
            val words = McpFailures.of(failure, server.name, (server.transport as? McpTransport.Remote)?.url, local?.tail(server.id).orEmpty())
            if (words == McpFailures.SIGN_IN && !renewed && signIn.renew(server)) return connect(server, renewed = true)
            done(server.id, null, McpServerState(if (words == McpFailures.SIGN_IN) McpStatus.SignIn else McpStatus.Failed, words))
        }
    }

    private fun done(id: String, connection: McpConnection?, state: McpServerState): McpConnection? {
        synchronized(lock) {
            starting.remove(id)
            if (connection != null) connections[id] = connection
        }
        set(id, state)
        return connection
    }

    suspend fun stop(id: String) {
        val connection = synchronized(lock) { starting.remove(id)?.cancel(); connections.remove(id) }
        connection?.close()
        set(id, McpServerState(McpStatus.Off))
    }

    /** [id]'s state as its owner says it: a server added off, and the value it misses. */
    fun set(id: String, state: McpServerState) {
        synchronized(lock) { states[id] = state }
        changed()
    }

    /** A server removed: nothing of it is kept. */
    fun forget(id: String) {
        synchronized(lock) { states.remove(id) }
        changed()
    }

    /** A turn started: nothing stops for idling. */
    fun wanted() {
        idling?.cancel()
    }

    /** The turn ended: local servers stop once idle [IDLE_MILLIS]. */
    fun idle() {
        idling?.cancel()
        idling = scope.launch {
            delay(IDLE_MILLIS)
            synchronized(lock) { connections.values.filter { it.server.transport is McpTransport.Local } }.forEach { stop(it.server.id) }
        }
    }

    override fun toolsChanged(connection: McpConnection) = set(connection.server.id, McpTool.state(connection))

    override fun ended(connection: McpConnection, failure: String) {
        val id = connection.server.id
        synchronized(lock) { if (connections[id] === connection) connections.remove(id) else return }
        set(id, McpServerState(McpStatus.Failed, failure))
    }

    override suspend fun ask(connection: McpConnection, request: ElicitRequest): ElicitResult = questions.ask(connection.server.name, request)

    private companion object {
        /** Local servers stop this long after the last turn ended. */
        const val IDLE_MILLIS = 10 * 60_000L
    }
}
