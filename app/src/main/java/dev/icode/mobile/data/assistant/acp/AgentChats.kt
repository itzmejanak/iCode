package dev.icode.mobile.data.assistant.acp

import android.content.Context
import com.agentclientprotocol.model.CreateElicitationRequest
import com.agentclientprotocol.model.CreateElicitationResponse
import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.agents.AgentReceipts
import dev.icode.mobile.data.assistant.AgentAnswers
import dev.icode.mobile.data.assistant.AssistantChats
import dev.icode.mobile.data.assistant.AssistantEvents
import dev.icode.mobile.data.assistant.AssistantPage
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.GuestStdio
import dev.icode.mobile.data.runtime.shellLine
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentChat
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.McpAnswer
import dev.icode.mobile.domain.McpQuestion
import dev.icode.mobile.domain.notices.AgentNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The coding agents in the chats (docs/assistant-and-mcp.md): the one a chat is answered by, chosen in
 * the model sheet; started as a chat it answers opens, so its settings and commands show before the
 * first message, and its sign-in ways until it is signed in; the one-line note an untested agent gets
 * the first time; and each message to one, sent once every open file's unsaved changes are saved.
 */
internal class AgentChats(
    private val scope: CoroutineScope,
    private val page: AssistantPage,
    private val chats: AssistantChats,
    private val preferences: AgentPreferences,
    private val catalogue: () -> List<CodingAgent>,
    private val installed: () -> Map<String, InstalledAgent>,
    /** A chat's place: its workspace's folder, by its location, or the runtime's home for none. */
    private val placeOf: (String?) -> AssistantWorkspace,
    /** [command] run in a chat's own terminal tab, opened if it has none. */
    private val runInTab: suspend (workspace: String?, chat: String, command: String) -> Unit,
    /** Every open file with unsaved changes saved. */
    private val saveOpen: suspend () -> Unit,
    /** The page as it changes: the chat shown, and who answers it. */
    private val states: Flow<AssistantState>,
    /** The question card the MCP servers ask on, for the agents' questions too; none where there is no card. */
    private val ask: (suspend ((Long) -> McpQuestion) -> McpAnswer)?,
    /** Each installed agent's version, as it changes: an update put in place, or a removal. */
    private val releases: Flow<Map<String, String>> = emptyFlow(),
    /**
     * The agents, told back of their sessions' changes ([offered]) and of their stops ([stopped]), their
     * sessions kept in the chats, and their questions put to the user ([asked]).
     */
    agentsOf: (
        changed: (String, Pair<String?, String>) -> Unit,
        stopped: (String) -> Unit,
        records: AcpAgents.Records,
        asked: (suspend (String, CreateElicitationRequest) -> CreateElicitationResponse)?,
    ) -> AcpAgents,
) : AgentAnswers {
    private val agents = agentsOf(::offered, ::stopped, ChatRecords(chats), ask?.let { { agent, request -> asked(agent, request) } })
    private val turn = AcpTurn(agents)
    private val outside = OutsideSessions(scope, page, chats, agents, placeOf, installed, runInTab, ::name)

    /** The chat shown, by its session — [AcpAgents.NEW_CHAT] for one not started — and the agent answering it, if one is. */
    private data class Shown(val chat: Pair<String?, String>, val agent: String?)

    /** What is kept, on the page from the start; then each chat an agent answers starts it as it is shown. */
    fun start() {
        page.state { it.copy(agent = preferences.last, untestedSaid = preferences.untestedSaid()) }
        scope.launch { states.map(::shownOf).distinctUntilChanged().collect(::shown) }
        scope.launch {
            var before: Map<String, String>? = null
            releases.collect { now ->
                before?.let { was -> was.keys.filter { now[it] != was[it] }.forEach { released(it) } }
                before = now
            }
        }
    }

    /** [agent]'s release changed or went: the one running stops, and the chat shown starts it again where it is still installed. */
    private suspend fun released(agent: String) {
        agents.restart(agent)
        if (agent in installed()) prepareShown()
    }

    fun handle(intent: AgentChatIntent) {
        when (intent) {
            is AgentChatIntent.Choose -> choose(intent.id)
            is AgentChatIntent.SignIn -> signIn(intent.agent, intent.method)
            is AgentChatIntent.SaveKeys -> scope.launch {
                runCatching { preferences.saveKeys(intent.agent, intent.values) }.onFailure { return@launch page.tell(AgentNotices.notSignedIn(name(intent.agent), "Unable to keep the keys")) }
                // An agent reads its keys as it starts.
                agents.restart(intent.agent)
                prepareShown()
            }
            is AgentChatIntent.SignedIn -> scope.launch { prepareShown() }
            is AgentChatIntent.Set -> set(intent)
            is AgentChatIntent.OpenProgram -> outside.openProgram(intent.agent)
            AgentChatIntent.Find -> outside.find()
            is AgentChatIntent.TakeUp -> outside.takeUp(intent.found)
        }
    }

    /** A model chosen: new chats start with it, not with the agent chosen before. */
    fun modelChosen() {
        preferences.last = null
        page.state { it.copy(agent = null) }
    }

    override fun name(id: String): String = catalogue().firstOrNull { it.id == id }?.name ?: id

    override suspend fun answer(
        id: String,
        chat: Pair<String?, String>,
        place: AssistantWorkspace,
        history: List<AssistantMessage>,
        message: AssistantMessage.User,
        events: AssistantEvents,
        aloud: Boolean,
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    ) {
        saveOpen()
        said(id)
        try {
            turn.answer(id, chat, place, history, message, events, aloud, approve)
        } catch (needed: AcpAgents.SignInNeeded) {
            page.state { it.copy(signIns = it.signIns + (id to needed.methods)) }
            throw AssistantTurnException("Sign in to ${name(id)} first, then send it again")
        } finally {
            // Its session may be new, started again by this message: what it offers shows.
            offered(id, chat)
        }
    }

    override fun forget(chat: Pair<String?, String>) = turn.forget(chat)

    /** The chat shown answered by [id] from now on, and new chats too; it starts for the chat as it is shown. */
    private fun choose(id: String) {
        val shown = page.current
        if (shown.opened && shown.session != null) {
            chats.choose(shown.workspace, shown.session, AssistantAgent(id))
            page.refresh(shown.workspace, shown.session)
        }
        runCatching { preferences.last = id }
        page.state { it.copy(agent = id) }
    }

    private fun shownOf(state: AssistantState) = Shown(
        state.workspace to (state.session ?: AcpAgents.NEW_CHAT),
        (state.answering as? AssistantAgent)?.id?.takeIf { state.opened },
    )

    /** The chat [shown]: what its agent offers it on the page, and the agent started for it if it is not. */
    private fun shown(shown: Shown) {
        val agent = shown.agent ?: return page.state { it.copy(agentChat = null) }
        offered(agent, shown.chat)
        // Started apart, so another chat shown meanwhile is not kept waiting on it.
        if (agent in installed()) scope.launch { prepare(agent, shown.chat) }
    }

    private suspend fun prepareShown() {
        val shown = shownOf(page.current)
        shown.agent?.let { prepare(it, shown.chat) }
    }

    /** [agent] started for [chat], its session made: the sign-in it asks for shown until it is signed in. */
    private suspend fun prepare(agent: String, chat: Pair<String?, String>) {
        runCatching { agents.use(agent) { agents.prepare(it, chat, placeOf(chat.first).guestPath) } }
            .onSuccess {
                page.state { it.copy(signIns = it.signIns - agent) }
                offered(agent, chat)
            }
            .onFailure { failure ->
                if (failure is AcpAgents.SignInNeeded) page.state { it.copy(signIns = it.signIns + (agent to failure.methods)) }
                else page.tell(AgentNotices.notStarted(name(agent), failure.message ?: failure.javaClass.simpleName))
            }
    }

    /** What [agent] offers [chat] now, on the page while it is the chat shown. */
    private fun offered(agent: String, chat: Pair<String?, String>) {
        val held = agents.held(agent, chat)
        val offer = held?.let { AgentChat(agent, AcpSettings.of(it.session), it.operations.commands) }
        page.state { state -> if (shownOf(state) == Shown(chat, agent)) state.copy(agentChat = offer) else state }
    }

    /** [agent]'s question on the card, as an MCP server's is asked; one it cannot be shown as is cancelled. */
    private suspend fun asked(agent: String, request: CreateElicitationRequest): CreateElicitationResponse {
        val questions = ask ?: return AcpQuestions.CANCELLED
        val question = AcpQuestions.questionOf(0, name(agent), request) ?: return AcpQuestions.CANCELLED
        val answer = questions { id -> question.copy(id = id) }
        return AcpQuestions.responseOf(answer, question)
    }

    /** [agent] stopped: the chat shown no longer has what it offered, until it starts again. */
    private fun stopped(agent: String) = page.state { if (it.agentChat?.agent == agent) it.copy(agentChat = null) else it }

    private fun set(intent: AgentChatIntent.Set) {
        val chat = shownOf(page.current).chat
        val held = agents.held(intent.agent, chat) ?: return
        scope.launch {
            runCatching { AcpSettings.set(held.session, intent.setting, intent.value) }
                .onSuccess { offered(intent.agent, chat) }
                .onFailure { page.tell(AgentNotices.notSet(name(intent.agent), it.message ?: it.javaClass.simpleName)) }
        }
    }

    private fun signIn(agent: String, method: String) {
        val way = page.current.signIns[agent]?.firstOrNull { it.id == method }?.way ?: return
        scope.launch {
            when (way) {
                AgentSignInWay.ByAgent -> runCatching { agents.authenticate(agent, method) }
                    .onSuccess { prepareShown() }
                    .onFailure { page.tell(AgentNotices.notSignedIn(name(agent), it.message ?: it.javaClass.simpleName)) }
                is AgentSignInWay.InTerminal -> {
                    val command = installed()[agent]?.command ?: return@launch
                    val line = shellLine(command.take(1) + way.args, way.env)
                    val shown = page.current
                    runCatching { runInTab(shown.workspace, shown.session ?: SIGN_IN_TAB, line) }
                        .onFailure { page.tell(AgentNotices.notSignedIn(name(agent), it.message ?: it.javaClass.simpleName)) }
                }
                is AgentSignInWay.Keys -> Unit
            }
        }
    }

    /** An untested agent's note is given once: from its first message on, it is not. */
    private fun said(id: String) {
        if (id in CodingAgents.VERIFIED || id in page.current.untestedSaid) return
        runCatching { preferences.said(id) }
        page.state { it.copy(untestedSaid = it.untestedSaid + id) }
    }

    /** Each agent's session for a chat, kept with the chat in its list (docs/assistant-and-mcp.md). */
    private class ChatRecords(private val chats: AssistantChats) : AcpAgents.Records {
        override fun of(agent: String, chat: Pair<String?, String>) = chats.of(chat.first, chat.second).agentSessions[agent]

        override fun keep(agent: String, chat: Pair<String?, String>, id: String) = chats.agentSession(chat.first, chat.second, agent, id)

        override fun drop(chat: Pair<String?, String>) = chats.dropAgentSessions(chat.first, chat.second)
    }

    companion object {
        /** The tab a sign-in runs in from a new chat, which has no tab of its own yet. */
        private const val SIGN_IN_TAB = "agent-sign-in"

        /** The guest folder the agents' logs go in: what each wrote on stderr since it last started. */
        private const val LOGS = "/tmp/icode-agents"

        /**
         * The agents in the chats as iCode runs them: in the runtime, with the login's variables, their
         * receipts' commands and their kept keys; [save] saves an open file, under the workspace's lock.
         */
        fun maker(
            context: Context,
            icode: ICodeStateHolder,
            scope: CoroutineScope,
            toolbox: AssistantToolbox,
            save: suspend (String) -> Boolean,
        ): (AssistantPage, AssistantChats, (String?) -> AssistantWorkspace, (suspend ((Long) -> McpQuestion) -> McpAnswer)?) -> AgentChats {
            val layout = toolbox.execution.layout
            val receipts = AgentReceipts(layout.home)
            val preferences = AgentPreferences(context)
            val saveOpen: suspend () -> Unit = {
                icode.mutations.withLock { icode.current.openDocuments.filter { it.dirty }.forEach { save(it.location) } }
            }
            return { page, chats, placeOf, ask ->
                AgentChats(
                    scope, page, chats, preferences, { icode.current.agents.catalogue }, receipts::installed, placeOf, toolbox::runInTab, saveOpen,
                    icode.snapshot.map { it.assistant }, ask, icode.snapshot.map { snapshot -> snapshot.agents.installed.mapValues { it.value.version } }.distinctUntilChanged(),
                ) { changed, stopped, records, asked ->
                    AcpAgents(
                        scope, GuestStdio(layout, GuestContext.Command, LOGS, tag = "agent"),
                        login = { toolbox.execution.environment.of(GuestContext.Command).variables },
                        installed = receipts::installed, keys = preferences::keys, version = BuildConfig.VERSION_NAME,
                        changed = changed, stopped = stopped, records = records, asked = asked,
                    )
                }
            }
        }
    }
}
