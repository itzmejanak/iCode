package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.notices.AssistantNotices
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticeRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the Assistant page shows (§12 R5): a place's chats and the one open, read from [chats].
 * Everything is read before the state is changed: no file is read inside an update.
 */
internal class AssistantPage(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val chats: AssistantChats,
) {
    val current: AssistantState get() = stateHolder.current.assistant

    /**
     * [workspace]'s chats — the chats in no workspace when null — the one open stays; else [running],
     * the turn's chat there; else the newest.
     */
    fun show(workspace: String?, running: Pair<String?, String>?) {
        val sessions = chats.sessions(workspace)
        val session = when {
            current.opened && current.workspace == workspace -> current.session
            else -> running?.takeIf { it.first == workspace }?.second ?: sessions.firstOrNull()?.id
        }
        display(workspace, session, sessions)
    }

    /** [session] of [workspace] open; null for a new chat. */
    fun display(workspace: String?, session: String?, sessions: List<AssistantSession> = chats.sessions(workspace)) {
        val chat = session?.let { chats.of(workspace, it) } ?: AssistantChat()
        val allowed = session != null && chats.allowedInWorkspace(workspace, session)
        state {
            val same = it.opened && it.workspace == workspace && it.session == session
            it.copy(
                opened = true, workspace = workspace, sessions = sessions, session = session, messages = chat.messages,
                contextTokens = chat.contextTokens, allowedInWorkspace = allowed, failure = if (same) it.failure else null,
                tab = if (same) it.tab else null,
            )
        }
    }

    /**
     * [session] of [workspace] as it is now, when it is open. A reply just [recorded] replaces what
     * was shown of it as it was written, in the same change: never both on the page, nor neither.
     */
    fun refresh(workspace: String?, session: String, recorded: Boolean = false) {
        val chat = chats.of(workspace, session)
        val allowed = chats.allowedInWorkspace(workspace, session)
        state {
            val turn = if (recorded) it.turn?.copy(live = null) else it.turn
            if (!it.opened || it.workspace != workspace || it.session != session) it.copy(turn = turn)
            else it.copy(messages = chat.messages, contextTokens = chat.contextTokens, allowedInWorkspace = allowed, turn = turn)
        }
    }

    /** [workspace]'s list as saved now. */
    fun listed(workspace: String?) {
        val sessions = chats.sessions(workspace)
        state { if (it.opened && it.workspace == workspace) it.copy(sessions = sessions) else it }
    }

    /** [workspace] was deleted: nothing of it stays on the page. */
    fun forgotten(workspace: String) = state {
        if (it.workspace != workspace) it
        else it.copy(opened = false, workspace = null, sessions = emptyList(), session = null, messages = emptyList(), contextTokens = null, allowedInWorkspace = false)
    }

    fun state(change: (AssistantState) -> AssistantState) = stateHolder.update { it.copy(assistant = change(it.assistant)) }

    fun tell(notice: Notice) {
        scope.launch { stateHolder.tell(notice) }
    }

    /**
     * [session]'s turn in [workspace] ended (docs/notices-redesign-plan.md D9): its [failure] told
     * when its chat is not the one [shown]; one that ran long, unless [stopped], kept and posted
     * should iCode be out of view by then. Where it is: the workspace, and the chat by its title.
     */
    fun ended(workspace: String?, session: String, failure: String?, stopped: Boolean, began: Long, shown: Boolean) {
        val chat = NoticePlace(AssistantPlace.name(workspace), workspace, subject = chats.sessions(workspace).firstOrNull { it.id == session }?.title)
        when {
            failure != null -> if (!shown) tell(AssistantNotices.stopped(failure, chat))
            !stopped && System.currentTimeMillis() - began >= NoticeRules.LONG_WORK_MILLIS -> tell(AssistantNotices.finished(chat))
        }
    }
}
