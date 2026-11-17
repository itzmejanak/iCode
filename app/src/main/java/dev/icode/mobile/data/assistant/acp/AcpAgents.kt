package dev.icode.mobile.data.assistant.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import com.agentclientprotocol.model.ElicitationAction
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.SessionConfigId
import com.agentclientprotocol.model.SessionConfigOptionValue
import com.agentclientprotocol.model.SessionUpdate
import dev.icode.mobile.data.assistant.AgentBinding
import dev.icode.mobile.data.assistant.AssistantAgents
import dev.icode.mobile.data.assistant.AssistantAgentsContext
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.AgentCommand
import dev.icode.mobile.domain.AgentIntent
import dev.icode.mobile.domain.AgentLink
import dev.icode.mobile.domain.AgentLog
import dev.icode.mobile.domain.AgentRules
import dev.icode.mobile.domain.AgentOption
import dev.icode.mobile.domain.AgentPlanItem
import dev.icode.mobile.domain.AgentSignIn
import dev.icode.mobile.domain.AgentStatus
import dev.icode.mobile.domain.AgentsState
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.McpValue
import dev.icode.mobile.domain.notices.AgentNotices
import dev.icode.mobile.domain.notices.Notice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.launch

/**
 * The coding agents (docs/assistant-and-mcp.md): which answers new chats, each chat's agent session, and
 * what each agent offers the page. A chat an agent answers is opened in that agent — new, or read back
 * from it (ACP `session/load`), so iCode keeps no second transcript — and its turns are prompts there,
 * in the assistant's own turn job ([dev.icode.mobile.data.assistant.AssistantTurns]). The agent's own
 * permission mode decides what it asks; iCode never answers for the user.
 */
