package dev.icode.mobile.ui.screens.entry

import dev.icode.mobile.domain.LostTerminalSession
import dev.icode.mobile.domain.ProcessEndCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LostSessionsNoticeTest {

    @Test
    fun `sessions are listed in tab order with their workspaces`() {
        val sessions = listOf(
            LostTerminalSession("bash", "superpowers"),
            LostTerminalSession("bash 2", "superpowers"),
            LostTerminalSession("bash 3", null),
        )
        assertEquals("bash · superpowers, bash 2 · superpowers, bash 3", lostSessionsList(sessions))
    }

    @Test
    fun `every cause has its own sentence`() {
        val sentences = ProcessEndCause.entries.map(::lostSessionsCause)
        assertEquals(sentences.size, sentences.toSet().size)
        assertTrue(sentences.none { it.isBlank() || it.endsWith(".") })
    }
}
