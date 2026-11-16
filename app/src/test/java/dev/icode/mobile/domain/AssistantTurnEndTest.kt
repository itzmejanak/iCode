package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/design-and-navigation.md: each turn's end is said once, with whether it changed a file. */
class AssistantTurnEndTest {
    private val turn = AssistantTurn("/w", "w", "chat", "Thinking…")

    @Test
    fun `a turn's end clears what it waited on and is counted`() {
        val asking = AssistantState(turn = turn, pause = AssistantPause(AssistantPause.Kind.StepLimit, ""))
        val first = asking.turnEnded()
        assertNull(first.turn)
        assertNull(first.pause)
        assertEquals(AssistantTurnEnd("/w", 1, changedFiles = false), first.ended)
        val second = first.copy(turn = turn.copy(changedFiles = true)).turnEnded()
        assertEquals(AssistantTurnEnd("/w", 2, changedFiles = true), second.ended)
    }

    @Test
    fun `with no turn in hand nothing ended`() {
        val ended = AssistantTurnEnd("/w", 3, changedFiles = true)
        assertEquals(ended, AssistantState(ended = ended).turnEnded().ended)
    }
}