@OptIn(UnstableApi::class)
internal class AcpAgents(
    private val context: AssistantAgentsContext,
    private val entries: List<AgentEntry>,
    private val launcher: AgentLauncher,
    private val store: AgentStore,
    /** The user's MCP servers to pass on, each a session's own ([AcpServers]). */
    private val servers: AcpServers,
    /** [command] typed in [chat]'s own terminal tab of [workspace] — a new tab when [chat] is null; returns the tab. */
    private val terminal: suspend (workspace: String?, chat: String?, command: String) -> String,
    /** Files changed outside the editor: the tree, Git and the open documents read again. */
    private val refreshFiles: () -> Unit,
) : AssistantAgents, AcpOperations.Owner, AcpAccess.Owner {
    private val scope = context.scope
    private val chats = context.chats
    private val page = context.page
    private val offers = ConcurrentHashMap<ChatKey, AgentChat>()
    private val loading = ConcurrentHashMap.newKeySet<ChatKey>()
    private val active = ConcurrentHashMap<String, AtomicInteger>()
    private val questions = AcpQuestions { question -> agents { it.copy(question = question) } }
    private val connections = AcpConnections(
        scope, launcher,
        variables = { entry -> store.reveal(entry.agent.id, keyNames(entry.agent.id)) },
        status = ::status,
        link = { agent, url -> agents { it.copy(link = AgentLink(agent, url)) } },
        busy = { agent -> (active[agent]?.get() ?: 0) > 0 },
    )
    private val access = AcpAccess(connections, launcher.commands, terminal, this)
    private val sessions = AcpSessions(
        context, connections, servers, owner = this, offers = AcpOffers { key, change -> offer(key, change) }, questions,
        signInOf = ::signInOf, refreshFiles = refreshFiles,
    )

    override fun start() = agents { it.copy(agents = entries.map(AgentEntry::agent), chosen = chosen()) }

    override fun handle(intent: AgentIntent) {
        when (intent) {
            is AgentIntent.Choose -> choose(intent.agent)
            is AgentIntent.Edit -> agents { it.copy(editing = intent.agent, log = intent.agent?.let { agent -> AgentLog(agent, launcher.tail(agent)) }) }
            is AgentIntent.SetOption -> scope.launch { set(intent.option) { session -> sessions.configure(session, intent.option, intent.value) } }
            is AgentIntent.SetToggle -> scope.launch { set(intent.option) { it.setConfigOption(SessionConfigId(intent.option), SessionConfigOptionValue.BoolValue(intent.on)) } }
            is AgentIntent.SignIn -> scope.launch { access.signIn(intent.agent, intent.method) }
            is AgentIntent.SignedIn -> scope.launch { entryOf(intent.agent ?: shownKey()?.let(::agentOf))?.let { access.signedIn(it, restart = true) } }
            AgentIntent.LinkTaken -> agents { it.copy(link = null) }
            is AgentIntent.SaveKeys -> scope.launch { access.saveKeys(intent.agent, intent.values) }
            is AgentIntent.OpenInTerminal -> scope.launch { access.openInTerminal(intent.agent) }
            is AgentIntent.Answer -> questions.answer(intent.answer)
            is AgentIntent.AnswerApproval -> context.questions.chose(intent.callId, intent.choice)
            is AgentIntent.ReadLog -> agents { it.copy(log = AgentLog(intent.agent, launcher.tail(intent.agent))) }
            is AgentIntent.Stop -> scope.launch { connections.stop(intent.agent) }
        }
    }

    override fun answers(workspace: String?, session: String): Boolean = chats.of(workspace, session).agent != null

    override fun takes(workspace: String?, session: String): Boolean = chosen()?.let { chats.bind(workspace, session, AgentBinding(it)) } != null

    override suspend fun converse(workspace: String?, session: String, input: String, attachments: List<String>, events: AssistantEvents, step: (String) -> Unit) {
        val key = ChatKey(workspace, session)
        val entry = entryOf(agentOf(key)) ?: throw AssistantTurnException("This chat's agent is no longer listed")
        busy(entry) {
            // What the agent reads decides which files can go: an image only to one that reads images.
            val connection = connections.connect(entry, step)
            val model = AssistantModel(entry.agent.name, images = connection.info.capabilities.promptCapabilities.image)
            val files = context.attachments.read(context.workspaceOf(workspace), attachments, model) { bytes, extension -> chats.keep(workspace, session, bytes, extension) }
            send(key, entry, AssistantMessage.User(input, files), events, step)
        }
    }

    /**
     * [message] in the chat, then its session opened and the message sent: a session that cannot open —
     * the agent asks to sign in — leaves the message there, unanswered, to send again. A chat read back
     * in the meantime is what the agent has, the message after it.
     */
    private suspend fun send(key: ChatKey, entry: AgentEntry, message: AssistantMessage.User, events: AssistantEvents, step: (String) -> Unit) {
        chats.append(key.workspace, key.session, message)
        page.listed(key.workspace)
        page.refresh(key.workspace, key.session)
        publishShown()
        val (opened, _) = sessions.open(key, entry, step)
        if (chats.of(key.workspace, key.session).messages.lastOrNull() != message) {
            chats.append(key.workspace, key.session, message)
            page.refresh(key.workspace, key.session)
        }
        sessions.prompt(key, entry, opened, message, events)
    }

    override suspend fun retry(workspace: String?, session: String, events: AssistantEvents, step: (String) -> Unit) {
        val key = ChatKey(workspace, session)
        val entry = entryOf(agentOf(key)) ?: throw AssistantTurnException("This chat's agent is no longer listed")
        val message = chats.withdraw(workspace, session) ?: return
        busy(entry) { send(key, entry, message, events, step) }
    }

    override fun shown(workspace: String?, session: String?) {
        publishShown()
        val key = ChatKey(workspace, session ?: return)
        val binding = chats.of(workspace, session).agent ?: return
        val entry = entryOf(binding.agent) ?: return
        // Read back once: from the agent, the first time the chat is shown since iCode started.
        val wanted = binding.session != null && chats.of(workspace, session).messages.isEmpty() &&
            page.current.turn?.let { it.workspace == workspace && it.session == session } != true
        if (!wanted || !loading.add(key)) return
        scope.launch {
            offer(key) { it.copy(loading = true, failure = null) }
            try {
                busy(entry) { sessions.open(key, entry) {} }
                offer(key) { it.copy(loading = false) }
            } catch (failure: Exception) {
                offer(key) { it.copy(loading = false, failure = failure.message, signIn = signInOf(entry, failure)) }
            } finally {
                loading.remove(key)
            }
        }
    }

    override fun chooseBuiltIn() = if (chosen() != null) choose(null) else Unit

    override fun moved(from: String, to: String) {
        offers.keys.filter { it.workspace == from }.forEach { key -> offers.remove(key)?.let { offers[key.copy(workspace = to)] = it.copy(workspace = to) } }
        sessions.moved(from, to)
    }

    override fun forgotten(workspace: String) {
        offers.keys.removeAll { it.workspace == workspace }
        sessions.forgotten(workspace)
    }

    // What the agent asks of the user, from its own process.

    override suspend fun approve(operations: AcpOperations, call: AssistantCall, options: List<PermissionOption>): String? {
        // Asked with no turn of its chat in hand, nobody is there to answer: the agent is told it was cancelled.
        page.current.turn?.takeIf { it.workspace == operations.chat.workspace && it.session == operations.chat.session } ?: return null
        return context.questions.choose(call, call.command ?: call.file ?: call.summary, AcpMapping.choices(options))
    }

    override suspend fun ask(operations: AcpOperations, request: CreateElicitationRequest): CreateElicitationResponse {
        // As an approval: shown only with a turn of its chat in hand, and cancelled without one.
        page.current.turn?.takeIf { it.workspace == operations.chat.workspace && it.session == operations.chat.session }
            ?: return CreateElicitationResponse(ElicitationAction.Cancel)
        return questions.ask(operations.entry.agent.name, request)
    }

    override fun between(operations: AcpOperations, update: SessionUpdate) {
        if (AcpOffersRules.offers(update)) offer(operations.chat) { AcpOffersRules.between(it, update) }
    }

    /** [option] of the shown chat changed by [change]; what the agent says of its settings after it, shown. */
    private suspend fun set(option: String, change: suspend (ClientSession) -> Unit) {
        val key = shownKey() ?: return
        val entry = entryOf(agentOf(key)) ?: return
        val session = sessions.session(key, entry.agent.id) ?: return
        runCatching { change(session) }
            .onSuccess { offer(key) { it.copy(options = sessions.optionsOf(session)) } }
            .onFailure { tell(AgentNotices.notSet(entry.agent.name, it.message ?: it.javaClass.simpleName)) }
    }

    private fun choose(agent: String?) {
        store.choose(agent?.takeIf { id -> entries.any { it.agent.id == id } })
        agents { it.copy(chosen = chosen()) }
        val shown = page.current
        // A new chat takes the choice; a chat answered by something else gives way to one.
        if (shown.opened && shown.session != null && chats.of(shown.workspace, shown.session).agent?.agent != chosen()) page.display(shown.workspace, null)
        publishShown()
    }

    private fun chosen(): String? = store.chosen()?.takeIf { id -> entries.any { it.agent.id == id } }

    private fun publishShown() {
        val shown = page.current
        val chat = when {
            !shown.opened -> null
            shown.session == null -> chosen()?.let { AgentChat(shown.workspace, null, it) }
            else -> chats.of(shown.workspace, shown.session).agent?.let { binding ->
                offers[ChatKey(shown.workspace, shown.session)] ?: AgentChat(shown.workspace, shown.session, binding.agent)
            }
        }
        agents { if (it.chat == chat) it else it.copy(chat = chat) }
    }

    override fun offer(key: ChatKey, change: (AgentChat) -> AgentChat) {
        val agent = agentOf(key) ?: return
        offers.compute(key) { _, before -> change(before ?: AgentChat(key.workspace, key.session, agent)) }
        publishShown()
    }

    private fun status(agent: String, status: AgentStatus) = agents {
        val known = status.copy(signIn = status.signIn.ifEmpty { it.status[agent]?.signIn.orEmpty() })
        it.copy(status = it.status + (agent to known), keys = it.keys + (agent to store.keys(agent, AgentRules.keyNames(known))))
    }

    private fun agents(change: (AgentsState) -> AgentsState) = context.stateHolder.update { it.copy(agents = change(it.agents)) }

    override fun tell(notice: Notice) = page.tell(notice)

    override fun shownKey(): ChatKey? = page.current.takeIf { it.opened }?.let { shown -> shown.session?.let { ChatKey(shown.workspace, it) } }

    override fun agentOf(key: ChatKey): String? = chats.of(key.workspace, key.session).agent?.agent

    override fun entryOf(agent: String?): AgentEntry? = entries.firstOrNull { it.agent.id == agent }

    private fun keyNames(agent: String): List<String> = AgentRules.keyNames(context.stateHolder.current.agents.status[agent])

    override fun offered(): Set<ChatKey> = offers.keys.toSet()

    override fun session(key: ChatKey): String? = chats.of(key.workspace, key.session).agent?.session

    override fun saveKeys(agent: String, values: List<McpValue>) {
        store.save(agent, values)
        agents { it.copy(keys = it.keys + (agent to store.keys(agent, keyNames(agent)))) }
    }

    /** [tab] shown in [key]'s Terminal sheet, if that chat is still the one shown. */
    override fun showTab(key: ChatKey?, tab: String) {
        if (key != null) page.state { if (it.workspace == key.workspace && it.session == key.session) it.copy(tab = tab) else it }
    }

    override fun keysMethod(agent: String): String? = AgentRules.keysMethod(context.stateHolder.current.agents.status[agent])

    /** How [entry] offers to sign in, when [failure] was its asking to; none otherwise. */
    private fun signInOf(entry: AgentEntry, failure: Throwable): List<AgentSignIn> =
        if (AcpFailures.needsSignIn(failure)) context.stateHolder.current.agents.status[entry.agent.id]?.signIn.orEmpty() else emptyList()

    /** [work] with [entry]'s process held up: it is not stopped as idle meanwhile. */
    private suspend fun <T> busy(entry: AgentEntry, work: suspend () -> T): T {
        val count = active.getOrPut(entry.agent.id) { AtomicInteger() }.apply { incrementAndGet() }
        try { return work() } finally { count.decrementAndGet().also { connections.touch(entry.agent.id) } }
    }
}
