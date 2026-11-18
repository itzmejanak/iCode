package dev.icode.mobile.data.assistant.acp

import android.content.Context
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
import dev.icode.mobile.data.runtime.shellQuote
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AgentChatIntent
import dev.icode.mobile.domain.AgentSignInWay
import dev.icode.mobile.domain.AssistantAgent
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantOption
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import dev.icode.mobile.domain.InstalledAgent
import dev.icode.mobile.domain.notices.AgentNotices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The coding agents in the chats (docs/assistant-and-mcp.md): the one a chat is answered by, chosen
 * in the model sheet; its sign-in, checked once — its ways shown in the chat until it is signed in;
 * the one-line note an untested agent gets the first time; and each message to one, sent once every
 * open file's unsaved changes are saved, so the agent works on the user's latest.
 */
internal class AgentChats(
    private val scope: CoroutineScope,
    private val page: AssistantPage,
    private val chats: AssistantChats,
    private val agents: AcpAgents,
    private val preferences: AgentPreferences,
    private val catalogue: () -> List<CodingAgent>,
    private val installed: () -> Map<String, InstalledAgent>,
    /** A chat's place: its workspace's folder, by its location, or the runtime's home for none. */
    private val placeOf: (String?) -> AssistantWorkspace,
    /** [command] run in a chat's own terminal tab, opened if it has none. */
    private val runInTab: suspend (workspace: String?, chat: String, command: String) -> Unit,
    /** Every open file with unsaved changes saved. */
    private val saveOpen: suspend () -> Unit,
) : AgentAnswers {
    private val turn = AcpTurn(agents)

    /** What is kept, on the page from the start: the agent new chats start with, and the notes already given. */
    fun start() = page.state { it.copy(agent = preferences.last, untestedSaid = preferences.untestedSaid()) }

    fun handle(intent: AgentChatIntent) {
        when (intent) {
            is AgentChatIntent.Choose -> choose(intent.id)
            is AgentChatIntent.SignIn -> signIn(intent.agent, intent.method)
            is AgentChatIntent.SaveKeys -> scope.launch {
                runCatching { preferences.saveKeys(intent.agent, intent.values) }.onFailure { return@launch page.tell(AgentNotices.notSignedIn(name(intent.agent), "Unable to keep the keys")) }
                // An agent reads its keys as it starts.
                agents.restart(intent.agent)
                check(intent.agent)
            }
            is AgentChatIntent.SignedIn -> scope.launch { check(intent.agent) }
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
        approve: suspend (AssistantCall, List<AssistantOption>) -> String?,
    ) {
        saveOpen()
        said(id)
        try {
            turn.answer(id, chat, place, history, message, events, approve)
        } catch (needed: AcpAgents.SignInNeeded) {
            preferences.signedIn(id, false)
            page.state { it.copy(signIns = it.signIns + (id to needed.methods)) }
            throw AssistantTurnException("Sign in to ${name(id)} first, then send it again")
        }
    }

    override fun forget(chat: Pair<String?, String>) = turn.forget(chat)

    /** The chat shown answered by [id] from now on, and new chats too; its sign-in checked unless it was. */
    private fun choose(id: String) {
        val shown = page.current
        if (shown.opened && shown.session != null) {
            chats.choose(shown.workspace, shown.session, AssistantAgent(id))
            page.refresh(shown.workspace, shown.session)
        }
        runCatching { preferences.last = id }
        page.state { it.copy(agent = id) }
        if (id in installed() && id !in preferences.signedIn()) scope.launch { check(id) }
    }

    /** [id] started once to learn whether it asks to be signed in: its ways shown until it is. */
    private suspend fun check(id: String) {
        runCatching { agents.use(id) { agents.check(it, placeOf(page.current.workspace).guestPath) } }
            .onSuccess {
                runCatching { preferences.signedIn(id, true) }
                page.state { it.copy(signIns = it.signIns - id) }
            }
            .onFailure { failure ->
                if (failure is AcpAgents.SignInNeeded) page.state { it.copy(signIns = it.signIns + (id to failure.methods)) }
                else page.tell(AgentNotices.notStarted(name(id), failure.message ?: failure.javaClass.simpleName))
            }
    }

    private fun signIn(agent: String, method: String) {
        val way = page.current.signIns[agent]?.firstOrNull { it.id == method }?.way ?: return
        scope.launch {
            when (way) {
                AgentSignInWay.ByAgent -> runCatching { agents.authenticate(agent, method) }
                    .onSuccess { check(agent) }
                    .onFailure { page.tell(AgentNotices.notSignedIn(name(agent), it.message ?: it.javaClass.simpleName)) }
                is AgentSignInWay.InTerminal -> {
                    val command = installed()[agent]?.command ?: return@launch
                    val variables = way.env.entries.joinToString(" ") { (name, value) -> "$name=${shellQuote(value)}" }
                    val line = (if (variables.isEmpty()) "" else "env $variables ") + (command.take(1) + way.args).joinToString(" ", transform = ::shellQuote)
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
        ): (AssistantPage, AssistantChats, (String?) -> AssistantWorkspace) -> AgentChats {
            val layout = toolbox.execution.layout
            val receipts = AgentReceipts(layout.home)
            val preferences = AgentPreferences(context)
            val agents = AcpAgents(
                scope, GuestStdio(layout, GuestContext.Command, LOGS, tag = "agent"),
                login = { toolbox.execution.environment.of(GuestContext.Command).variables },
                installed = receipts::installed, keys = preferences::keys, version = BuildConfig.VERSION_NAME,
            )
            val saveOpen: suspend () -> Unit = {
                icode.mutations.withLock { icode.current.openDocuments.filter { it.dirty }.forEach { save(it.location) } }
            }
            return { page, chats, placeOf ->
                AgentChats(scope, page, chats, agents, preferences, { icode.current.agents.catalogue }, receipts::installed, placeOf, toolbox::runInTab, saveOpen)
            }
        }
    }
}
