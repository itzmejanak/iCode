package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantExports
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import dev.icode.mobile.domain.AssistantUndo
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.notices.AssistantNotices
import dev.icode.mobile.domain.notices.NoticePlace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the user does to the chats of the page (docs/assistant-and-mcp.md): send the last message
 * again, take it back to edit, compact, delete or share a chat, and undo back to before one of its
 * messages — its place's files, the chat, or both.
 */
internal class AssistantChatActions(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val page: AssistantPage,
    private val chats: AssistantChats,
    private val turns: AssistantTurns,
    private val shared: AssistantShares,
    /** The places' checkpoints, which Undo puts files back from; none in tests that need none. */
    private val checkpoints: AssistantCheckpoints?,
    /** A chat's place: its workspace's folder, by its location, or the runtime's home for none. */
    private val placeOf: (String?) -> AssistantWorkspace,
    /** A chat deleted: what it left in the runtime goes, its terminal tabs. */
    private val forget: suspend (workspace: String?, chat: String) -> Unit,
) {
    fun retry() {
        val (workspace, session) = shown() ?: return
        if (chats.of(workspace, session).messages.lastOrNull() !is AssistantMessage.User) return page.tell(AssistantNotices.nothingToSendAgain())
        turns.retry(workspace, session)
    }

    /** The last message, unanswered, out of the chat for the user to edit; a chat left empty is a new one. */
    fun takeBack() {
        val (workspace, session) = shown() ?: return
        if (turns.runs(workspace, session)) return
        val taken = chats.takeBack(workspace, session) ?: return
        forgetCheckpoints(workspace, listOf(taken))
        shownAgain(workspace, session)
    }

    fun compact() {
        val (workspace, session) = shown() ?: return
        if (AssistantRules.forModel(chats.of(workspace, session).messages).messages.isEmpty()) return page.tell(AssistantNotices.nothingToCompact())
        turns.compact(workspace, session)
    }

    fun delete(session: String) {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        if (turns.runs(workspace, session)) return page.tell(AssistantNotices.stopFirst(NoticePlace(AssistantPlace.name(workspace), workspace)))
        forgetCheckpoints(workspace, chats.of(workspace, session).messages)
        chats.delete(workspace, session)
        scope.launch { forget(workspace, session) }
        if (page.current.session == session) page.display(workspace, null) else page.listed(workspace)
    }

    /** One chat, shared as its transcript: the user's words, the answers, the steps and their output. */
    fun export(session: String) {
        if (!page.current.opened) return
        val workspace = page.current.workspace
        val chat = chats.of(workspace, session)
        if (chat.messages.isEmpty()) return page.tell(AssistantNotices.nothingToShare())
        val listed = page.current.sessions.firstOrNull { it.id == session }
        val title = listed?.title ?: AssistantRules.sessionTitle(chat.messages.firstNotNullOfOrNull { it as? AssistantMessage.User }?.text.orEmpty())
        val text = AssistantExports.transcript(AssistantSession(session, title, listed?.updatedAt ?: 0L), AssistantPlace.name(workspace), chat.messages)
        scope.launch {
            runCatching { shared.write(title, text) }
                .onSuccess { stateHolder.emit(ICodeEffect.ShareChat(title, it.path)) }
                .onFailure { page.tell(AssistantNotices.notShared(it.javaClass.simpleName)) }
        }
    }

    /**
     * The chat shown back to before its message at [index]: its place's [files] — exactly, what came
     * since undone too — its [chat], or both. Files changed outside the chat's own work since are listed
     * and asked about first, unless [confirmed]; nothing is undone while a chat works in that place.
     */
    fun undo(index: Int, files: Boolean, chat: Boolean, confirmed: Boolean) {
        page.state { it.copy(undo = null) }
        val (workspace, session) = shown() ?: return
        if (page.current.turns.any { it.workspace == workspace }) return page.tell(AssistantNotices.undoWaits(NoticePlace(AssistantPlace.name(workspace), workspace)))
        val messages = chats.of(workspace, session).messages
        val message = messages.getOrNull(index) as? AssistantMessage.User ?: return
        if (files) {
            val id = message.checkpoint
            if (checkpoints == null || id == null) return page.tell(AssistantNotices.noCheckpoint())
            val place = placeOf(workspace)
            if (!confirmed) {
                val changed = checkpoints.outside(place, messages.drop(index).mapNotNull { (it as? AssistantMessage.User)?.checkpoint })
                if (changed.isNotEmpty()) return page.state { it.copy(undo = AssistantUndo(index, files, chat, changed.toList())) }
            }
            val failed = checkpoints.restore(place, id) ?: return page.tell(AssistantNotices.noCheckpoint())
            if (failed.isNotEmpty()) page.tell(AssistantNotices.notPutBack(failed))
        }
        if (chat) {
            forgetCheckpoints(workspace, chats.cutBack(workspace, session, index))
            shownAgain(workspace, session)
            // Back in the composer, to send again as it is or changed.
            page.state { it.copy(takenBack = message.text) }
        }
        page.tell(AssistantNotices.undone(files, chat))
    }

    /** The Undo asked about is not made. */
    fun dismissUndo() = page.state { it.copy(undo = null) }

    /** The chat shown: its workspace — null for none — and its session; null while none is. */
    private fun shown(): Pair<String?, String>? = page.current.takeIf { it.opened }?.let { shown -> shown.session?.let { shown.workspace to it } }

    /** The chat shown, as it is after a change at its end; a chat left empty is a new one. */
    private fun shownAgain(workspace: String?, session: String) {
        page.state { it.copy(failure = null) }
        if (chats.of(workspace, session).messages.isEmpty()) page.display(workspace, null) else page.refresh(workspace, session)
        page.listed(workspace)
    }

    /** [messages] gone from their chat: the checkpoints only they had go too. */
    private fun forgetCheckpoints(workspace: String?, messages: List<AssistantMessage>) {
        val owner = checkpoints ?: return
        val ids = messages.mapNotNull { (it as? AssistantMessage.User)?.checkpoint }
        if (ids.isNotEmpty()) scope.launch { owner.forget(workspace, ids) }
    }
}
