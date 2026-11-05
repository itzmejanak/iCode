package dev.icode.mobile.data.assistant.mcp

import android.content.Context
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTools
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpLog
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.NoticeTone
import io.modelcontextprotocol.kotlin.sdk.types.ElicitRequest
import io.modelcontextprotocol.kotlin.sdk.types.ElicitResult
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The assistant's MCP servers (docs/mcp-plan.md §5, §7.3): the ones the user added and the open
 * workspace's, each connected when it is wanted — saved, switched on, the Assistant page opened, a
 * turn starting — and stopped when it idles. What they are and how they are goes to the snapshot as it
 * changes; a turn is offered their tools ([offer]); their questions go to the user ([McpQuestions]).
 */
internal class McpServers(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val store: McpServerStore,
    private val local: McpLocal,
    private val remote: McpRemote,
    private val workspaces: McpWorkspaceServers,
    private val signIn: McpSignIn,
    private val registry: McpRegistry,
    /** The Linux runtime is installed: local servers can start. */
    private val runtimeInstalled: () -> Boolean,
    /** The open workspace's location; null on the home screen. */
    private val activeRoot: () -> String?,
) : McpConnection.Events {
    private val lock = Any()
    private val connections = HashMap<String, McpConnection>()
    private val starting = HashMap<String, Deferred<McpConnection?>>()
    private val states = HashMap<String, McpServerState>()
    private val questions = McpQuestions { question -> stateHolder.update { it.copy(mcp = it.mcp.copy(question = question)) } }
    private var idling: Job? = null
    private val intents = Channel<McpIntent>(Channel.UNLIMITED)

    /** The servers shown; then the intents, in the order they were made. */
    fun start() {
        publish()
        scope.launch { for (intent in intents) apply(intent) }
    }

    /** [intent] in its turn; an answer to a server's question at once, as a call waits on it. */
    fun handle(intent: McpIntent) {
        if (intent is McpIntent.Answer) questions.answer(intent.answer) else intents.trySend(intent)
    }

    /** The servers a chat in [workspace] has: the user's, then that workspace's `.mcp.json`'s. */
    private fun servers(workspace: String?): List<McpServer> {
        val own = store.servers()
        val declared = workspace?.let { location -> workspaces.of(location, own.map { it.key }) }.orEmpty()
        return own + declared
    }

    private fun server(id: String): McpServer? = servers(activeRoot()).firstOrNull { it.id == id }

    /** What reaches the network on the user's behalf runs on its own, so the next intent never waits on it. */
    private suspend fun apply(intent: McpIntent) {
        when (intent) {
            McpIntent.Connect -> { publish(); servers(activeRoot()).filter { it.enabled }.forEach(::ensure) }
            is McpIntent.SaveServer -> save(intent.server)
            is McpIntent.ImportServers -> import(intent.json, intent.single)
            is McpIntent.RemoveServer -> { stop(intent.id); store.remove(intent.id); forget(intent.id) }
            is McpIntent.SetServerEnabled -> server(intent.id)?.let { save(it.copy(enabled = intent.enabled)) }
            is McpIntent.SetToolChoice -> server(intent.id)?.let { store.save(it.copy(tools = it.tools + (intent.tool to intent.choice))); publish() }
            is McpIntent.RestartServer -> server(intent.id)?.let { stop(it.id); ensure(it) }
            is McpIntent.ReadLog -> stateHolder.update { it.copy(mcp = it.mcp.copy(log = intent.id?.let { id -> McpLog(id, local.tail(id)) })) }
            is McpIntent.SignIn -> server(intent.id)?.let { server -> scope.launch { signingIn(server) { stateHolder.emit(ICodeEffect.OpenExternalUrl(signIn.begin(server))) } } }
            is McpIntent.SignOut -> server(intent.id)?.let { store.saveSignIn(it.id, null); stop(it.id); ensure(it) }
            is McpIntent.SignedIn -> scope.launch { signingIn(null) { server(signIn.finish(intent.uri))?.let { stop(it.id); ensure(it) } } }
            is McpIntent.Answer -> questions.answer(intent.answer)
            is McpIntent.Trust -> server(intent.id)?.let { trust(it, intent.trusted) }
            is McpIntent.SearchRegistry -> scope.launch { search(intent.query) }
            is McpIntent.UsePrompt -> scope.launch { draft(intent.id) { McpDrafts.prompt(it.prompt(intent.name, intent.arguments)) } }
            is McpIntent.UseResource -> scope.launch { draft(intent.id) { McpDrafts.resource(intent.uri, it.read(intent.uri)) } }
            McpIntent.DraftTaken -> stateHolder.update { it.copy(mcp = it.mcp.copy(draft = null)) }
        }
    }

    /** What a turn in [workspace] is offered: each server that is on, connected first, its tools in what [room] is left. */
    suspend fun offer(workspace: String, step: AssistantStep, taken: Set<String>, room: Int, offers: (AssistantTool) -> Boolean): AssistantTools.Offered {
        idling?.cancel()
        // A workspace's server not trusted yet waits for its page; it is not missed.
        val wanted = servers(workspace).filter { it.enabled && (it.workspace == null || workspaces.trusted(it)) }
        val waiting = synchronized(lock) { wanted.filter { it.id !in connections } }
        if (waiting.isNotEmpty()) step.progress("Starting ${waiting.joinToString { it.name }}…")
        val started = wanted.map { it to ensure(it) }
        val connected = started.mapNotNull { (server, connection) -> connection.await()?.let { server to it } }
        val missed = waiting.filter { server -> connected.none { it.first.id == server.id } }
        if (missed.isNotEmpty()) step.progress("Going on without ${missed.joinToString { it.name }}")
        return McpTool.offer(connected, step, taken, room, questions::asking, offers).also { offered ->
            if (offered.leftOut > 0) step.progress("${offered.leftOut} MCP tools left out: over ${McpRules.MAX_TOOLS} a request")
        }
    }

    /** The turn ended: a question still waiting goes; local servers stop once idle [IDLE_MILLIS]. */
    fun idle() {
        questions.cancel()
        idling?.cancel()
        idling = scope.launch {
            delay(IDLE_MILLIS)
            synchronized(lock) { connections.values.filter { it.server.transport is McpTransport.Local } }.forEach { stop(it.server.id) }
        }
    }

    /** [server]'s connection, started if it is not: null when it cannot be. */
    private fun ensure(server: McpServer): Deferred<McpConnection?> = synchronized(lock) {
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
        state(server.id, McpServerState(McpStatus.Starting))
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
            val words = McpFailures.of(failure, server.name, (server.transport as? McpTransport.Remote)?.url, local.tail(server.id))
            if (words == McpFailures.SIGN_IN && !renewed && signIn.renew(server)) return connect(server, renewed = true)
            done(server.id, null, McpServerState(if (words == McpFailures.SIGN_IN) McpStatus.SignIn else McpStatus.Failed, words))
        }
    }

    private fun done(id: String, connection: McpConnection?, state: McpServerState): McpConnection? {
        synchronized(lock) {
            starting.remove(id)
            if (connection != null) connections[id] = connection
        }
        state(id, state)
        return connection
    }

    private suspend fun stop(id: String) {
        val connection = synchronized(lock) { starting.remove(id)?.cancel(); connections.remove(id) }
        connection?.close()
        state(id, McpServerState(McpStatus.Off))
    }

    /** A workspace's server keeps only the user's choices and values: its file is checked by [McpWorkspaceServers]. */
    private suspend fun save(server: McpServer) {
        val others = servers(activeRoot()).filter { it.id != server.id }
        if (server.workspace == null) McpRules.problem(server, others)?.let { return say(it, server) }
        val kept = store.save(server)
        stop(kept.id)
        if (kept.enabled) ensure(kept) else publish()
    }

    /** Each server a paste names, added under a name of its own; one still missing a value is added off, its page saying which. */
    private suspend fun import(json: String, single: String) {
        val imported = runCatching { McpRules.import(json, single) }.getOrElse { return say(it.message ?: "This is not an MCP configuration", null) }
        val names = store.servers().map { it.name.lowercase() }.toMutableSet()
        imported.servers.forEach { found ->
            val name = generateSequence(found.name) { "${found.name} ${names.count { n -> n.startsWith(found.name.lowercase()) } + 1}" }.first { it.lowercase() !in names }
            names += name.lowercase()
            val server = found.copy(name = name)
            val missing = McpRules.problem(server, emptyList())
            store.save(server.copy(enabled = server.enabled && missing == null)).also { kept -> if (kept.enabled) ensure(kept) else missing?.let { state(kept.id, McpServerState(McpStatus.Failed, it)) } }
        }
        if (imported.ignored.isNotEmpty()) say("Not read: ${imported.ignored.joinToString()}", null, NoticeTone.Info)
        publish()
    }

    /** A sign-in's step, its failure said in its words, or the network's. */
    private suspend fun signingIn(server: McpServer?, step: suspend () -> Unit) {
        try {
            step()
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: McpSignIn.Refused) {
            say(refused.message ?: "The sign-in failed", server)
        } catch (failure: Exception) {
            say(McpFailures.of(failure, server?.name ?: "The sign-in", (server?.transport as? McpTransport.Remote)?.url), server)
        }
    }

    private suspend fun trust(server: McpServer, trusted: Boolean) {
        store.trust(server.id, if (trusted) workspaces.fingerprint(server) else null)
        stop(server.id)
        if (trusted && server.enabled) ensure(server)
    }

    private suspend fun search(query: String?) {
        if (query == null) return stateHolder.update { it.copy(mcp = it.mcp.copy(registry = null)) }
        stateHolder.update { it.copy(mcp = it.mcp.copy(registry = registry.searching(query))) }
        val found = registry.search(query)
        stateHolder.update { if (it.mcp.registry?.query == query) it.copy(mcp = it.mcp.copy(registry = found)) else it }
    }

    /** A prompt's or a resource's text, from [id]'s server, into the composer's draft. */
    private suspend fun draft(id: String, read: suspend (McpConnection) -> String) {
        val server = server(id) ?: return
        val connection = ensure(server).await() ?: return say("${server.name} is not connected", server)
        runCatching { read(connection) }
            .onSuccess { text -> stateHolder.update { it.copy(mcp = it.mcp.copy(draft = text)) } }
            .onFailure { failure -> if (failure is CancellationException) throw failure else say(McpFailures.of(failure, server.name), server) }
    }

    override fun toolsChanged(connection: McpConnection) = state(connection.server.id, McpTool.state(connection))

    override fun ended(connection: McpConnection, failure: String) {
        val id = connection.server.id
        synchronized(lock) { if (connections[id] === connection) connections.remove(id) else return }
        state(id, McpServerState(McpStatus.Failed, failure))
    }

    override suspend fun ask(connection: McpConnection, request: ElicitRequest): ElicitResult = questions.ask(connection.server.name, request)

    private fun state(id: String, state: McpServerState) {
        synchronized(lock) { states[id] = state }
        publish()
    }

    private fun forget(id: String) {
        synchronized(lock) { states.remove(id) }
        publish()
    }

    private fun publish() {
        val servers = servers(activeRoot())
        val shown = synchronized(lock) { servers.associate { it.id to (states[it.id] ?: McpServerState()) } }
        stateHolder.update { it.copy(mcp = it.mcp.copy(servers = servers, states = shown)) }
    }

    private suspend fun say(title: String, server: McpServer?, tone: NoticeTone = NoticeTone.HeadsUp) {
        stateHolder.emit(ICodeEffect.Notice(AppNotice.Message(tone, title, server?.name.orEmpty())))
        publish()
    }

    companion object {
        /** Local servers stop this long after the last turn ended (§7.3). */
        private const val IDLE_MILLIS = 10 * 60_000L

        /** The servers, their secrets in their own Keystore key, their local ones in [execution]'s runtime. */
        fun create(
            context: Context,
            stateHolder: ICodeStateHolder,
            scope: CoroutineScope,
            execution: RuntimeExecution,
            runtimeInstalled: () -> Boolean,
            activeRoot: () -> String?,
        ): McpServers {
            val store = McpServerStore(context)
            val remote = McpRemote()
            val environment = execution.environment
            return McpServers(
                stateHolder, scope, store, McpLocal(execution.layout, environment), remote,
                McpWorkspaceServers(store, execution.layout.paths::guestOf) { if (runtimeInstalled()) environment.of(GuestContext.Command).variables else emptyMap() },
                McpSignIn(store, remote.http, McpSignIn.redirect(BuildConfig.APPLICATION_ID), BuildConfig.ICODE_WEB_URL),
                McpRegistry(remote.http), runtimeInstalled, activeRoot,
            )
        }
    }
}
