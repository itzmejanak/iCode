package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionStateTest {

    private fun session(
        status: TerminalStatus,
        exitCode: Int? = null,
        exitSignal: Int? = null,
        failure: String? = null,
    ) = TerminalSessionState(
        id = "s", title = "bash", status = status,
        exitCode = exitCode, exitSignal = exitSignal, failure = failure,
    )

    @Test
    fun `only a running session counts as running`() {
        assertTrue(session(TerminalStatus.Running).isRunning)
        assertFalse(session(TerminalStatus.Exited, exitSignal = 9).isRunning)
        assertFalse(session(TerminalStatus.Failed).isRunning)
    }

    @Test
    fun `a shell killed by a signal is described by the signal's name`() {
        assertEquals("Ended by SIGKILL", session(TerminalStatus.Exited, exitSignal = 9).exitDescription)
        assertEquals("Ended by SIGSEGV", session(TerminalStatus.Exited, exitSignal = 11).exitDescription)
    }

    @Test
    fun `an unnamed signal keeps its number`() {
        assertEquals("Ended by signal 34", session(TerminalStatus.Exited, exitSignal = 34).exitDescription)
    }

    @Test
    fun `an exit code of 130 is an exit code, not a signal`() {
        // bash exits with the status of the last command; after Ctrl+C that is
        // 130. Only waitpid can say a signal ended the shell.
        assertEquals("Process exited with code 130", session(TerminalStatus.Exited, exitCode = 130).exitDescription)
    }

    @Test
    fun `a clean exit is described as normal`() {
        assertEquals("Process exited normally", session(TerminalStatus.Exited, exitCode = 0).exitDescription)
    }

    @Test
    fun `a failed start reports why`() {
        assertEquals("The configured shell /bin/zsh is unavailable in the runtime",
            session(TerminalStatus.Failed, failure = "The configured shell /bin/zsh is unavailable in the runtime").exitDescription)
        assertEquals("The terminal could not start", session(TerminalStatus.Failed).exitDescription)
    }

    @Test
    fun `a running session has no exit description`() {
        assertNull(session(TerminalStatus.Running).exitDescription)
    }

    @Test
    fun `snapshot falls back to the first session when the selection is stale`() {
        val snapshot = TerminalSnapshot(
            sessions = listOf(session(TerminalStatus.Running).copy(id = "a")),
            selectedId = "gone",
        )
        assertEquals("a", snapshot.selected?.id)
    }

    @Test
    fun `snapshot with no sessions selects nothing`() {
        assertNull(TerminalSnapshot().selected)
    }
}
