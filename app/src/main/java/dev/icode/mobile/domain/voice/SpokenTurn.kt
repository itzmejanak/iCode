package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantPlace
import dev.icode.mobile.domain.AssistantQuestionRules
import dev.icode.mobile.domain.AssistantState
import kotlin.math.min

/** What a change in the assistant asks of the voice. */
sealed interface VoiceCue {
    /** A turn to be read aloud began: what was being said stops, and the voice gets ready while the model thinks. */
    data object Begin : VoiceCue

    /** Said after what already waits to be said. */
    data class Say(val text: String) : VoiceCue

    /** The turn is over: what waits is said, then the voice rests. */
    data object End : VoiceCue

    /** Silence now: a turn not read aloud began, or the one read was stopped or failed. */
    data object Hush : VoiceCue
}

/**
 * Reads one turn aloud as it is written (docs/phone-agent-plan.md §5.8): each sentence of its replies as
 * soon as it is whole — while the model still writes the next — and each question it asks, in order.
 * Ended, what waits is said; stopped or failed, the voice hushes. A turn is read when its message was
 * sent to be answered aloud ([dev.icode.mobile.domain.AssistantTurn.aloud]) and Spoken replies is not Off.
 */
class SpokenTurn {
    /** The workspace and chat of the turn read now; null when none is. */
    private var reading: Pair<String?, String>? = null

    /** The chat's length when the turn began: its replies come after it, and after its own message. */
    private var base = 0

    /** Of the turn's replies, how many were read whole, and how much of the next one so far. */
    private var replies = 0
    private var taken = 0

    fun next(before: AssistantState, after: AssistantState, spoken: SpokenReplies): List<VoiceCue> {
        val turn = after.turn
        if (before.turn == null && turn != null) {
            reading = (turn.workspace to turn.session).takeIf { turn.aloud && spoken != SpokenReplies.Off }
            base = after.messages.size
            replies = 0
            taken = 0
            return if (reading == null) listOf(VoiceCue.Hush) else listOf(VoiceCue.Begin) + said(after)
        }
        val place = reading ?: return emptyList()
        if (spoken == SpokenReplies.Off) return listOf(VoiceCue.Hush).also { reading = null }
        // Read only while its chat is shown: the messages on the page are then this turn's.
        val shown = after.workspace == place.first && after.session == place.second
        val cues = if (shown) said(after) + asked(before, after) else emptyList()
        if (before.turn == null || turn != null) return cues
        reading = null
        val last = after.messages.lastOrNull()
        val finished = after.failure == null && last is AssistantMessage.Reply && last.calls.isEmpty()
        return cues + if (finished || !shown) VoiceCue.End else VoiceCue.Hush
    }

    /** The sentences of the turn's replies not yet read: the recorded ones whole, the one being written as far as it is whole. */
    private fun said(after: AssistantState): List<VoiceCue> {
        val messages = after.messages
        val asked = (base until messages.size).lastOrNull { messages[it] is AssistantMessage.User }
        val recorded = messages.drop(asked?.plus(1) ?: base).filterIsInstance<AssistantMessage.Reply>()
        val cues = mutableListOf<VoiceCue>()
        while (replies < recorded.size) {
            cues += read(recorded[replies].text, final = true)
            replies++
            taken = 0
        }
        after.turn?.live?.text?.let { cues += read(it, final = false) }
        return cues
    }

    private fun read(text: String, final: Boolean): List<VoiceCue> {
        val ready = SpokenText.ready(text.substring(min(taken, text.length)), final)
        taken += ready.taken
        return ready.sentences.map { VoiceCue.Say(it) }
    }

    /** A question the turn asks now: an approval, the model's questions — the first, with its options — or a pause. */
    private fun asked(before: AssistantState, after: AssistantState): List<VoiceCue> {
        val approval = after.approval
        if (approval != null && approval.call.id != before.approval?.call?.id) {
            val workspace = if (after.turn != null) after.turn.workspace else after.workspace
            return listOf(VoiceCue.Say("${SpokenText.of(AssistantPermissions.question(approval, AssistantPlace.name(workspace)))} ${SpokenText.of(approval.call.summary)}"))
        }
        val clarification = after.clarification
        if (clarification != null && clarification.call.id != before.clarification?.call?.id) {
            return listOf(VoiceCue.Say(SpokenText.of(AssistantQuestionRules.spoken(clarification))))
        }
        val pause = after.pause ?: return emptyList()
        if (before.pause != null) return emptyList()
        return listOf(VoiceCue.Say("${SpokenText.of(pause.kind.question)} ${SpokenText.of(pause.detail)}"))
    }
}
