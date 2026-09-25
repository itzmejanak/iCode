package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CountsTest {
    @Test fun `one is singular, every other count is plural`() {
        assertEquals("1 file", countOf(1, "file"))
        assertEquals("0 files", countOf(0, "file"))
        assertEquals("12 directories", countOf(12, "directory", "directories"))
        assertEquals("1 branch", countOf(1, "branch", "branches"))
    }
}
