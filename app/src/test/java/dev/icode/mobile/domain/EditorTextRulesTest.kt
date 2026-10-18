package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class EditorTextRulesTest {
    private fun TextEdit.on(text: String) = text.substring(0, start) + replacement + text.substring(end)

    @Test fun `a file's line break is the one it writes, and a file with none writes LF`() {
        assertEquals("\r\n", EditorTextRules.separatorOf(LineEnding.CrLf))
        assertEquals("\r", EditorTextRules.separatorOf(LineEnding.Cr))
        assertEquals("\n", EditorTextRules.separatorOf(LineEnding.Lf))
        assertEquals("\n", EditorTextRules.separatorOf(LineEnding.None))
    }

    @Test fun `typed or pasted line breaks of any kind become the file's`() {
        assertEquals("a\r\nb\r\nc\r\nd", EditorTextRules.withLineBreaks("a\nb\r\nc\rd", "\r\n"))
        assertEquals("a\nb\nc\nd", EditorTextRules.withLineBreaks("a\nb\r\nc\rd", "\n"))
        assertEquals("a\rb", EditorTextRules.withLineBreaks("a\r\nb", "\r"))
        assertEquals("plain", EditorTextRules.withLineBreaks("plain", "\r\n"))
        assertEquals("\r\n    ", EditorTextRules.withLineBreaks("\n    ", "\r\n"))
    }

    @Test fun `the smallest edit keeps what both texts start and end with`() {
        assertEquals(TextEdit(4, 7, "TWO"), EditorTextRules.splice("one\ntwo\nthree", "one\nTWO\nthree"))
        assertEquals(TextEdit(3, 3, "\nX"), EditorTextRules.splice("abc", "abc\nX"))
        assertEquals(TextEdit(0, 3, ""), EditorTextRules.splice("abc", ""))
        assertNull(EditorTextRules.splice("same", "same"))
    }

    @Test fun `an edit never splits a CRLF, nor makes one across its edges`() {
        // A line gains a CRLF where it had a CR: the edit takes the CR, not a place between it and the LF.
        assertEquals(TextEdit(1, 2, "\r\n"), EditorTextRules.splice("a\rb", "a\r\nb"))
        assertEquals(TextEdit(1, 3, "\r"), EditorTextRules.splice("a\r\nb", "a\rb"))
        // A CRLF both keep stays out of the edit.
        assertEquals(TextEdit(2, 3, "X"), EditorTextRules.splice("a\nb\r\n", "a\nX\r\n"))
        val random = Random(11)
        repeat(20_000) {
            val old = randomText(random)
            val new = randomText(random)
            val edit = EditorTextRules.splice(old, new) ?: return@repeat assertEquals(old, new)
            val result = edit.on(old)
            assertEquals(new, result)
            val end = edit.start + edit.replacement.length
            assertFalse("$old -> $new: $edit", old.splitsCrlfAt(edit.start) || old.splitsCrlfAt(edit.end))
            assertFalse("$old -> $new: $edit", result.splitsCrlfAt(edit.start) || result.splitsCrlfAt(end))
        }
    }

    @Test fun `Backspace in leading whitespace goes back to the previous tab stop, as VS Code's does`() {
        assertEquals(4, EditorTextRules.deleteLeftCount("    ", 4))
        assertEquals(2, EditorTextRules.deleteLeftCount("      ", 4))
        assertEquals(4, EditorTextRules.deleteLeftCount("        ", 4))
        assertEquals(2, EditorTextRules.deleteLeftCount("  ", 2))
        assertEquals(1, EditorTextRules.deleteLeftCount("\t\t", 4))
        assertEquals(3, EditorTextRules.deleteLeftCount("  \t", 4))
        assertEquals(2, EditorTextRules.deleteLeftCount("\t  ", 4))
        assertEquals(1, EditorTextRules.deleteLeftCount(" ", 4))
    }

    @Test fun `Backspace after other text takes one character`() {
        assertEquals(1, EditorTextRules.deleteLeftCount("    val x =    ", 4))
        assertEquals(1, EditorTextRules.deleteLeftCount("a", 4))
        assertEquals(0, EditorTextRules.deleteLeftCount("", 4))
    }

    /** Whether [at] falls between a `\r` and the `\n` after it. */
    private fun String.splitsCrlfAt(at: Int) = at in 1 until length && this[at - 1] == '\r' && this[at] == '\n'

    private fun randomText(random: Random) = String(CharArray(random.nextInt(0, 8)) { "ab\r\n"[random.nextInt(4)] })
}
