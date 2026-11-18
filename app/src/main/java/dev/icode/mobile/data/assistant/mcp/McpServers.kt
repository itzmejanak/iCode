package dev.icode.mobile.data.assistant.mcp

import android.content.Context
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantTools
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpIntent
import dev.icode.mobile.domain.McpLog
import dev.icode.mobile.domain.McpQuestion
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.notices.McpNotices
import dev.icode.mobile.domain.notices.Notice
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The assistant's MCP servers (docs/assistant-and-mcp.md): the ones the user added and the open
 * workspace's, each connected when it is wanted — saved, switched on, the Assistant page opened, a
 * turn starting — and stopped when it idles. What they are and how they are goes to the snapshot as it
 * changes; a turn is offered their tools ([offer]); their questions go to the user ([McpQuestions]).
 * The pages' intents and the agent's tools change them through the same operations ([save], [import],
 * [remove], [enable], [choose]: docs/assistant-and-mcp.md), one at a time; a refusal is the
 * user's notice, or the model's words.
 */
internal class McpServers(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val store: McpServerStore,
    /** The Linux runtime local servers start in; null where there is none (the JVM's tests). */
    private val local: McpLocal?,
    remote: McpRemote,
    private val workspaces: McpWorkspaceServers,
    private val signIn: McpSignIn,
    private val registry: McpRegistry,
    /** The Linux runtime is installed: local servers can start. */
    runtimeInstalled: () -> Boolean,
    /** The open workspace's location; null on the home screen. */
    private val activeRoot: () -> String?,
) {
    private val questions = McpQuestions { question -> stateHolder.update { it.copy(mcp = it.mcp.copy(question = question)) } }

    /** A coding agent's question, on the servers' card, after any of theirs asked before it (docs/assistant-and-mcp.md). */
    suspend fun ask(make: (Long) -> McpQuestion): McpAnswer = questions.ask(make)
    private val connections = McpConnections(scope, store, local, remote, workspaces, signIn, questions, runtimeInstalled) { publish() }
    private val intents = Channel<McpIntent>(Channel.UNLIMITED)
    private val changes = Mutex()

    /** The servers shown; then the intents, in the order they were made. */
    fun start() {
        publish()
        scope.launch { for (intent in intents) apply(intent) }
    }

    /** [intent] in its turn; an answer to a server's question at once, as a call waits on it. */
    fun handle(intent: McpIntent) {
        if (intent is McpIntent.Answer) questions.answer(intent.answer) else intents.trySend(intent)
    }

    /** The servers a chat in [workspace] has, and how each is now. */
    fun listed(workspace: String?): List<Pair<McpServer, McpServerState>> = servers(workspace).map { it to connections.state(it.id) }

    /** The servers a chat in [workspace] has: the user's, then that workspace's `.mcp.json`'s. */
    private fun servers(workspace: String?): List<McpServer> {
        val own = store.servers()
        val declared = workspace?.let { location -> workspaces.of(location, own.map { it.key }) }.orEmpty()
        return own + declared
    }

    private fun server(id: String, workspace: String? = activeRoot()): McpServer? = servers(workspace).firstOrNull { it.id == id }

    /** What reaches the network on the user's behalf runs on its own, so the next intent never waits on it. */
    private suspend fun apply(intent: McpIntent) {
        when (intent) {
            McpIntent.Connect -> { publish(); servers(activeRoot()).filter { it.enabled }.forEach(connections::ensure) }
            is McpIntent.SaveServer -> save(activeRoot(), intent.server).said(intent.server)
            is McpIntent.ImportServers -> import(intent.json, intent.single)
                .onSuccess { if (it.ignored.isNotEmpty()) told(McpNotices.notImported(it.ignored)) }.said(null)
            is McpIntent.RemoveServer -> remove(activeRoot(), intent.id).said(null)
            is McpIntent.SetServerEnabled -> enable(activeRoot(), intent.id, intent.enabled).said(null)
            is McpIntent.SetToolChoice -> choose(activeRoot(), intent.id, intent.tool, intent.choice).said(null)
            is McpIntent.RestartServer -> server(intent.id)?.let { restart(it) }
            is McpIntent.Edit -> stateHolder.update {
                it.copy(mcp = it.mcp.copy(editing = intent.server, log = intent.server?.id?.takeIf(String::isNotEmpty)?.let { id -> McpLog(id, local?.tail(id).orEmpty()) }))
            }
            is McpIntent.SignIn -> server(intent.id)?.let { server -> scope.launch { signingIn(server) { stateHolder.emit(ICodeEffect.OpenExternalUrl(signIn.begin(server))) } } }
            is McpIntent.SignOut -> server(intent.id)?.let { store.saveSignIn(it.id, null); restart(it) }
            is McpIntent.SignedIn -> scope.launch { signingIn(null) { server(signIn.finish(intent.uri))?.let { restart(it) } } }
            is McpIntent.Answer -> questions.answer(intent.answer)
            is McpIntent.Trust -> server(intent.id)?.let { trust(it, intent.trusted) }
            is McpIntent.SearchRegistry -> scope.launch { search(intent.query) }
            is McpIntent.UsePrompt -> scope.launch { draft(intent.id) { McpDrafts.prompt(it.prompt(intent.name, intent.arguments)) } }
            is McpIntent.UseResource -> scope.launch { draft(intent.id) { McpDrafts.resource(intent.uri, it.read(intent.uri)) } }
            McpIntent.DraftTaken -> stateHolder.update { it.copy(mcp = it.mcp.copy(draft = null)) }
        }
    }

    /** What a turn in [workspace] is offered: each server that is on, connected first, its tools in what [room] is left. */
    suspend fun offer(workspace: String?, step: AssistantStep, taken: Set<String>, room: Int, offers: (AssistantTool) -> Boolean): AssistantTools.Offered {
        connections.wanted()
        // A workspace's server not trusted yet waits for its page; it is not missed.
        val wanted = servers(workspace).filter { it.enabled && (it.workspace == null || workspaces.trusted(it)) }
        val waiting = wanted.filterNot { connections.connected(it.id) }
        if (waiting.isNotEmpty()) step.progress("Starting ${waiting.joinToString { it.name }}…")
        val started = wanted.map { it to connections.ensure(it) }
        val connected = started.mapNotNull { (server, connection) -> connection.await()?.let { server to it } }
        val missed = waiting.filter { server -> connected.none { it.first.id == server.id } }
        if (missed.isNotEmpty()) step.progress("Going on without ${missed.joinToString { it.name }}")
        return McpTool.offer(connected, step, taken, room, questions::asking, offers).also { offered ->
            stateHolder.update { it.copy(mcp = it.mcp.copy(leftOut = offered.leftOut)) }
            if (offered.leftOut > 0) step.progress("${offered.leftOut} MCP tools left out: over ${McpRules.MAX_TOOLS} a request")
        }
    }

    /** The turn ended: a question still waiting goes, and local servers idle from now. */
    fun idle() {
        questions.cancel()
        connections.idle()
    }

    /**
     * Adds [server] ([McpServer.id] "") or replaces it, among [workspace]'s, and starts it again if it is
     * on. A workspace's server keeps only the user's choices and values: its file is checked by
     * [McpWorkspaceServers].
     */
    suspend fun save(workspace: String?, server: McpServer): Result<McpServer> = changing {
        if (server.workspace == null) McpRules.problem(server, servers(workspace).filter { it.id != server.id })?.let(::refuse)
        keep(server)
    }

    /** Each server a paste names, added under a name of its own; one still missing a value is added off, its state saying which. */
    suspend fun import(json: String, single: String): Result<McpRules.Imported> = changing {
        val imported = McpRules.import(json, single)
        val names = store.servers().map { it.name.lowercase() }.toMutableSet()
        val kept = imported.servers.map { found ->
            val name = generateSequence(found.name) { "${found.name} ${names.count { n -> n.startsWith(found.name.lowercase()) } + 1}" }.first { it.lowercase() !in names }
            names += name.lowercase()
            val server = found.copy(name = name)
            val missing = McpRules.problem(server, emptyList())
            store.save(server.copy(enabled = server.enabled && missing == null)).also { kept ->
                if (kept.enabled) connections.ensure(kept) else missing?.let { connections.set(kept.id, McpServerState(McpStatus.Failed, it)) }
            }
        }
        publish()
        McpRules.Imported(kept, imported.ignored)
    }

    /** Forgets [id], one of the user's servers, with its secrets and its sign-in; a workspace's stays its file's. */
    suspend fun remove(workspace: String?, id: String): Result<McpServer> = changing {
        val server = known(workspace, id)
        if (server.workspace != null) refuse("${server.name} comes from this workspace's .mcp.json: it is removed there")
        connections.stop(id)
        store.remove(id)
        connections.forget(id)
        server
    }

    suspend fun enable(workspace: String?, id: String, enabled: Boolean): Result<McpServer> = changing { keep(known(workspace, id).copy(enabled = enabled)) }

    /** [tool]'s [choice] on [id]: it applies from the next turn, so the server keeps running. */
    suspend fun choose(workspace: String?, id: String, tool: String, choice: McpToolChoice): Result<McpServer> = changing {
        val server = known(workspace, id)
        store.save(server.copy(tools = server.tools + (tool to choice))).also { publish() }
    }

    /** One change at a time, the pages' and the agent's; what refuses it, in its words. */
    private suspend fun <T> changing(change: suspend () -> T): Result<T> = changes.withLock {
        try {
            Result.success(change())
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: Exception) {
            Result.failure(refused)
        }
    }

    private fun known(workspace: String?, id: String): McpServer = server(id, workspace) ?: refuse("There is no MCP server $id")

    private fun refuse(words: String): Nothing = throw IllegalArgumentException(words)

    private suspend fun keep(server: McpServer): McpServer = store.save(server).also { kept ->
        connections.stop(kept.id)
        if (kept.enabled) connections.ensure(kept) else publish()
    }

    private suspend fun restart(server: McpServer) {
        connections.stop(server.id)
        connections.ensure(server)
    }

    /** A page's change refused: the user's notice. */
    private suspend fun Result<*>.said(server: McpServer?) {
        exceptionOrNull()?.let { told(McpNotices.notChanged(stateHolder.current, server?.name, it.message)) }
    }

    /** A sign-in's step, its failure said in its words, or the network's. */
    private suspend fun signingIn(server: McpServer?, step: suspend () -> Unit) {
        try {
            step()
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (refused: McpSignIn.Refused) {
            told(McpNotices.notSignedIn(stateHolder.current, server?.name, refused.message ?: "The server refused it."))
        } catch (failure: Exception) {
            told(McpNotices.notSignedIn(stateHolder.current, server?.name, McpFailures.of(failure, server?.name ?: "The sign-in", (server?.transport as? McpTransport.Remote)?.url)))
        }
    }

    private suspend fun trust(server: McpServer, trusted: Boolean) {
        store.trust(server.id, if (trusted) workspaces.fingerprint(server) else null)
        connections.stop(server.id)
        if (trusted && server.enabled) connections.ensure(server)
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
        val connection = connections.ensure(server).await() ?: return told(McpNotices.notConnected(stateHolder.current, server.name))
        runCatching { read(connection) }
            .onSuccess { text -> stateHolder.update { it.copy(mcp = it.mcp.copy(draft = text)) } }
            .onFailure { failure -> if (failure is CancellationException) throw failure else told(McpNotices.notRead(stateHolder.current, server.name, McpFailures.of(failure, server.name))) }
    }

    private fun publish() {
        val servers = servers(activeRoot())
        val shown = servers.associate { it.id to connections.state(it.id) }
        val signedIn = servers.filter { it.transport is McpTransport.Remote && store.signedIn(it.id) }.map { it.id }.toSet()
        stateHolder.update { it.copy(mcp = it.mcp.copy(servers = servers, states = shown, signedIn = signedIn)) }
    }

    private suspend fun told(notice: Notice) {
        stateHolder.tell(notice)
        publish()
    }

    companion object {
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
