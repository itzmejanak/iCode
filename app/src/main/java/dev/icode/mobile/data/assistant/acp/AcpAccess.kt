package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.model.AuthMethod
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.McpValue
import dev.icode.mobile.domain.notices.AgentNotices
import dev.icode.mobile.domain.notices.Notice

/**
 * A coding agent's sign-in, its keys and its own CLI (docs/assistant-and-mcp.md). An agent signs in as it
 * offers to: in itself (often through the browser, whose link iCode shows), with its own login in a
 * terminal tab, or with keys in its environment, which iCode keeps encrypted. What ACP does not carry is
 * reached through the agent's own CLI in the chat's terminal tab, resuming the chat where it can.
 */
@OptIn(UnstableApi::class)
internal class AcpAccess(
    private val connections: AcpConnections,
    private val commands: AgentCommands,
    /** [command] typed in [chat]'s own terminal tab of [workspace] — a new tab when [chat] is null; returns the tab. */
    private val terminal: suspend (workspace: String?, chat: String?, command: String) -> String,
    private val owner: Owner,
) {
    /** What the agents' owner gives: the shown chat, each chat's agent, and the page. */
    interface Owner {
        fun shownKey(): ChatKey?
        fun agentOf(key: ChatKey): String?
        fun entryOf(agent: String?): AgentEntry?

        /** [key]'s session in its agent, once it has one. */
        fun session(key: ChatKey): String?
        fun offer(key: ChatKey, change: (AgentChat) -> AgentChat)

        /** Every chat whose agent offers it something now. */
        fun offered(): Set<ChatKey>
        fun showTab(key: ChatKey?, tab: String)
        fun tell(notice: Notice)

        /** [values] saved, encrypted, for [agent]; its keys shown as saved. */
        fun saveKeys(agent: String, values: List<McpValue>)
        fun keysMethod(agent: String): String?
    }

    /** [agent] — the shown chat's when null — signs in with [method]. */
    suspend fun signIn(agent: String?, method: String) {
        val key = owner.shownKey()?.takeIf { agent == null || owner.agentOf(it) == agent }
        val entry = owner.entryOf(agent ?: key?.let(owner::agentOf)) ?: return
        val name = entry.agent.name
        val connection = runCatching { connections.connect(entry) {} }.getOrElse { return owner.tell(AgentNotices.notSignedIn(name, it.message ?: "")) }
        val auth = connection.info.authMethods.firstOrNull { it.id.value == method } ?: return
        key?.let { owner.offer(it) { chat -> chat.copy(signingIn = method) } }
        when (auth) {
            is AuthMethod.TerminalAuth -> {
                // The agent's own login, in the chat's tab (or a new one): its args and variables replace the agent's.
                val program = commands.server(entry)?.first() ?: return owner.tell(AgentNotices.notInstalled(name))
                val command = AgentCommands.commandLine(listOf(program) + auth.args.orEmpty(), entry.distribution.env + auth.env.orEmpty())
                runCatching { terminal(key?.workspace, key?.session, command) }
                    .onSuccess { owner.showTab(key, it) }
                    .onFailure { owner.tell(AgentNotices.cliNotOpened(name, it.message ?: "")) }
            }
            is AuthMethod.EnvVarAuth -> Unit // Its keys are entered on its settings page, then saved.
            else -> try {
                connection.authenticate(method)
                signedIn(entry, restart = false)
            } catch (failure: Exception) {
                owner.tell(AgentNotices.notSignedIn(name, AcpFailures.words(entry, failure)))
            } finally {
                key?.let { owner.offer(it) { chat -> chat.copy(signingIn = null) } }
            }
        }
    }

    /** Signed in: every chat of [entry]'s stops asking; one signed in outside it starts again to read its credentials. */
    suspend fun signedIn(entry: AgentEntry, restart: Boolean) {
        if (restart) connections.stop(entry.agent.id)
        owner.offered().filter { owner.agentOf(it) == entry.agent.id }.forEach { key ->
            owner.offer(key) { it.copy(signIn = emptyList(), signingIn = null, failure = null) }
        }
        owner.tell(AgentNotices.signedIn(entry.agent.name))
    }

    /** [agent]'s keys saved; it starts again with them and, where it signs in with keys, is asked to. */
    suspend fun saveKeys(agent: String, values: List<McpValue>) {
        val entry = owner.entryOf(agent) ?: return
        owner.saveKeys(agent, values)
        connections.stop(agent)
        val method = owner.keysMethod(agent) ?: return owner.tell(AgentNotices.keysSaved(entry.agent.name))
        runCatching { connections.connect(entry) {}.authenticate(method) }
            .onSuccess { signedIn(entry, restart = false) }
            .onFailure { owner.tell(AgentNotices.notSignedIn(entry.agent.name, AcpFailures.words(entry, it))) }
    }

    /** [agent]'s own CLI — the shown chat's agent's when null, resuming that chat where it can — in a terminal tab. */
    suspend fun openInTerminal(agent: String?) {
        val key = owner.shownKey()?.takeIf { agent == null }
        val entry = owner.entryOf(agent ?: key?.let(owner::agentOf)) ?: return
        val argv = commands.cli(entry, key?.let(owner::session)) ?: return owner.tell(AgentNotices.notInstalled(entry.agent.name))
        runCatching { terminal(key?.workspace, key?.session, AgentCommands.commandLine(argv, entry.distribution.env)) }
            .onSuccess { owner.showTab(key, it) }
            .onFailure { owner.tell(AgentNotices.cliNotOpened(entry.agent.name, it.message ?: it.javaClass.simpleName)) }
    }
}
