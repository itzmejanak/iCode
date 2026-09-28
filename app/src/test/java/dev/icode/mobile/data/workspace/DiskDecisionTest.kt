package dev.icode.mobile.data.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class DiskDecisionTest {
    private fun decide(disk: String?, dirty: Boolean = false, recovery: Boolean = false, asked: Boolean = false) =
        decideOnDisk("read", disk, dirty, recovery, asked)

    @Test fun `the same version on disk needs nothing`() {
        assertEquals(DiskDecision.Keep, decide("read"))
        assertEquals(DiskDecision.Keep, decide("read", dirty = true))
    }

    @Test fun `a clean file takes the new disk version`() {
        assertEquals(DiskDecision.Reload, decide("new"))
    }

    @Test fun `unsaved edits over a changed file are asked about once`() {
        assertEquals(DiskDecision.Ask, decide("new", dirty = true))
        assertEquals(DiskDecision.Keep, decide("new", dirty = true, asked = true))
    }

    @Test fun `an unanswered draft question comes first`() {
        assertEquals(DiskDecision.Keep, decide("new", recovery = true))
        assertEquals(DiskDecision.Keep, decide("new", dirty = true, recovery = true))
    }

    @Test fun `a file gone from disk is said whatever its state`() {
        assertEquals(DiskDecision.Gone, decide(null))
        assertEquals(DiskDecision.Gone, decide(null, dirty = true))
    }
}
