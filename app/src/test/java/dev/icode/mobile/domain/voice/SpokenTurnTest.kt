package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantLive
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import org.junit.Assert.assertEquals
import org.junit.Test

/** A turn read aloud as it is written (docs/voice-and-phone-control.md). */
class SpokenTurnTest {
    private val reader = SpokenTurn()
    private val earlier = listOf(AssistantMessage.User("before"), AssistantMessage.Reply("Old answer."))
    private val idle = AssistantState(workspace = "/w/app", session = "s1", messages = earlier)
    private val turn = AssistantTurn("/w/app", "app", "s1", "Thinking…", aloud = true)
    private val call = AssistantCall("c1", "phone_tap", "{}", "Tap **Send**", AssistantToolKind.Change)
    private var now = idle

    /** The assistant moves on to [next]; what the voice is asked. */
    private fun to(next: AssistantState, spoken: SpokenReplies = SpokenReplies.InBubble): List<VoiceCue> =
        reader.next(now, next, spoken).also { now = next }

    private fun writing(vararg messages: AssistantMessage, live: String? = null) =
        idle.copy(turn = turn.copy(live = live?.let { AssistantLive("", it) }), messages = earlier + messages)

    private fun say(vararg texts: String) = texts.map { VoiceCue.Say(it) }

    @Test fun `a turn read aloud begins the voice, says each sentence once whole, and ends after the last`() {
        assertEquals(listOf(VoiceCue.Begin), to(writing()))
        val asked = AssistantMessage.User("turn on wifi")
        assertEquals(emptyList<VoiceCue>(), to(writing(asked, live = "Opening Sett")))
        assertEquals(say("Opening Settings."), to(writing(asked, live = "Opening Settings. Then I")))
        // Recorded with a call: the rest of it is said, and the next reply is read from its start.
        val opening = AssistantMessage.Reply("Opening Settings. Then I tap Wi-Fi.", listOf(call))
        assertEquals(say("Then I tap Wi-Fi."), to(writing(asked, opening)))
        assertEquals(say("Wi-Fi is on."), to(writing(asked, opening, AssistantMessage.Results(emptyList()), live = "Wi-Fi is on.\nAnything")))
        val done = AssistantMessage.Reply("Wi-Fi is on.\nAnything else?")
        val ended = idle.copy(messages = earlier + listOf(asked, opening, AssistantMessage.Results(emptyList()), done))
        assertEquals(say("Anything else?") + VoiceCue.End, to(ended))
    }

    @Test fun `a turn not to be read hushes the voice, and is then left alone`() {
        val quiet = turn.copy(aloud = false)
        assertEquals(listOf(VoiceCue.Hush), to(writing().copy(turn = quiet)))
        assertEquals(emptyList<VoiceCue>(), to(writing(AssistantMessage.User("hi"), live = "Hello there. More").copy(turn = quiet)))
        assertEquals(emptyList<VoiceCue>(), to(idle))
        // Sent aloud while Spoken replies is Off: not read either.
        assertEquals(listOf(VoiceCue.Hush), to(writing(), spoken = SpokenReplies.Off))
    }

    @Test fun `stopped part-way or failed the voice hushes, and Off hushes it at once`() {
        to(writing())
        val asked = AssistantMessage.User("go")
        val stopped = idle.copy(messages = earlier + listOf(asked, AssistantMessage.Reply("Opening.", listOf(call))))
        assertEquals(say("Opening.") + VoiceCue.Hush, to(stopped))
        to(writing())
        val failed = idle.copy(messages = earlier + asked, failure = "The provider refused")
        assertEquals(listOf(VoiceCue.Hush), to(failed))
        to(writing())
        assertEquals(listOf(VoiceCue.Hush), to(writing(asked, live = "Hi. There"), spoken = SpokenReplies.Off))
    }

    @Test fun `questions are said in turn, and another chat shown is not read`() {
        to(writing())
        val asking = writing(AssistantMessage.User("send it")).copy(approval = AssistantApproval(call, "Tap [2]"))
        val question = to(asking).single() as VoiceCue.Say
        assertEquals(true, question.text.endsWith("Tap Send."))
        assertEquals(emptyList<VoiceCue>(), to(asking))
        val paused = writing(AssistantMessage.User("send it")).copy(pause = AssistantPause(AssistantPause.Kind.HandOver, "Type your password"))
        assertEquals(say("Your turn. Type your password."), to(paused))
        val elsewhere = writing(AssistantMessage.User("x"), live = "Other chat. Here").copy(session = "s2")
        assertEquals(emptyList<VoiceCue>(), to(elsewhere))
    }
}
