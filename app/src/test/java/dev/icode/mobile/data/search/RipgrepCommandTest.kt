package dev.icode.mobile.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RipgrepCommandTest {
    @Test fun `file discovery is nul safe and keeps ignore rules for hidden files`() {
        val arguments = RipgrepCommand.files()

        assertTrue("--files" in arguments)
        assertTrue("--null" in arguments)
        assertTrue("--hidden" in arguments)
        assertTrue("--no-require-git" in arguments)
        assertTrue("--glob=!.git" in arguments)
        assertFalse("--no-ignore" in arguments)
    }

    @Test fun `literal query that looks like an option stays after separator`() {
        val arguments = RipgrepCommand.content(
            query = "--files",
            matchCase = true,
            wholeWord = false,
            regex = false,
            maxMatchingLinesPerFile = 201,
        )

        val separator = arguments.indexOf("--")
        assertEquals("--files", arguments[separator + 1])
        assertEquals(".", arguments[separator + 2])
        assertTrue("--fixed-strings" in arguments)
        assertTrue("--case-sensitive" in arguments)
    }

    @Test fun `regex case and whole word flags are explicit`() {
        val arguments = RipgrepCommand.content(
            query = "[a-z]+",
            matchCase = false,
            wholeWord = true,
            regex = true,
            maxMatchingLinesPerFile = 25,
        )

        assertTrue("--ignore-case" in arguments)
        assertTrue("--word-regexp" in arguments)
        assertTrue("--max-count=25" in arguments)
        assertFalse("--fixed-strings" in arguments)
    }
}
