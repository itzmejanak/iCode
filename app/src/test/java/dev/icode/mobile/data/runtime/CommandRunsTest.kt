package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandRunsTest {

    private val running = { program: String -> TerminalForeground.Running(program) }

    @Test
    fun `a command ends when the shell has the terminal again`() {
        val runs = CommandRuns()
        assertNull(runs.sample("s1", TerminalForeground.Idle, 0))
        assertNull(runs.sample("s1", running("npm"), 5_000))
        assertNull(runs.sample("s1", running("npm"), 10_000))
        assertEquals(FinishedCommand("npm", 40_000), runs.sample("s1", TerminalForeground.Idle, 45_000))
        assertNull(runs.sample("s1", TerminalForeground.Idle, 50_000))
    }

    @Test
    fun `another program taking the terminal ends the first`() {
        val runs = CommandRuns()
        runs.sample("s1", running("make"), 0)
        assertEquals(FinishedCommand("make", 7_000), runs.sample("s1", running("vim"), 7_000))
        assertEquals(FinishedCommand("vim", 3_000), runs.sample("s1", TerminalForeground.Idle, 10_000))
    }

    @Test
    fun `an unreadable sample neither starts nor ends anything`() {
        val runs = CommandRuns()
        runs.sample("s1", running("sleep"), 0)
        assertNull(runs.sample("s1", TerminalForeground.Unknown, 5_000))
        assertEquals(FinishedCommand("sleep", 40_000), runs.sample("s1", TerminalForeground.Idle, 40_000))
    }

    @Test
    fun `sessions are tracked apart, and a closed one is forgotten`() {
        val runs = CommandRuns()
        runs.sample("s1", running("claude"), 0)
        runs.sample("s2", running("npm"), 0)
        runs.forget("s1")
        assertNull(runs.sample("s1", TerminalForeground.Idle, 60_000))
        assertEquals(FinishedCommand("npm", 60_000), runs.sample("s2", TerminalForeground.Idle, 60_000))
    }

    @Test
    fun `durations are said the way a person would`() {
        assertEquals("45 s", spokenDuration(45_900))
        assertEquals("2 min", spokenDuration(130_000))
        assertEquals("1 h", spokenDuration(3_600_000))
        assertEquals("1 h 5 min", spokenDuration(3_900_000))
    }
}
