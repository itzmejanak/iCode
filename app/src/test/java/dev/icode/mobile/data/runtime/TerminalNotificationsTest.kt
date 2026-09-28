package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalNotificationsTest {
    @Test fun `a row says where, what runs and whether it keeps the phone awake`() {
        assertEquals(
            "superpowers · running opencode · wake lock held",
            TerminalNotifications.rowText("superpowers", TerminalForeground.Running("opencode"), awake = true),
        )
        assertEquals("superpowers · idle", TerminalNotifications.rowText("superpowers", TerminalForeground.Idle, awake = false))
        assertEquals("", TerminalNotifications.rowText(null, TerminalForeground.Unknown, awake = false))
    }

    @Test fun `an alert names the tab and its workspace`() {
        assertEquals("bash · superpowers", TerminalNotifications.whereIs("bash", "superpowers"))
        assertEquals("bash 2", TerminalNotifications.whereIs("bash 2", null))
    }

    @Test fun `each session keeps one row, apart from the parent`() {
        assertEquals(5003, TerminalNotifications.rowId("session-3"))
        assertEquals(TerminalNotifications.rowId("session-3"), TerminalNotifications.rowId("session-3"))
        assertTrue(TerminalNotifications.rowId("odd") != TerminalNotifications.PARENT_ID)
    }
}
