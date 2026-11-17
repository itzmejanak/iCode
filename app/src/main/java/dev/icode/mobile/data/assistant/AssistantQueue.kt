package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantDraftFile
import dev.icode.mobile.domain.AssistantQueued
import java.util.concurrent.ConcurrentHashMap

/**
 * Each chat's waiting messages (docs/assistant-and-mcp.md), saved with it. A message sent while its
 * chat works waits here; when the work ends, however it ends, the first goes — not when the chat was
 * stopped with its workspace, which is being moved or deleted. Send now puts one first: the work in
 * hand takes it after its step, or stops for it when another answerer is to answer it. A chat is its
 * workspace — null for none — and its session; the user's actions are on the chat shown.
 */
internal class AssistantQueue(
    private val chats: AssistantChats,
    private val page: AssistantPage,
    /** The chats' turns, made with this queue: whether a chat works, its Stop, and a message's start. */
    private val turns: () -> AssistantTurns,
) {
    /** Held while the queue is read and a turn started from it: a message is never sent twice, nor left behind. */
    private val lock = Any()

    /** The chats whose work in hand takes a waiting message after its step, by its ID: Send now. */
    private val steering = ConcurrentHashMap<Pair<String?, String>, String>()

    /** A message for [session] of [workspace]: it goes now, or waits at the end of the queue while the chat works. */
    fun send(workspace: String?, session: String, text: String, files: List<AssistantDraftFile>, aloud: Boolean) = synchronized(lock) {
        if (!turns().runs(workspace, session)) return@synchronized turns().send(workspace, session, text, files.map { it.location }, aloud)
        chats.enqueue(workspace, session, text, files, aloud)
        page.refresh(workspace, session)
    }

    /** The shown chat's waiting message [id] goes now: on its own, steering the work in hand, or in its place for another answerer. */
    fun sendNow(id: String) {
        val shown = page.current
        val session = shown.session?.takeIf { shown.opened } ?: return
        val chat = shown.workspace to session
        if (!chats.toFront(shown.workspace, session, id)) return
        page.refresh(shown.workspace, session)
        val by = shown.turnOf(shown.workspace, session)?.by
        synchronized(lock) {
            when {
                !turns().runs(shown.workspace, session) -> next(chat)
                by == null || by == shown.choice -> steering.put(chat, id)
                else -> turns().stop(shown.workspace, session)
            }
        }
    }

    /** The shown chat's waiting message [id] out of its queue: removed, or taken back to edit. */
    fun remove(id: String) {
        val shown = page.current
        val session = shown.session?.takeIf { shown.opened } ?: return
        chats.unqueue(shown.workspace, session, id) ?: return
        page.refresh(shown.workspace, session)
    }

    /** Send now asked [chat]'s work in hand for one of its waiting messages. */
    fun steering(chat: Pair<String?, String>): Boolean = steering.containsKey(chat)

    /** That message, out of the queue for the work in hand to go on with; null when none was asked for, or it was removed since. */
    fun steered(chat: Pair<String?, String>): AssistantQueued? = synchronized(lock) {
        val id = steering.remove(chat) ?: return@synchronized null
        chats.unqueue(chat.first, chat.second, id)?.also { page.refresh(chat.first, chat.second) }
    }

    /** [chat]'s work ended, however it ended: its first waiting message goes, unless the chat does not go on. */
    fun ended(chat: Pair<String?, String>, goesOn: Boolean) = synchronized(lock) {
        steering -= chat
        if (goesOn) next(chat)
    }

    /** [chat]'s first waiting message goes, unless the chat works. */
    fun next(chat: Pair<String?, String>) = synchronized(lock) {
        if (turns().runs(chat.first, chat.second)) return@synchronized
        val queued = chats.unqueue(chat.first, chat.second) ?: return@synchronized
        page.refresh(chat.first, chat.second)
        turns().send(chat.first, chat.second, queued.text, queued.files.map { it.location }, queued.aloud)
    }
}
