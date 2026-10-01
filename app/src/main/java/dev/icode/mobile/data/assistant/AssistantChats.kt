package dev.icode.mobile.data.assistant

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantRules

/**
 * Each workspace's chat as last seen in this process (§4.6): read from [store] the first time,
 * saved after every change, so a killed process loses at most the reply in flight. "Allow All in
 * This Chat" lives here too, and lasts as long as the chat does in this process.
 */
internal class AssistantChats(private val store: AssistantChatStore) {
    private val lock = Any()
    private val loaded = mutableMapOf<String, List<AssistantMessage>>()
    private val allowingAll = mutableSetOf<String>()

    fun of(workspace: String): List<AssistantMessage> = synchronized(lock) { loaded.getOrPut(workspace) { store.load(workspace) } }

    fun append(workspace: String, message: AssistantMessage) =
        change(workspace) { it + message }

    /** Every call answered — what a turn leaves, however it ended. */
    fun close(workspace: String) = change(workspace, AssistantRules::closed)

    fun clear(workspace: String) {
        synchronized(lock) {
            loaded[workspace] = emptyList()
            allowingAll -= workspace
        }
        runCatching { store.forget(workspace) }
    }

    fun allowsAll(workspace: String): Boolean = synchronized(lock) { workspace in allowingAll }

    fun allowAll(workspace: String) {
        synchronized(lock) { allowingAll += workspace }
    }

    /** The workspace was renamed or moved: its chat follows it. */
    fun moved(from: String, to: String) {
        synchronized(lock) {
            loaded.remove(from)?.let { loaded[to] = it }
            if (allowingAll.remove(from)) allowingAll += to
        }
        runCatching { store.move(from, to) }.onFailure { warn("move", it) }
    }

    /** The workspace was deleted: its chat goes with it. */
    fun forgotten(workspace: String) {
        synchronized(lock) {
            loaded -= workspace
            allowingAll -= workspace
        }
        runCatching { store.forget(workspace) }
    }

    private fun change(workspace: String, edit: (List<AssistantMessage>) -> List<AssistantMessage>) {
        val messages = synchronized(lock) { edit(of(workspace)).also { loaded[workspace] = it } }
        runCatching { store.save(workspace, messages) }.onFailure { warn("save", it) }
    }

    /** Its type only: a chat's content never reaches the log. */
    private fun warn(what: String, failure: Throwable) = BackendLog.w(TAG, "Unable to $what a chat (${failure.javaClass.simpleName})")

    private companion object {
        const val TAG = "AssistantChats"
    }
}
