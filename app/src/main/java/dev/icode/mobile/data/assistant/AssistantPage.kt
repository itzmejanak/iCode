package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.NoticeTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Assistant page shows (§12 R5): a workspace's chats and the one open, read from [chats].
 * Everything is read before the state is changed: no file is read inside an update.
 */
internal class AssistantPage(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val chats: AssistantChats,
) {
    val current: AssistantState get() = stateHolder.current.assistant

    /** [workspace]'s chats: the one open stays; else [running], the turn's chat there; else the newest. */
    fun show(workspace: String?, running: Pair<String, String>?) {
        if (workspace == null) return state { it.copy(workspace = null, sessions = emptyList(), session = null, messages = emptyList(), contextTokens = null) }
        val sessions = chats.sessions(workspace)
        val session = when {
            current.workspace == workspace -> current.session
            else -> running?.takeIf { it.first == workspace }?.second ?: sessions.firstOrNull()?.id
        }
        display(workspace, session, sessions)
    }

    /** [session] of [workspace] open; null for a new chat. */
    fun display(workspace: String, session: String?, sessions: List<AssistantSession> = chats.sessions(workspace)) {
        val chat = session?.let { chats.of(workspace, it) } ?: AssistantChat()
        val allowAll = session != null && chats.allowsAll(workspace, session)
        state {
            val same = it.workspace == workspace && it.session == session
            it.copy(
                workspace = workspace, sessions = sessions, session = session, messages = chat.messages,
                contextTokens = chat.contextTokens, allowAll = allowAll, failure = if (same) it.failure else null,
            )
        }
    }

    /**
     * [session] of [workspace] as it is now, when it is open. A reply just [recorded] replaces what
     * was shown of it as it was written, in the same change: never both on the page, nor neither.
     */
    fun refresh(workspace: String, session: String, recorded: Boolean = false) {
        val chat = chats.of(workspace, session)
        val allowAll = chats.allowsAll(workspace, session)
        state {
            val turn = if (recorded) it.turn?.copy(live = null) else it.turn
            if (it.workspace != workspace || it.session != session) it.copy(turn = turn)
            else it.copy(messages = chat.messages, contextTokens = chat.contextTokens, allowAll = allowAll, turn = turn)
        }
    }

    /** [workspace]'s list as saved now. */
    fun listed(workspace: String) {
        val sessions = chats.sessions(workspace)
        state { if (it.workspace == workspace) it.copy(sessions = sessions) else it }
    }

    /** [workspace] was deleted: nothing of it stays on the page. */
    fun forgotten(workspace: String) = state {
        if (it.workspace != workspace) it
        else it.copy(workspace = null, sessions = emptyList(), session = null, messages = emptyList(), contextTokens = null, allowAll = false)
    }

    fun state(change: (AssistantState) -> AssistantState) = stateHolder.update { it.copy(assistant = change(it.assistant)) }

    fun say(tone: NoticeTone, title: String, detail: String? = null) {
        scope.launch { stateHolder.notify(tone, title, detail) }
    }
}
