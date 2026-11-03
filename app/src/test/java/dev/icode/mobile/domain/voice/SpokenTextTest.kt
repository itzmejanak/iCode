package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantMessage
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is read aloud of the assistant, and when (docs/phone-agent-plan.md §5.8). */
class SpokenTextTest {
    @Test fun `code blocks are not read aloud`() {
        assertEquals("Run this: Then it builds.", SpokenText.of("Run this:\n```bash\n./gradlew build\n```\nThen it builds."))
    }

    @Test fun `links are read as their words, addresses not at all`() {
        assertEquals("See the docs or go.", SpokenText.of("See [the docs](https://example.com/docs) or go https://example.com"))
    }

    @Test fun `markdown marks are dropped, and a heading or an item pauses`() {
        assertEquals("Done. The build passed. main.kt changed.", SpokenText.of("# Done\n- **The build** passed.\n- `main.kt` changed"))
        assertEquals("snake_case stays.", SpokenText.of("snake_case stays"))
        assertEquals("A b.", SpokenText.of("| A | b |\n|---|---|"))
    }

    @Test fun `the last answer is the replies after the user's last message`() {
        val messages = listOf(
            AssistantMessage.User("first"),
            AssistantMessage.Reply("old answer"),
            AssistantMessage.User("turn on wifi"),
            AssistantMessage.Reply("Opening Settings."),
            AssistantMessage.Results(emptyList()),
            AssistantMessage.Reply("Wi-Fi is **on**."),
        )
        assertEquals("Opening Settings. Wi-Fi is on.", SpokenText.lastAnswer(messages))
    }

    @Test fun `a turn with no words says nothing`() {
        assertNull(SpokenText.lastAnswer(listOf(AssistantMessage.User("hi"), AssistantMessage.Reply("```\ncode\n```"))))
    }

    @Test fun `replies are spoken in the bubble by default, never when off, always when always`() {
        assertEquals(SpokenReplies.InBubble, SpokenReplies.of(null))
        assertTrue(SpokenReplies.InBubble.speaks(aloud = true))
        assertFalse(SpokenReplies.InBubble.speaks(aloud = false))
        assertFalse(SpokenReplies.Off.speaks(aloud = true))
        assertTrue(SpokenReplies.Always.speaks(aloud = false))
    }

    @Test fun `long text is cut between sentences, and a sentence too long between words`() {
        assertEquals(listOf("One. Two.", "Three."), SpokenText.parts("One. Two. Three.", 10))
        assertEquals(listOf("aaaa bbbb", "cccc"), SpokenText.parts("aaaa bbbb cccc", 9))
        assertEquals(emptyList<String>(), SpokenText.parts("  ", 9))
    }

    private val turn = AssistantTurn("/w/app", "app", "s1", "Thinking…", aloud = true)
    private val idle = AssistantState(workspace = "/w/app", session = "s1")
    private val working = idle.copy(turn = turn)
    private val call = AssistantCall("c1", "phone_tap", "{}", "Tap **Send**", AssistantToolKind.Change)

    private fun ended(vararg messages: AssistantMessage, failure: String? = null) = idle.copy(messages = messages.toList(), failure = failure)

    @Test fun `a new turn hushes, and a turn sent aloud says its answer once it ends`() {
        assertEquals(VoiceCue.Hush, SpokenText.cue(idle, working, SpokenReplies.Off))
        val answered = ended(AssistantMessage.User("hi"), AssistantMessage.Reply("**Done**."))
        assertEquals(VoiceCue.Say("Done."), SpokenText.cue(working, answered, SpokenReplies.InBubble))
        assertNull(SpokenText.cue(working.copy(turn = turn.copy(aloud = false)), answered, SpokenReplies.InBubble))
        assertEquals(VoiceCue.Say("Done."), SpokenText.cue(working.copy(turn = turn.copy(aloud = false)), answered, SpokenReplies.Always))
        assertNull(SpokenText.cue(working, answered, SpokenReplies.Off))
    }

    @Test fun `a turn stopped part-way or failed hushes, and one whose chat was left says nothing`() {
        val stopped = ended(AssistantMessage.User("hi"), AssistantMessage.Reply("Opening.", listOf(call)))
        assertEquals(VoiceCue.Hush, SpokenText.cue(working, stopped, SpokenReplies.Always))
        val failed = ended(AssistantMessage.User("hi"), AssistantMessage.Reply("Done."), failure = "The provider refused")
        assertEquals(VoiceCue.Hush, SpokenText.cue(working, failed, SpokenReplies.Always))
        val elsewhere = ended(AssistantMessage.User("hi"), AssistantMessage.Reply("Done.")).copy(session = "s2")
        assertNull(SpokenText.cue(working, elsewhere, SpokenReplies.Always))
    }

    @Test fun `a question asked aloud is said once, with what it is about`() {
        val asking = working.copy(approval = AssistantApproval(call, "Tap [2]"))
        val said = SpokenText.cue(working, asking, SpokenReplies.InBubble) as VoiceCue.Say
        assertTrue(said.text, said.text.endsWith("Tap Send."))
        assertNull(SpokenText.cue(asking, asking.copy(turn = turn.copy(step = "Waiting")), SpokenReplies.InBubble))
        val paused = working.copy(pause = AssistantPause(AssistantPause.Kind.HandOver, "Type your password"))
        assertEquals(VoiceCue.Say("Your turn. Type your password."), SpokenText.cue(working, paused, SpokenReplies.InBubble))
    }
}
