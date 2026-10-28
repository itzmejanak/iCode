package dev.icode.mobile.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the floating stack stands clear of (docs/explorer-gaps-plan.md §3.8): the highest floor on screen, and any sheet open. */
class IcodeFloorsTest {
    @Test fun `the stack stands above the highest floor on screen, and nothing once they have gone`() {
        val floors = IcodeFloors()
        val fab = Any()
        val bar = Any()
        assertNull(floors.highest)
        floors.report(bar, 2200f)
        floors.report(fab, 2050f)
        assertEquals(2050f, floors.highest)
        floors.report(fab, 2100f)
        assertEquals(2100f, floors.highest)
        floors.leave(fab)
        assertEquals(2200f, floors.highest)
        floors.leave(bar)
        assertNull(floors.highest)
    }

    @Test fun `it steps aside while any sheet is open`() {
        val floors = IcodeFloors()
        val sheet = Any()
        val page = Any()
        floors.sheet(sheet)
        floors.sheet(page)
        floors.leave(sheet)
        assertTrue(floors.sheetOpen)
        floors.leave(page)
        assertFalse(floors.sheetOpen)
    }
}
