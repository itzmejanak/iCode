package dev.icode.mobile.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScreenTest {
    @Test
    fun `catalog contains every Figma frame exactly once`() {
        assertEquals(40, DemoScreen.entries.size)
        assertEquals(40, DemoScreen.entries.map { it.figmaNode }.distinct().size)
        assertTrue(DemoScreen.entries.all { it.figmaNode.matches(Regex("\\d+:\\d+")) })
    }
}
