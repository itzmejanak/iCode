package dev.icode.mobile.ui.syntax

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When colouring stops (docs/editor-and-language-services.md): the measured values and each decision. */
class SyntaxLimitsTest {
    @Test fun `the measured limits are kept`() {
        assertEquals(10_000, SyntaxLimits.LINE_CHARS)
        assertEquals(Duration.ofMillis(100), SyntaxLimits.LINE_TIME)
        assertEquals(2 * 1024 * 1024, SyntaxLimits.FILE_BYTES)
        assertEquals(50_000, SyntaxLimits.FILE_LINES)
        assertEquals(200_000, SyntaxLimits.BLOCK_CHARS)
    }

    @Test fun `a file over 2 MiB opens plain, counted in UTF-8 bytes`() {
        assertTrue(SyntaxLimits.colours("fun main() = Unit\n"))
        assertTrue(SyntaxLimits.colours("a".repeat(SyntaxLimits.FILE_BYTES)))
        assertFalse(SyntaxLimits.colours("a".repeat(SyntaxLimits.FILE_BYTES + 1)))
        // 2 bytes each: half as many characters reach the limit.
        assertTrue(SyntaxLimits.colours("é".repeat(SyntaxLimits.FILE_BYTES / 2)))
        assertFalse(SyntaxLimits.colours("é".repeat(SyntaxLimits.FILE_BYTES / 2 + 1)))
        // A surrogate pair is 4 bytes.
        assertFalse(SyntaxLimits.colours("😀".repeat(SyntaxLimits.FILE_BYTES / 4 + 1)))
    }

    @Test fun `a file of more than 50 000 lines opens plain, whatever its line breaks`() {
        for (separator in listOf("\n", "\r\n", "\r")) {
            assertTrue(separator, SyntaxLimits.colours("x$separator".repeat(SyntaxLimits.FILE_LINES - 1) + "x"))
            assertFalse(separator, SyntaxLimits.colours("x$separator".repeat(SyntaxLimits.FILE_LINES)))
        }
    }

    @Test fun `a 1 MB line opens coloured, up to the line limit`() {
        val line = "a".repeat(1_000_000)
        assertTrue(SyntaxLimits.colours(line))
        assertEquals(SyntaxLimits.LINE_CHARS, SyntaxLimits.cut(line))
        assertEquals(500, SyntaxLimits.cut("a".repeat(500)))
    }

    @Test fun `the cut never splits a surrogate pair`() {
        val line = "a".repeat(SyntaxLimits.LINE_CHARS - 1) + "😀" + "b".repeat(10)
        assertEquals(SyntaxLimits.LINE_CHARS - 1, SyntaxLimits.cut(line))
    }

    @Test fun `a read-only block over 200 000 characters is plain`() {
        assertTrue(SyntaxLimits.coloursBlock("x".repeat(SyntaxLimits.BLOCK_CHARS)))
        assertFalse(SyntaxLimits.coloursBlock("x".repeat(SyntaxLimits.BLOCK_CHARS + 1)))
    }
}
