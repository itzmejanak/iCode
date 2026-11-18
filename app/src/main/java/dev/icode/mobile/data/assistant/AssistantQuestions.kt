package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantAnswer
import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantClarification
import dev.icode.mobile.domain.AssistantCode
import dev.icode.mobile.domain.AssistantOption
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantQuestion
import dev.icode.mobile.domain.AssistantReach
import dev.icode.mobile.domain.AssistantReply
import dev.icode.mobile.domain.AssistantTurn
import kotlinx.coroutines.CompletableDeferred

/**
 * What each working chat asks the user, one question at a time in a chat: an approval before a call
 * (docs/assistant-and-mcp.md), a pause between steps (docs/voice-and-phone-control.md), or the model's
 * own questions (docs/assistant-and-mcp.md). Each waits until it is answered or its turn is stopped,
 * shown on its turn meanwhile. A chat is its workspace — null for none — and its session.
 */
internal class AssistantQuestions(private val page: AssistantPage) {
    private val lock = Any()
    private val waiting = mutableMapOf<Pair<String?, String>, Waiting>()

    /** The answer a chat's question waits for, of the kind asked. */
    private class Waiting {
        var approval: CompletableDeferred<AssistantAnswer>? = null
        var option: CompletableDeferred<String?>? = null
        var pause: CompletableDeferred<Unit>? = null
        var clarification: CompletableDeferred<AssistantReply>? = null
    }

    /** [call] waits for the user's answer; [detail], [code] and [reach] are what its card and page show. */
    suspend fun approve(
        workspace: String?,
        session: String,
        call: AssistantCall,
        detail: String,
        code: List<AssistantCode>,
        reach: AssistantReach?,
        always: Boolean = false,
    ): AssistantAnswer = wait(
        workspace, session,
        hold = { approval = it },
        show = { it.copy(approval = AssistantApproval(call, detail, code, reach, always)) },
        hide = { it.copy(approval = null) },
        then = call.summary,
    )

    /**
     * An agent's [call] asks the user's permission with the agent's own [options]: the one chosen, or
     * null for none. It asks each time: the agent, not iCode, keeps what is allowed from now on.
     */
    suspend fun choose(workspace: String?, session: String, call: AssistantCall, options: List<AssistantOption>): String? = wait(
        workspace, session,
        hold = { option = it },
        show = { it.copy(approval = AssistantApproval(call, call.summary, always = true, options = options)) },
        hide = { it.copy(approval = null) },
        then = call.summary,
    )

    /** The turn waits between steps until the user goes on; then its step reads [then]. */
    suspend fun pause(workspace: String?, session: String, question: AssistantPause, then: String): Unit =
        wait(workspace, session, hold = { pause = it }, show = { it.copy(pause = question) }, hide = { it.copy(pause = null) }, then = then)

    /** [call] asks the user [questions] and waits for the reply. */
    suspend fun clarify(workspace: String?, session: String, call: AssistantCall, questions: List<AssistantQuestion>): AssistantReply =
        wait(
            workspace, session,
            hold = { clarification = it },
            show = { it.copy(clarification = AssistantClarification(call, questions)) },
            hide = { it.copy(clarification = null) },
            then = call.summary,
        )

    /**
     * The one way a turn waits for the user: [hold] keeps the answer to come (null once it came, or the
     * turn stopped), [show] puts the question on the turn and [hide] takes it off; then the step reads
     * [then].
     */
    private suspend fun <T> wait(
        workspace: String?,
        session: String,
        hold: Waiting.(CompletableDeferred<T>?) -> Unit,
        show: (AssistantTurn) -> AssistantTurn,
        hide: (AssistantTurn) -> AssistantTurn,
        then: String,
    ): T {
        val chat = workspace to session
        val answer = CompletableDeferred<T>()
        synchronized(lock) { waiting.getOrPut(chat, ::Waiting).hold(answer) }
        page.state { state -> state.changeTurn(workspace, session) { show(it).copy(step = WAITING) } }
        try {
            return answer.await()
        } finally {
            synchronized(lock) { waiting[chat]?.hold(null) }
            page.state { state -> state.changeTurn(workspace, session) { hide(it).copy(step = then) } }
        }
    }

    /**
     * The user's [answer] to the approval of [callId] in [session] of [workspace], if it is the one asked.
     * An agent's is answered with its own [option] chosen, or with the one [answer] stands for.
     */
    fun answer(workspace: String?, session: String, callId: String, answer: AssistantAnswer, option: String? = null) {
        val approval = page.current.turnOf(workspace, session)?.approval?.takeIf { it.call.id == callId } ?: return
        synchronized(lock) {
            val waits = waiting[workspace to session] ?: return
            waits.option?.complete(option ?: approval.optionFor(answer)) ?: waits.approval?.complete(answer)
        }
    }

    /** The pause in [session] of [workspace] answered Continue (or Done): its turn goes on. */
    fun resume(workspace: String?, session: String) {
        synchronized(lock) { waiting[workspace to session]?.pause }?.complete(Unit)
    }

    /** The user's [reply] to the questions of [callId] in [session] of [workspace], if they are the ones asked. */
    fun reply(workspace: String?, session: String, callId: String, reply: AssistantReply) {
        if (page.current.turnOf(workspace, session)?.clarification?.call?.id != callId) return
        synchronized(lock) { waiting[workspace to session]?.clarification }?.complete(reply)
    }

    /**
     * A message sent in [session] of [workspace] while its turn asks its questions: it is the reply.
     * False when no question waits there — the message is a message.
     */
    fun replyWith(workspace: String?, session: String, text: String): Boolean {
        val asked = page.current.turnOf(workspace, session)?.clarification ?: return false
        reply(workspace, session, asked.call.id, AssistantReply.Said(text))
        return true
    }

    /** [session]'s turn in [workspace] ended: nothing is asked there any more. */
    fun clear(workspace: String?, session: String) {
        synchronized(lock) { waiting -= workspace to session }
    }

    private companion object {
        const val WAITING = "Waiting for your answer"
    }
}
