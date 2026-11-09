package dev.icode.mobile.data.language

import dev.icode.mobile.domain.TextPosition
import dev.icode.mobile.domain.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LspTextTest {
    @Test
    fun `columns count UTF-16 units, so a character outside the BMP counts two`() {
        val text = "a𐐀b"
        assertEquals(TextPosition(0, 3), LspText.positionOf(text, 3))
        assertEquals(3, LspText.offsetOf(text, TextPosition(0, 3)))
    }

    @Test
    fun `every kind of line break ends a line, CRLF as one`() {
        val text = "one\r\ntwo\rthree\nfour"
        assertEquals(TextPosition(1, 0), LspText.positionOf(text, 5))
        assertEquals(TextPosition(2, 0), LspText.positionOf(text, 9))
        assertEquals(TextPosition(3, 2), LspText.positionOf(text, 17))
        assertEquals(17, LspText.offsetOf(text, TextPosition(3, 2)))
        assertEquals(9, LspText.offsetOf(text, TextPosition(2, 0)))
    }

    @Test
    fun `past a line's end is its end, past the last line the text's end`() {
        val text = "ab\r\ncd"
        assertEquals(2, LspText.offsetOf(text, TextPosition(0, 99)))
        assertEquals(text.length, LspText.offsetOf(text, TextPosition(9, 0)))
    }

    @Test
    fun `a change is one splice in the old text's places`() {
        assertEquals(TextRange(TextPosition(1, 3), TextPosition(1, 3)) to "x", LspText.change("ab\r\ncde", "ab\r\ncdex"))
        assertEquals(TextRange(TextPosition(0, 1), TextPosition(1, 1)) to "", LspText.change("ab\ncd", "ad"))
        assertNull(LspText.change("same", "same"))
    }

    @Test
    fun `turning a line break into a CRLF never splits one`() {
        val (range, replacement) = LspText.change("a\nb", "a\r\nb")!!
        val old = "a\nb"
        val start = LspText.offsetOf(old, range.start)
        val end = LspText.offsetOf(old, range.end)
        assertEquals("a\r\nb", old.substring(0, start) + replacement + old.substring(end))
    }
}
