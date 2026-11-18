package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.AuthMethod
import com.agentclientprotocol.model.AuthMethodId
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.rpc.JsonRpcErrorCode
import com.agentclientprotocol.transport.StdioTransport
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.domain.AgentKey
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.InstalledAgent
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered

/**
 * The agents running for the chats (docs/assistant-and-mcp.md): one process per agent, started in
 * the runtime as a chat it answers opens or sends, its sessions — one per chat — sharing its
 * connection, and stopped once no chat has used it for [idleMillis]. Every session starts in the
 * agent's asking values ([AcpSettings.startAsking]). Its standard output is the protocol and is never
 * logged; its standard error goes to its own log.
 */
internal class AcpAgents(
    private val scope: CoroutineScope,
    private val programs: StdioPrograms,
    /** The login's variables, which every agent starts with. */
    private val login: suspend () -> Map<String, String>,
    private val installed: () -> Map<String, InstalledAgent>,
    /** The keys entered for an agent, by variable: given to its process only. */
    private val keys: (String) -> Map<String, String>,
    /** iCode's version, as the agent is told who connects. */
    private val version: String,
    private val idleMillis: Long = IDLE_MILLIS,
    /** What a chat's session offers changed: its commands, its settings. */
    private val changed: (agent: String, chat: Pair<String?, String>) -> Unit = { _, _ -> },
    /** An agent stopped: its sessions are gone. */
    private val stopped: (agent: String) -> Unit = {},
) {
    /** An agent's process and its connection: what it said of itself, and a session per chat. */
    class Running(val id: String, val program: StdioProgram, val client: Client, val methods: List<AgentSignIn>) {
        val sessions = ConcurrentHashMap<Pair<String?, String>, Held>()

        /** Held while a session is made or handed to a chat: one chat never gets two. */
        val opening = Mutex()
        var users = 0
        var stopping: Job? = null
    }

    /** A chat's session, and whether a message has gone in it: the first carries the chat so far. */
    class Held(val session: ClientSession) {
        @Volatile var prompted = false
        val operations: ChatOperations get() = session.operations as ChatOperations
    }

    /** Held while an agent starts or stops: [running] changes only under it. */
    private val lock = Mutex()
    private val running = ConcurrentHashMap<String, Running>()

    /** [block] with [id]'s connection, started if it is not running; it idles from the last use on. */
    suspend fun <T> use(id: String, block: suspend (Running) -> T): T {
        val agent = lock.withLock {
            // One that ended by itself has lost its sessions with it.
            running[id]?.takeIf { it.program.ended.isActive } ?: start(id).also { if (running.put(id, it) != null) stopped(id) }
        }.also { it.users++; it.stopping?.cancel() }
        try {
            return block(agent)
        } finally {
            lock.withLock {
                agent.users--
                if (agent.users == 0) agent.stopping = scope.launch { delay(idleMillis); stop(id, agent) }
            }
        }
    }

    /**
     * [chat]'s session with [agent] for a message, made in [cwd] — the chat's place in the runtime — when it
     * has none; a new chat takes the one made for it while it was empty. True when no message has gone in
     * it yet. Throws [SignInNeeded] when the agent asks to be signed in first.
     */
    suspend fun session(agent: Running, chat: Pair<String?, String>, cwd: String): Pair<ClientSession, Boolean> {
        val held = prepare(agent, chat, cwd)
        val first = !held.prompted
        held.prompted = true
        return held.session to first
    }

    /**
     * [chat]'s session with [agent], made in [cwd] if it has none, for its settings and commands to show
     * before a message goes: [NEW_CHAT] names a chat not started yet. Throws [SignInNeeded] when the agent
     * asks to be signed in first.
     */
    suspend fun prepare(agent: Running, chat: Pair<String?, String>, cwd: String): Held = agent.opening.withLock {
        agent.sessions[chat] ?: adopted(agent, chat) ?: open(agent, chat, cwd)
    }

    /** The session made for a new chat in [chat]'s workspace, now [chat]'s: no message has gone in it, so the chat so far goes with its first. */
    private fun adopted(agent: Running, chat: Pair<String?, String>): Held? {
        if (chat.second == NEW_CHAT) return null
        val held = agent.sessions.remove(chat.first to NEW_CHAT) ?: return null
        held.operations.chat = chat
        agent.sessions[chat] = held
        return held
    }

    private suspend fun open(agent: Running, chat: Pair<String?, String>, cwd: String): Held {
        val operations = ChatOperations(agent.id, chat) { changed(agent.id, it) }
        val session = try {
            agent.client.newSession(SessionCreationParameters(cwd, emptyList())) { _, _ -> operations }
        } catch (refused: JsonRpcException) {
            if (refused.code == JsonRpcErrorCode.AUTH_REQUIRED.code) throw SignInNeeded(agent.methods)
            throw AssistantTurnException("The agent refused the chat: ${refused.message}", refused)
        }
        AcpSettings.startAsking(agent.id, session)
        return Held(session).also { agent.sessions[chat] = it }
    }

    /** [chat]'s session with [id], while its agent runs. */
    fun held(id: String, chat: Pair<String?, String>): Held? = running[id]?.sessions?.get(chat)

    /** [chat]'s sessions forgotten: its next message starts each agent afresh, from the chat as it stands. */
    fun forget(chat: Pair<String?, String>) = running.values.forEach { it.sessions.remove(chat) }

    /** [id] signs in the [method] way, by itself. */
    suspend fun authenticate(id: String, method: String) = use(id) { it.client.authenticate(AuthMethodId(method)) }

    /** [id] stopped now, if it runs: keys it is given change only as it starts again. */
    suspend fun restart(id: String) {
        lock.withLock { running.remove(id) }?.program?.stop() ?: return
        stopped(id)
    }

    private suspend fun start(id: String): Running {
        val agent = installed()[id] ?: throw AssistantTurnException("This agent is not installed: install it in Settings › Assistant › Coding Agents")
        val program = programs.start(id, agent.command, login() + agent.env + keys(id), RuntimeLayout.GUEST_HOME, scope)
        val transport = StdioTransport(scope, Dispatchers.IO, program.output.asSource().buffered(), program.input.asSink().buffered(), name = "agent-$id")
        val protocol = Protocol(scope, transport)
        val client = Client(protocol)
        protocol.start()
        val info = try {
            withTimeout(START_MILLIS) { client.initialize(ClientInfo(implementation = Implementation("icode", version, "iCode"))) }
        } catch (failed: Exception) {
            program.stop()
            val said = programs.tail(id, LOG_LINES).lastOrNull { it.isNotBlank() }
            throw AssistantTurnException("The agent did not start: ${said ?: failed.message ?: failed.javaClass.simpleName}", failed)
        }
        return Running(id, program, client, info.authMethods.mapNotNull(::signInOf))
    }

    private suspend fun stop(id: String, agent: Running) {
        lock.withLock {
            if (agent.users > 0 || running[id] !== agent) return
            running.remove(id)
        }
        agent.program.stop()
        stopped(id)
    }

    /** One that asks to be signed in first, and the ways it offers. */
    class SignInNeeded(val methods: List<AgentSignIn>) : Exception("The agent asks to be signed in")

    companion object {
        /** The chat a new chat's session is kept for until its first message names it. */
        const val NEW_CHAT = ""

        /** An agent no chat has used for this long stops (owner's choice, 2026-11-17). */
        const val IDLE_MILLIS = 10 * 60 * 1000L

        private const val LOG_LINES = 20

        /** How long an agent may take to say it is ready: a first start under PRoot is slow. */
        private const val START_MILLIS = 120_000L

        /** A way the protocol describes, as iCode offers it; one it does not know is left out. */
        fun signInOf(method: AuthMethod): AgentSignIn? = when (method) {
            is AuthMethod.AgentAuth -> AgentSignInWay.ByAgent
            is AuthMethod.TerminalAuth -> AgentSignInWay.InTerminal(method.args.orEmpty(), method.env.orEmpty())
            is AuthMethod.EnvVarAuth -> AgentSignInWay.Keys(method.vars.map { AgentKey(it.name, it.label, it.secret, it.optional) }, method.link)
            is AuthMethod.UnknownAuthMethod -> null
        }?.let { way -> AgentSignIn(method.id.value, method.name, method.description, way) }
    }
}
