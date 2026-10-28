package dev.icode.mobile.data.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathRulesTest {
    @Test fun `a location follows its folder, and only its folder`() {
        assertTrue("/w/src/a.kt".isAtOrUnder("/w/src"))
        assertTrue("/w/src".isAtOrUnder("/w/src"))
        assertFalse("/w/srcx/a.kt".isAtOrUnder("/w/src"))
        assertEquals("/w/lib/a.kt", "/w/src/a.kt".relocated("/w/src", "/w/lib"))
        assertEquals("/w/srcx/a.kt", "/w/srcx/a.kt".relocated("/w/src", "/w/lib"))
    }
}
