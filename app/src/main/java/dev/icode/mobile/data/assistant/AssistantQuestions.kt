package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantReach
import kotlinx.coroutines.CompletableDeferred

/**
 * What the turn in hand asks the user, one question at a time: an approval before a call
 * (docs/assistant-permissions-plan.md §5), or a pause between steps (docs/phone-agent-plan.md L5).
 * Each waits until it is answered or the turn is stopped, shown on the [page] meanwhile.
 */
internal class AssistantQuestions(private val page: AssistantPage) {
    private val lock = Any()
    private var approval: CompletableDeferred<AssistantAnswer>? = null
    private var pause: CompletableDeferred<Unit>? = null

    /** [call] waits for the user's answer; [detail] and [reach] are what its card shows. */
    suspend fun approve(call: AssistantCall, detail: String, reach: AssistantReach?, always: Boolean = false): AssistantAnswer {
        val answer = CompletableDeferred<AssistantAnswer>()
        synchronized(lock) { approval = answer }
        page.state { it.copy(approval = AssistantApproval(call, detail, reach, always), turn = it.turn?.copy(step = WAITING)) }
        try {
            return answer.await()
        } finally {
            synchronized(lock) { approval = null }
            page.state { it.copy(approval = null, turn = it.turn?.copy(step = call.summary)) }
        }
    }

    /** The turn waits between steps until the user goes on; then its step reads [then]. */
    suspend fun pause(question: AssistantPause, then: String) {
        val waiting = CompletableDeferred<Unit>()
        synchronized(lock) { pause = waiting }
        page.state { it.copy(pause = question, turn = it.turn?.copy(step = WAITING)) }
        try {
            waiting.await()
        } finally {
            synchronized(lock) { pause = null }
            page.state { it.copy(pause = null, turn = it.turn?.copy(step = then)) }
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

    /** The turn ended: nothing is asked any more. */
    fun clear() = synchronized(lock) {
        approval = null
        pause = null
    }

    private companion object {
        const val WAITING = "Waiting for your answer"
    }
}
