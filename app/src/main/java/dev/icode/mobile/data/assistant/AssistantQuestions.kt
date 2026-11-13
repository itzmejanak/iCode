package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantClarification
import dev.icode.mobile.domain.AssistantCode
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantReply
import dev.icode.mobile.domain.AssistantState
import kotlinx.coroutines.CompletableDeferred

/**
 * What the turn in hand asks the user, one question at a time: an approval before a call
 * (docs/assistant-permissions-plan.md §5), a pause between steps (docs/phone-agent-plan.md L5), or
 * the model's own questions (docs/assistant-questions-plan.md D2). Each waits until it is answered
 * or the turn is stopped, shown on the [page] meanwhile.
 */
internal class AssistantQuestions(private val page: AssistantPage) {
    private val lock = Any()
    private var approval: CompletableDeferred<AssistantAnswer>? = null
    private var pause: CompletableDeferred<Unit>? = null
    private var clarification: CompletableDeferred<AssistantReply>? = null

    /** [call] waits for the user's answer; [detail], [code] and [reach] are what its card and page show. */
    suspend fun approve(call: AssistantCall, detail: String, code: List<AssistantCode>, reach: AssistantReach?, always: Boolean = false): AssistantAnswer =
        wait(
            hold = { approval = it },
            show = { it.copy(approval = AssistantApproval(call, detail, code, reach, always)) },
            hide = { it.copy(approval = null) },
            then = call.summary,
        )

    /** The turn waits between steps until the user goes on; then its step reads [then]. */
    suspend fun pause(question: AssistantPause, then: String): Unit =
        wait(hold = { pause = it }, show = { it.copy(pause = question) }, hide = { it.copy(pause = null) }, then = then)

    /** [call] asks the user [questions] and waits for the reply. */
    suspend fun clarify(call: AssistantCall, questions: List<AssistantQuestion>): AssistantReply =
        wait(
            hold = { clarification = it },
            show = { it.copy(clarification = AssistantClarification(call, questions)) },
            hide = { it.copy(clarification = null) },
            then = call.summary,
        )

    /**
     * The one way the turn waits for the user: [hold] keeps the answer to come (null once it came, or
     * the turn stopped), [show] puts the question on the page and [hide] takes it off; then the step
     * reads [then].
     */
    private suspend fun <T> wait(
        hold: (CompletableDeferred<T>?) -> Unit,
        show: (AssistantState) -> AssistantState,
        hide: (AssistantState) -> AssistantState,
        then: String,
    ): T {
        val answer = CompletableDeferred<T>()
        synchronized(lock) { hold(answer) }
        page.state { show(it).copy(turn = it.turn?.copy(step = WAITING)) }
        try {
            return answer.await()
        } finally {
            synchronized(lock) { hold(null) }
            page.state { hide(it).copy(turn = it.turn?.copy(step = then)) }
        }
    }

    /** The user's [answer] to the approval of [callId], if it is the one asked. */
    fun answer(callId: String, answer: AssistantAnswer) {
        if (page.current.approval?.call?.id != callId) return
        synchronized(lock) { approval }?.complete(answer)
    }

    /** The pause answered Continue (or Done): the turn goes on. */
    fun resume() {
        synchronized(lock) { pause }?.complete(Unit)
    }

    /** The user's [reply] to the questions of [callId], if they are the ones asked. */
    fun reply(callId: String, reply: AssistantReply) {
        if (page.current.clarification?.call?.id != callId) return
        synchronized(lock) { clarification }?.complete(reply)
    }

    /**
     * A message sent in the chat of [workspace] and [session] while the turn there asks its questions:
     * it is the reply (D5). False when no question waits there — the message is a message.
     */
    fun replyWith(workspace: String?, session: String, text: String): Boolean {
        val shown = page.current
        val asked = shown.clarification?.takeIf { shown.turn?.let { it.workspace == workspace && it.session == session } == true } ?: return false
        reply(asked.call.id, AssistantReply.Said(text))
        return true
    }

    /** The turn ended: nothing is asked any more. */
    fun clear() = synchronized(lock) {
        approval = null
        pause = null
        clarification = null
    }

    private companion object {
        const val WAITING = "Waiting for your answer"
    }
}
