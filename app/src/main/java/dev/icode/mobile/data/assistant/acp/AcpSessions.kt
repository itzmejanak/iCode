package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.SessionModeState
import com.agentclientprotocol.model.SessionModelState
import dev.icode.mobile.data.assistant.AgentBinding
import dev.icode.mobile.data.assistant.AssistantAgentsContext
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AssistantMessage
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Each chat's session in its agent (docs/assistant-and-mcp.md): opened new, or read back from the agent
 * (ACP `session/load`) once per process — what it sends of the chat replacing what the page showed, so
 * iCode keeps no second transcript — and its messages sent as prompts. One chat opens one at a time.
 */
@OptIn(UnstableApi::class)
internal class AcpSessions(
    private val context: AssistantAgentsContext,
    private val connections: AcpConnections,
    private val servers: AcpServers,
    /** What answers for each session: approvals, questions and what comes between turns. */
    private val owner: AcpOperations.Owner,
    private val offers: AcpOffers,
    private val questions: AcpQuestions,
    /** How the agent offers to sign in, when a failure was its asking to. */
    private val signInOf: (AgentEntry, Throwable) -> List<AgentSignIn>,
    private val refreshFiles: () -> Unit,
) {
    private val chats = context.chats
    private val page = context.page
    private val operations = ConcurrentHashMap<ChatKey, AcpOperations>()
    private val locks = ConcurrentHashMap<ChatKey, Mutex>()

    /**
     * [key]'s session in its agent, the agent started if it is not: new for a chat it has not answered yet,
     * else read back — what it sends of the chat replacing what the page showed — once per process.
     */
    suspend fun open(key: ChatKey, entry: AgentEntry, step: (String) -> Unit): Pair<ClientSession, AcpConnection> =
        locks.getOrPut(key) { Mutex() }.withLock {
            val connection = connections.connect(entry, step)
            val binding = chats.of(key.workspace, key.session).agent ?: AgentBinding(entry.agent.id)
            binding.session?.let { connection.sessions[it] }?.let { return@withLock it to connection }
            val ops = AcpOperations(entry, key, context.workspaceOf(key.workspace).guestPath, owner).also { operations[key] = it }
            val session = try {
                if (binding.session == null) {
                    connection.newSession(ops.cwd, servers.of(connection.info.capabilities.mcpCapabilities), ops)
                        .also { chats.bind(key.workspace, key.session, binding.copy(session = it.sessionId.value)) }
                } else {
                    if (!connection.info.capabilities.loadSession) throw AssistantTurnException("${entry.agent.name} can't open this chat again: it does not load sessions")
                    step("Reading the chat back from ${entry.agent.name}…")
                    replay(key, connection, ops, binding.session)
                }
            } catch (failure: AssistantTurnException) {
                throw failure
            } catch (failure: Exception) {
                offers.change(key) { it.copy(signIn = signInOf(entry, failure)) }
                throw AssistantTurnException(AcpFailures.words(entry, failure))
            }
            offers.change(key) { it.copy(options = optionsOf(session), signIn = emptyList(), failure = null) }
            session to connection
        }

    /** [message], in [key]'s chat already, sent to its agent as the turn's prompt; the workspace read again when the agent changed files. */
    suspend fun prompt(key: ChatKey, entry: AgentEntry, session: ClientSession, message: AssistantMessage.User, events: AssistantEvents) {
        val ops = operations[key] ?: throw AssistantTurnException("${entry.agent.name}'s session is not open")
        val changed = try {
            AcpTurn(entry, session, ops, chats, page, offers, questions).run(message, events)
        } catch (failed: AcpTurnFailure) {
            offers.change(key) { it.copy(signIn = signInOf(entry, failed.failure)) }
            throw AssistantTurnException(AcpFailures.words(entry, failed.failure))
        } finally {
            connections.touch(entry.agent.id)
        }
        if (changed) refreshFiles()
    }

    /** [key]'s session, when it is open in its agent's process now. */
    fun session(key: ChatKey, agent: String): ClientSession? =
        chats.of(key.workspace, key.session).agent?.session?.let { connections.of(agent)?.sessions?.get(it) }

    /** The settings [session] offers: its config options, else its modes and models as ACP had them before. */
    fun optionsOf(session: ClientSession) = AcpMapping.options(
        session.configOptions.value.takeIf { session.configOptionsSupported },
        if (session.modesSupported) SessionModeState(session.currentMode.value, session.availableModes) else null,
        if (session.modelsSupported) SessionModelState(session.currentModel.value, session.availableModels) else null,
    )

    /** [option] of [session] set to [value]: a mode or a model as ACP had them before config options, or a config option. */
    suspend fun configure(session: ClientSession, option: String, value: String) {
        when (option) {
            AcpMapping.MODE -> session.setMode(SessionModeId(value))
            AcpMapping.MODEL -> session.setModel(ModelId(value))
            else -> session.setConfigOption(SessionConfigId(option), SessionConfigOptionValue.StringValue(value))
        }
    }

    fun moved(from: String, to: String) {
        operations.keys.filter { it.workspace == from }.forEach { key ->
            operations.remove(key)?.let { ops -> operations[key.copy(workspace = to)] = ops.apply { chat = chat.copy(workspace = to) } }
        }
        locks.keys.filter { it.workspace == from }.forEach { key -> locks.remove(key)?.let { locks[key.copy(workspace = to)] = it } }
    }

    fun forgotten(workspace: String) {
        operations.keys.removeAll { it.workspace == workspace }
        locks.keys.removeAll { it.workspace == workspace }
    }

    private suspend fun replay(key: ChatKey, connection: AcpConnection, ops: AcpOperations, session: String): ClientSession {
        val replay = AcpReplay()
        val transcript = AcpTranscript(ops.cwd, ReplaySink(key, replay))
        ops.attach(transcript)
        try {
            val opened = connection.loadSession(session, ops.cwd, servers.of(connection.info.capabilities.mcpCapabilities), ops)
            ops.settle()
            ops.finish(transcript)
            chats.replaced(key.workspace, key.session, replay.messages)
            page.refresh(key.workspace, key.session)
            return opened
        } finally {
            ops.attach(null)
        }
    }

    /** A chat read back: its messages to the replay, what the agent offers to the page. */
    private inner class ReplaySink(private val key: ChatKey, replay: AcpReplay) : AcpTranscript.Sink by replay {
        override fun plan(plan: List<AgentPlanItem>) = offers.change(key) { it.copy(plan = plan) }
        override fun commands(commands: List<AgentCommand>) = offers.change(key) { it.copy(commands = commands) }
        override fun options(options: List<AgentOption>) = offers.change(key) { it.copy(options = options) }
        override fun used(used: Long, size: Long) = offers.change(key) { it.copy(window = size) }
        override fun mode(current: String) = offers.change(key) { it.copy(options = AcpOffersRules.withMode(it.options, current)) }
    }
}
