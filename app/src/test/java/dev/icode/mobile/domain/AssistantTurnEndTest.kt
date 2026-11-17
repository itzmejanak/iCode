package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** docs/design-and-navigation.md: each turn's end is said once, with whether it changed a file. */
class AssistantTurnEndTest {
    private val turn = AssistantTurn("/w", "w", "chat", "Thinking…")

    @Test
    fun `a turn's end clears what it waited on and is counted`() {
        val asking = AssistantState(workspace = "/w", session = "chat", turns = listOf(turn.copy(pause = AssistantPause(AssistantPause.Kind.StepLimit, ""))))
        val first = asking.turnEnded("/w", "chat")
        assertNull(first.turn)
        assertNull(first.pause)
        assertEquals(AssistantTurnEnd("/w", 1, changedFiles = false), first.ended)
        val second = first.copy(turns = listOf(turn.copy(changedFiles = true))).turnEnded("/w", "chat")
        assertEquals(AssistantTurnEnd("/w", 2, changedFiles = true), second.ended)
    }

    @Test
    fun `with no turn in that chat nothing ended`() {
        val ended = AssistantTurnEnd("/w", 3, changedFiles = true)
        val other = AssistantState(turns = listOf(turn), ended = ended)
        assertSame(other, other.turnEnded("/w", "another"))
        assertEquals(ended, AssistantState(ended = ended).turnEnded("/w", "chat").ended)
    }

    @Test
    fun `chats end on their own, and another chat's turn and question stay`() {
        val elsewhere = AssistantTurn(null, "Home", "other", "Read notes.md", approval = AssistantApproval(AssistantCall("c1", "read_file", "{}", "Read notes.md", AssistantToolKind.Read), ""))
        val both = AssistantState(workspace = "/w", session = "chat", turns = listOf(turn, elsewhere))
        val one = both.turnEnded("/w", "chat")
        assertEquals(listOf(elsewhere), one.turns)
        assertEquals(AssistantTurnEnd("/w", 1, changedFiles = false), one.ended)
        assertEquals(true, one.asking)
        assertNull(one.approval)
    }
}
