package dev.icode.mobile.data.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RipgrepCommandTest {
    @Test fun `file discovery is nul safe and keeps ignore rules for hidden files`() {
        val arguments = RipgrepCommand.files(ignored = false)

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
            ignored = false,
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
            ignored = false,
        )

        assertTrue("--ignore-case" in arguments)
        assertTrue("--word-regexp" in arguments)
        assertTrue("--max-count=25" in arguments)
        assertFalse("--fixed-strings" in arguments)
    }

    @Test fun `ignored also reaches what ignore files leave out, never Git's own folder`() {
        val files = RipgrepCommand.files(ignored = true)
        val content = RipgrepCommand.content("x", matchCase = false, wholeWord = false, regex = false, maxMatchingLinesPerFile = 1, ignored = true)
        listOf(files, content).forEach { arguments ->
            assertTrue("--no-ignore" in arguments)
            assertTrue("--glob=!.git" in arguments)
        }
    }
}
