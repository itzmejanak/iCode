package dev.icode.mobile.data.assistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Which chats work now (docs/assistant-and-mcp.md): each its own job, as many chats at once as the user
 * starts, one job in a chat; and which of them has the phone's screen, one at a time
 * (docs/voice-and-phone-control.md). A chat is its workspace — null for none — and its session. Each
 * chat's work, once it has ended, is told to [ended]: whether it was the last, and whether the chat
 * goes on — not when it was stopped with its workspace, which is being moved or deleted.
 */
internal class AssistantWork(
    private val scope: CoroutineScope,
    private val ended: (chat: Pair<String?, String>, last: Boolean, goesOn: Boolean) -> Unit,
) {
    private val lock = Any()

    /** A chat's job is here from its start until it has completed, its end done or never begun: here, it works. */
    private val jobs = mutableMapOf<Pair<String?, String>, Job>()

    /** The chats stopped with their workspace, until their work has ended. */
    private val held = mutableSetOf<Pair<String?, String>>()

    /** The chat whose turn acts on the phone: the screen is its until that turn ends. */
    private var phone: Pair<String?, String>? = null

    fun runs(chat: Pair<String?, String>): Boolean = synchronized(lock) { chat in jobs }

    /**
     * [work] in [chat]'s own job; false while that chat works already. The job, not the page, decides:
     * a second Send in the same instant finds the first. It is listed before it starts and unlisted
     * once it completes — stopped before it began, too.
     */
    fun start(chat: Pair<String?, String>, work: suspend () -> Unit): Boolean = synchronized(lock) {
        if (chat in jobs) return false
        val job = scope.launch(start = CoroutineStart.LAZY) { work() }
        jobs[chat] = job
        job.invokeOnCompletion { completed(chat) }
        job.start()
        true
    }

    private fun completed(chat: Pair<String?, String>) {
        val (last, goesOn) = synchronized(lock) {
            jobs -= chat
            if (phone == chat) phone = null
            jobs.isEmpty() to !held.remove(chat)
        }
        ended(chat, last, goesOn)
    }

    fun stop(chat: Pair<String?, String>) {
        synchronized(lock) { jobs[chat] }?.cancel()
    }

    /** Stops every chat working in [workspace], and waits until each is saved; none goes on. */
    suspend fun stopIn(workspace: String) {
        synchronized(lock) { jobs.filterKeys { it.first == workspace }.also { held += it.keys }.values.toList() }.onEach { it.cancel() }.joinAll()
    }

    /**
     * [chat] takes the phone's screen for a phone tool; refused while another chat's turn has it.
     * True when it has just taken it.
     */
    fun takePhone(chat: Pair<String?, String>): Boolean = synchronized(lock) {
        when (phone) {
            chat -> false
            null -> true.also { phone = chat }
            else -> throw AssistantToolException(PHONE_TAKEN)
        }
    }

    private companion object {
        const val PHONE_TAKEN = "Another chat is using the phone's screen now. Try again when it is done, or tell the user."
    }
}
