package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTabOrderTest {
    @Test
    fun `dragging a tab moves it to the target position`() {
        val tabs = listOf("one", "two", "three", "four")

        assertEquals(listOf("two", "three", "one", "four"), reorderTerminalIds(tabs, "one", "three"))
        assertEquals(listOf("four", "one", "two", "three"), reorderTerminalIds(tabs, "four", "one"))
    }

    @Test
    fun `invalid or stationary drag keeps the same order`() {
        val tabs = listOf("one", "two")

        assertEquals(tabs, reorderTerminalIds(tabs, "one", "one"))
        assertEquals(tabs, reorderTerminalIds(tabs, "missing", "two"))
        assertEquals(tabs, reorderTerminalIds(tabs, "one", "missing"))
    }
}
