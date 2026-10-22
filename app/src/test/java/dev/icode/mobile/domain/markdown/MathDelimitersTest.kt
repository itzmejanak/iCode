package dev.icode.mobile.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class MathDelimitersTest {
    private fun converted(text: String, chunk: Int = text.length): String {
        val delimiters = MathDelimiters()
        return text.chunked(chunk.coerceAtLeast(1)).joinToString("") { delimiters.feed(it) } + delimiters.finish()
    }

    @Test fun `parentheses and brackets become dollars`() {
        assertEquals("Rate \$\\frac{1}{20}\$ a day", converted("Rate \\( \\frac{1}{20} \\) a day"))
        assertEquals("\$\$\nx = 1\n\$\$", converted("\\[\nx = 1\n\\]"))
        assertEquals("> \$\$ y \$\$", converted("> \\[ y \\]"))
    }

    @Test fun `the same in any chunks`() {
        val text = "a \\(  x + y  \\) b\n\n```\n\\(code\\)\n```\n\\[z\\] and `\\(span\\)` \\\\(not\\\\)"
        val whole = converted(text)
        (1..9).forEach { assertEquals("chunks of $it", whole, converted(text, it)) }
        assertEquals("a \$x + y\$ b\n\n```\n\\(code\\)\n```\n\$\$z\$\$ and `\\(span\\)` \\\\(not\\\\)", whole)
    }

    @Test fun `a bracket mid-line stays an escaped bracket`() {
        assertEquals("a\\[0\\] = 1", converted("a\\[0\\] = 1"))
    }

    @Test fun `a blank line ends math left open`() {
        assertEquals("\$a\n\nb \\) c", converted("\\(a\n\nb \\) c"))
    }

    @Test fun `tilde fences and longer backtick fences hold their text`() {
        assertEquals("~~~\n\\(x\\)\n~~~\n\$y\$", converted("~~~\n\\(x\\)\n~~~\n\\(y\\)"))
        assertEquals("````\n```\n\\(x\\)\n````\n\$y\$", converted("````\n```\n\\(x\\)\n````\n\\(y\\)"))
    }
}
