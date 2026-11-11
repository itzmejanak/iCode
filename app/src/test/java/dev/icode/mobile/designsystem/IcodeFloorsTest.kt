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

    @Test fun `a sheet is open while any sheet is`() {
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

    @Test fun `the stack is shown while it is visible and no sheet is open`() {
        val floors = IcodeFloors()
        assertFalse(floors.hidesStack(visible = true))
        floors.report(Any(), 2000f)
        assertFalse(floors.hidesStack(visible = true))
    }

    @Test fun `the stack is hidden while it is not visible, the Notifications page`() {
        val floors = IcodeFloors()
        assertTrue(floors.hidesStack(visible = false))
    }

    @Test fun `the stack is hidden while a sheet is open and shown again once it closes`() {
        val floors = IcodeFloors()
        val sheet = Any()
        floors.sheet(sheet)
        assertTrue(floors.hidesStack(visible = true))
        floors.leave(sheet)
        assertFalse(floors.hidesStack(visible = true))
    }

    @Test fun `with sheets over sheets, the stack is shown again only when the last one closes`() {
        val floors = IcodeFloors()
        val itemSheet = Any()
        val confirm = Any()
        floors.sheet(itemSheet)
        floors.sheet(confirm)
        floors.leave(itemSheet)
        assertTrue(floors.hidesStack(visible = true))
        floors.leave(confirm)
        assertFalse(floors.hidesStack(visible = true))
    }

    @Test fun `a sheet closing on the Notifications page leaves the stack hidden`() {
        val floors = IcodeFloors()
        val sheet = Any()
        floors.sheet(sheet)
        floors.leave(sheet)
        assertTrue(floors.hidesStack(visible = false))
    }
}
