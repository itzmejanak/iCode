package dev.icode.mobile.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyGridLayoutTest {
    // A phone's settings card, 339 dp inside, at 2.625 px a dp: 48 dp cells, 4 dp apart.
    private val layout = KeyGridLayout.fit(width = 890, minCell = 126, cellHeight = 126, gap = 10)

    @Test fun `as many equal cells as fit, stretched to fill the width`() {
        assertEquals(6, layout.columns)
        assertEquals(140, layout.cellWidth)
        assertEquals(0, 890 - 6 * 140 - 5 * 10)
        assertEquals(1, KeyGridLayout.fit(width = 100, minCell = 126, cellHeight = 126, gap = 10).columns)
    }

    @Test fun `cells run left to right, then down`() {
        assertEquals(0 to 0, layout.x(0) to layout.y(0))
        assertEquals(5 * 150 to 0, layout.x(5) to layout.y(5))
        assertEquals(0 to 136, layout.x(6) to layout.y(6))
        assertEquals(0, layout.height(0))
        assertEquals(126, layout.height(6))
        assertEquals(126 + 136, layout.height(7))
    }

    @Test fun `a key dragged over a cell takes its place, and never one past the last`() {
        // Its middle over the third cell of the second row.
        assertEquals(8, layout.indexAt(2 * 150 + 70f, 136 + 63f, count = 42))
        // Nearer the fourth than the third.
        assertEquals(9, layout.indexAt(3 * 150 + 10f, 136 + 63f, count = 42))
        // Off the grid's ends.
        assertEquals(0, layout.indexAt(-300f, -300f, count = 42))
        assertEquals(7, layout.indexAt(5000f, 5000f, count = 8))
    }

    @Test fun `a moved item leaves the others in their order`() {
        assertEquals(listOf("b", "c", "a", "d"), listOf("a", "b", "c", "d").moved(0, 2))
        assertEquals(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d").moved(3, 0))
    }
}
