package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestEnvironment
import dev.icode.mobile.data.runtime.GuestStdio
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.StdioProgram
import dev.icode.mobile.data.runtime.StdioPrograms
import dev.icode.mobile.domain.AgentStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * How a coding agent's process is started in the runtime (docs/assistant-and-mcp.md), as a local MCP
 * server's is: the login environment, then the agent's own variables and the keys saved for it; its
 * standard error kept in its log; a link it opens handed to iCode, which shows it rather than opening it.
 */
internal class AgentLauncher(
    private val rootfs: File,
    /** The login environment's variables, read through the user's profile. */
    private val login: suspend () -> Map<String, String>,
    private val stdio: StdioPrograms,
    val commands: AgentCommands = AgentCommands(rootfs),
) {
    constructor(layout: RuntimeLayout, environment: GuestEnvironment) : this(
        layout.rootfs, { environment.of(GuestContext.Command).variables }, GuestStdio(layout, GuestContext.Command, LOGS, tag = "agent"),
    )

    fun start(entry: AgentEntry, variables: Map<String, String>, scope: CoroutineScope, login: Map<String, String>): StdioProgram {
        val argv = commands.server(entry) ?: throw AssistantTurnException("${entry.agent.name} is not installed: install it in Settings › Assistant › Coding Agents")
        return stdio.start(entry.agent.id, argv, environment(entry, variables, login), RuntimeLayout.GUEST_HOME, scope)
    }

    suspend fun login(): Map<String, String> = login.invoke()

    /** What [entry]'s processes run with: the login's variables, the agent's own, its keys, and iCode's link opener first on its path. */
    fun environment(entry: AgentEntry, variables: Map<String, String>, login: Map<String, String>): Map<String, String> {
        installOpener()
        return login + entry.distribution.env + variables + mapOf(
            "BROWSER" to "$OPENERS/xdg-open",
            "PATH" to "$OPENERS:${login["PATH"] ?: RuntimeLayout.GUEST_PATH}",
            OPEN_FILE to linksOf(entry.agent.id),
        )
    }

    fun tail(agent: String, lines: Int = GuestStdio.LOG_LINES): List<String> = stdio.tail(agent, lines)

    /** The links [agent] asked to open since this was last asked, oldest first; they are read once. */
    fun links(agent: String): List<String> {
        val file = File(rootfs, linksOf(agent).removePrefix("/"))
        if (!file.isFile || file.length() == 0L) return emptyList()
        return runCatching { file.readLines().also { file.writeText("") } }.getOrDefault(emptyList())
            .map(String::trim).filter { it.startsWith("https://") || it.startsWith("http://") }
    }

    /** `xdg-open`, as the agents see it: the link written for iCode to show. */
    private fun installOpener() {
        val folder = File(rootfs, OPENERS.removePrefix("/"))
        val opener = File(folder, "xdg-open")
        if (opener.isFile && opener.readText() == OPENER) return
        folder.mkdirs()
        opener.writeText(OPENER)
        opener.setExecutable(true, false)
    }

    private fun linksOf(agent: String) = "$LOGS/$agent.links"

    companion object {
        const val LOGS = "/tmp/icode-agents"
        const val OPEN_FILE = "ICODE_OPEN_FILE"
        private const val OPENERS = "${AgentCommands.AGENTS}/.bin"
        private const val OPENER = "#!/bin/sh\n" +
            "# iCode (docs/assistant-and-mcp.md): a link a coding agent opens is shown in iCode, opened only when the user taps it.\n" +
            "[ -n \"\$$OPEN_FILE\" ] && printf '%s\\n' \"\$1\" >> \"\$$OPEN_FILE\"\n"
    }
}

/**
 * One process per coding agent, its sessions sharing its connection (docs/assistant-and-mcp.md): started
 * when a chat needs it, stopped once it has had nothing to do for [IDLE_MILLIS], as local MCP servers are.
 * A process that ends on its own is said so, with the end of its log.
 */
internal class AcpConnections(
    private val scope: CoroutineScope,
    private val launcher: AgentLauncher,
    /** The variables an agent's process gets beyond the login's: its saved keys. */
    private val variables: (AgentEntry) -> Map<String, String>,
    private val status: (agent: String, AgentStatus) -> Unit,
    /** A link [agent] asked to open. */
    private val link: (agent: String, url: String) -> Unit,
    /** Work in a chat of [agent]: a turn, or a chat being read back, holds its process up. */
    private val busy: (agent: String) -> Boolean,
) {
    private val starting = Mutex()
    private val connections = ConcurrentHashMap<String, AcpConnection>()
    private val watchers = ConcurrentHashMap<String, Job>()
    private val used = ConcurrentHashMap<String, Long>()

    fun of(agent: String): AcpConnection? = connections[agent]?.takeIf { it.running }

    /** [entry]'s connection, started and initialized if it has none; throws with why it could not be. */
    suspend fun connect(entry: AgentEntry, step: (String) -> Unit): AcpConnection = starting.withLock {
        val id = entry.agent.id
        used[id] = System.currentTimeMillis()
        of(id)?.let { return it }
        step("Starting ${entry.agent.name}…")
        status(id, AgentStatus(AgentStatus.State.Starting))
        val program = launcher.start(entry, variables(entry), scope, launcher.login())
        val connection = AcpConnection(entry, scope, program, ::ended)
        try {
            withTimeout(START_MILLIS) { connection.start() }
        } catch (failure: Exception) {
            connection.stop()
            if (failure is CancellationException && failure !is kotlinx.coroutines.TimeoutCancellationException) throw failure
            val words = AcpFailures.words(entry, failure, launcher.tail(id))
            status(id, AgentStatus(AgentStatus.State.Failed, words))
            throw AssistantTurnException(words)
        }
        connections[id] = connection
        val info = connection.info
        status(id, AgentStatus(AgentStatus.State.Ready, identity = info.implementation?.let { "${it.title ?: it.name} ${it.version}" }, signIn = AcpMapping.signIn(info.authMethods)))
        watchers[id] = scope.launch { watch(connection) }
        connection
    }

    /** [agent] did something: its idle time starts again. */
    fun touch(agent: String) {
        used[agent] = System.currentTimeMillis()
    }

    suspend fun stop(agent: String) {
        watchers.remove(agent)?.cancel()
        connections.remove(agent)?.stop()
        status(agent, AgentStatus(AgentStatus.State.Off))
    }

    /** While it runs: the links it asks to open, and its idle stop. */
    private suspend fun watch(connection: AcpConnection) {
        val id = connection.entry.agent.id
        while (scope.isActive && connection.running) {
            delay(WATCH_MILLIS)
            launcher.links(id).lastOrNull()?.let { link(id, it) }
            if (busy(id)) used[id] = System.currentTimeMillis()
            else if (System.currentTimeMillis() - (used[id] ?: 0L) >= IDLE_MILLIS) {
                BackendLog.i(TAG, "Stopping ${connection.entry.agent.name}: idle")
                connections.remove(id, connection)
                connection.stop()
                status(id, AgentStatus(AgentStatus.State.Off))
                return
            }
        }
    }

    private fun ended(connection: AcpConnection, code: Int) {
        val id = connection.entry.agent.id
        if (!connections.remove(id, connection)) return
        watchers.remove(id)?.cancel()
        status(id, AgentStatus(AgentStatus.State.Failed, AcpFailures.ended(connection.entry, code, launcher.tail(id))))
    }

    private companion object {
        const val TAG = "AcpConnections"
        const val START_MILLIS = 90_000L
        const val WATCH_MILLIS = 1_000L
        const val IDLE_MILLIS = 10 * 60_000L
    }
}
