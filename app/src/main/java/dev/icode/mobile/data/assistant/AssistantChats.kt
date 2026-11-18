package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantDraftFile
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantQueued
import dev.icode.mobile.domain.AssistantResult
import dev.icode.mobile.domain.AssistantRules
import dev.icode.mobile.domain.AssistantSession
import java.util.UUID

/**
 * The chats as last seen in this process: each read from [store] the first time,
 * saved after every change, so a killed process loses at most the reply in flight. "Allow All in
 * This Chat" lives here too, and lasts as long as the chat does in this process.
 */
internal class AssistantChats(
    private val store: AssistantChatStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Key(val workspace: String?, val session: String)

    private val lock = Any()
    private val loaded = mutableMapOf<Key, AssistantChat>()
    /** What each chat's answers allowed beyond the setting (docs/assistant-and-mcp.md): until iCode closes. */
    private class Answers(var inWorkspace: Boolean = false, val roots: MutableSet<String> = linkedSetOf())

    private val answers = mutableMapOf<Key, Answers>()

    /** [workspace]'s chats, newest first; none when they cannot be read. */
    fun sessions(workspace: String?): List<AssistantSession> =
        runCatching { store.sessions(workspace) }.onFailure { warn("list", it) }.getOrDefault(emptyList())

    /** Every agent's session [workspace]'s chats keep: an agent's conversations no chat has are the others. */
    fun agentSessions(workspace: String?): Set<String> =
        runCatching { store.agentSessions(workspace) }.onFailure { warn("list", it) }.getOrDefault(emptySet())

    /** An ID for a new chat: it is saved, and listed, with its first message. */
    fun newSession(): String = UUID.randomUUID().toString()

    fun of(workspace: String?, session: String): AssistantChat =
        synchronized(lock) { loaded.getOrPut(Key(workspace, session)) { store.load(workspace, session) } }

    fun append(workspace: String?, session: String, message: AssistantMessage) =
        change(workspace, session) { it.copy(messages = it.messages + message) }

    /** Keeps an image's or a PDF's bytes in [session]'s folder; returns where. */
    fun keep(workspace: String?, session: String, bytes: ByteArray, extension: String): String =
        store.keep(workspace, session, bytes, extension)

    /**
     * Takes back [session]'s last message when nothing answered it — a turn that failed before any
     * reply — to send again; null when there is none.
     */
    fun withdraw(workspace: String?, session: String): AssistantMessage.User? = synchronized(lock) {
        val chat = of(workspace, session)
        val last = chat.messages.lastOrNull() as? AssistantMessage.User ?: return@synchronized null
        loaded[Key(workspace, session)] = chat.copy(messages = chat.messages.dropLast(1))
        last
    }

    /**
     * Takes back [session]'s last message, unanswered and without files, for the user to edit, as the
     * chat is kept: a chat left empty goes, as an empty one is never kept. Null when there is none.
     */
    fun takeBack(workspace: String?, session: String): AssistantMessage.User? = synchronized(lock) {
        val last = of(workspace, session).messages.lastOrNull() as? AssistantMessage.User
        if (last == null || last.attachments.isNotEmpty()) return@synchronized null
        withdraw(workspace, session)
        if (of(workspace, session).messages.isEmpty()) delete(workspace, session) else change(workspace, session) { it }
        last
    }

    /**
     * [session] back to before its message at [index]: that message and all after them go, and are
     * returned; the meter starts again, and a chat left empty goes.
     */
    fun cutBack(workspace: String?, session: String, index: Int): List<AssistantMessage> = synchronized(lock) {
        val messages = of(workspace, session).messages
        if (index !in messages.indices) return@synchronized emptyList()
        loaded[Key(workspace, session)] = of(workspace, session).copy(messages = messages.take(index), contextTokens = null)
        if (index == 0) delete(workspace, session) else change(workspace, session) { it }
        messages.drop(index)
    }

    /** One call's result: with the others of its reply, which are right after it. */
    fun answer(workspace: String?, session: String, result: AssistantResult) = change(workspace, session) { it.copy(messages = AssistantRules.answered(it.messages, result)) }

    /** [session] begins with [messages] said elsewhere: an agent's conversation, taken up here. */
    fun begin(workspace: String?, session: String, messages: List<AssistantMessage>) = change(workspace, session) { it.copy(messages = messages) }

    /** [session] answered by [answerer] from now on: saved with the chat, or with its first message. */
    fun choose(workspace: String?, session: String, answerer: AssistantAnswerer) = change(workspace, session) { it.copy(answerer = answerer) }

    /** A message waits at the end of [session]'s queue while it works; saved with the chat (docs/assistant-and-mcp.md). */
    fun enqueue(workspace: String?, session: String, text: String, files: List<AssistantDraftFile>, aloud: Boolean) =
        change(workspace, session) { it.copy(queued = it.queued + AssistantQueued(UUID.randomUUID().toString(), text, files, aloud)) }

    /** [id] out of [session]'s queue — the first when null — or null when it is not there. */
    fun unqueue(workspace: String?, session: String, id: String? = null): AssistantQueued? = synchronized(lock) {
        val queue = of(workspace, session).queued
        val taken = (if (id == null) queue.firstOrNull() else queue.firstOrNull { it.id == id }) ?: return@synchronized null
        change(workspace, session) { it.copy(queued = queue - taken) }
        taken
    }

    /** [id] first in [session]'s queue, the next to go; false when it is not there. */
    fun toFront(workspace: String?, session: String, id: String): Boolean = synchronized(lock) {
        val queue = of(workspace, session).queued
        val moved = queue.firstOrNull { it.id == id } ?: return@synchronized false
        change(workspace, session) { it.copy(queued = listOf(moved) + (queue - moved)) }
        true
    }

    /** The tokens the last request used; saved with the reply that follows them. */
    fun used(workspace: String?, session: String, tokens: Long?) {
        synchronized(lock) { loaded[Key(workspace, session)] = of(workspace, session).copy(contextTokens = tokens) }
    }

    /**
     * [summary] marks where the model's view of the chat starts again: after what it covers
     * — before a message still waiting for its answer, which is not part of it — with the meter reset.
     */
    fun compacted(workspace: String?, session: String, summary: String) = change(workspace, session) { chat ->
        val marker = AssistantMessage.Compacted(summary)
        val waiting = chat.messages.lastOrNull() as? AssistantMessage.User
        chat.copy(
            messages = if (waiting != null) chat.messages.dropLast(1) + marker + waiting else chat.messages + marker,
            contextTokens = null,
        )
    }

    /** A turn begins in it: so it is saved, with its first message for a new chat, until the turn ends. */
    fun working(workspace: String?, session: String) = change(workspace, session) { it.copy(working = true) }

    /** Every call answered — what a turn leaves, however it ended — and no turn working in it. */
    fun close(workspace: String?, session: String) =
        change(workspace, session) { it.copy(messages = AssistantRules.closed(it.messages), working = false) }

    /** [agent]'s session for the chat is [id]: taken up again once the agent has stopped. Opening a chat does not make it newer. */
    fun agentSession(workspace: String?, session: String, agent: String, id: String) {
        if (of(workspace, session).agentSessions[agent] != id) change(workspace, session, touch = false) { it.copy(agentSessions = it.agentSessions + (agent to id)) }
    }

    /** The agents' sessions for the chat no longer follow it: the next starts afresh from the chat as it stands. Those it had. */
    fun dropAgentSessions(workspace: String?, session: String): Map<String, String> = synchronized(lock) {
        of(workspace, session).agentSessions.also { kept ->
            if (kept.isNotEmpty()) change(workspace, session, touch = false) { it.copy(agentSessions = emptyMap()) }
        }
    }

    fun delete(workspace: String?, session: String) {
        synchronized(lock) {
            loaded -= Key(workspace, session)
            answers -= Key(workspace, session)
        }
        runCatching { store.delete(workspace, session) }.onFailure { warn("delete", it) }
    }

    /** The chat answered "Allow in" its workspace: it runs as Auto in workspace ([AssistantPermissions.effective]). */
    fun allowedInWorkspace(workspace: String?, session: String): Boolean = synchronized(lock) { answers[Key(workspace, session)]?.inWorkspace == true }

    fun allowInWorkspace(workspace: String?, session: String) {
        synchronized(lock) { answers.getOrPut(Key(workspace, session)) { Answers() }.inWorkspace = true }
    }

    /** The runtime folders outside its workspace this chat may reach without asking. */
    fun roots(workspace: String?, session: String): Set<String> = synchronized(lock) { answers[Key(workspace, session)]?.roots?.toSet().orEmpty() }

    fun open(workspace: String?, session: String, roots: List<String>) {
        synchronized(lock) { answers.getOrPut(Key(workspace, session)) { Answers() }.roots += roots }
    }

    /** A mode chosen for the chat replaces its answers. */
    fun forgetAnswers(workspace: String?, session: String) {
        synchronized(lock) { answers -= Key(workspace, session) }
    }

    /** The workspace was renamed or moved: its chats follow it. */
    fun moved(from: String, to: String) {
        synchronized(lock) {
            loaded.keys.filter { it.workspace == from }.forEach { key -> loaded.remove(key)?.let { loaded[key.copy(workspace = to)] = it } }
            answers.keys.filter { it.workspace == from }.forEach { key -> answers.remove(key)?.let { answers[key.copy(workspace = to)] = it } }
        }
        runCatching { store.move(from, to) }.onFailure { warn("move", it) }
    }

    /** The workspace was deleted: its chats go with it. */
    fun forgotten(workspace: String) {
        synchronized(lock) {
            loaded.keys.removeAll { it.workspace == workspace }
            answers.keys.removeAll { it.workspace == workspace }
        }
        runCatching { store.forget(workspace) }.onFailure { warn("delete", it) }
    }

    /**
     * Saved under the lock, so two changes never reach the file in the other order; a chat with
     * nothing in it is never saved, so it is never listed.
     */
    private fun change(workspace: String?, session: String, touch: Boolean = true, edit: (AssistantChat) -> AssistantChat) = synchronized(lock) {
        val chat = edit(of(workspace, session)).also { loaded[Key(workspace, session)] = it }
        // Not [touch]ed, it keeps its place in the list: saved as of when it last changed.
        val now = if (touch) clock() else store.sessions(workspace).firstOrNull { it.id == session }?.updatedAt ?: clock()
        if (chat.messages.isNotEmpty()) runCatching { store.save(workspace, session, chat, now) }.onFailure { warn("save", it) }
    }

    /** Its type only: a chat's content never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what a chat (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChats"
    }
}
