package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantAnswerer
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPermissions
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

    /** One call's result: with the others of its reply, which are right after it. */
    fun answer(workspace: String?, session: String, result: AssistantResult) = change(workspace, session) { chat ->
        val last = chat.messages.lastOrNull()
        chat.copy(
            messages = if (last is AssistantMessage.Results) {
                chat.messages.dropLast(1) + AssistantMessage.Results(last.results + result)
            } else {
                chat.messages + AssistantMessage.Results(listOf(result))
            },
        )
    }

    /** [session] answered by [answerer] from now on: saved with the chat, or with its first message. */
    fun choose(workspace: String?, session: String, answerer: AssistantAnswerer) = change(workspace, session) { it.copy(answerer = answerer) }

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

    /** Every call answered — what a turn leaves, however it ended. */
    fun close(workspace: String?, session: String) =
        change(workspace, session) { it.copy(messages = AssistantRules.closed(it.messages)) }

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
    private fun change(workspace: String?, session: String, edit: (AssistantChat) -> AssistantChat) = synchronized(lock) {
        val chat = edit(of(workspace, session)).also { loaded[Key(workspace, session)] = it }
        if (chat.messages.isNotEmpty()) runCatching { store.save(workspace, session, chat, clock()) }.onFailure { warn("save", it) }
    }

    /** Its type only: a chat's content never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what a chat (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChats"
    }
}
