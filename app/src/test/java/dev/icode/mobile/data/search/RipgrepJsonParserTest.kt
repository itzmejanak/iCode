package dev.icode.mobile.data.search

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RipgrepJsonParserTest {
    @Test fun `groups real match messages and reads summary file count`() {
        val parser = RipgrepJsonParser("/workspace", maxMatchesPerFile = 20, maxTotalMatches = 100)
        parser.accept("""{"type":"begin","data":{"path":{"text":"./src/a file.txt"}}}""")
        parser.accept(match("./src/a file.txt", "alpha alpha\n", line = 3, starts = listOf(0, 6)))
        parser.accept("""{"type":"end","data":{"path":{"text":"./src/a file.txt"},"stats":{"searches":1}}}""")
        parser.accept("""{"type":"summary","data":{"stats":{"searches":9}}}""")

        val outcome = parser.outcome()
        val result = outcome.results.single()
        assertEquals(File("/workspace/src/a file.txt").path, result.path)
        assertEquals("a file.txt", result.name)
        assertEquals(listOf(3 to 0, 3 to 6), result.matches.map { it.lineNumber to it.column })
        assertEquals("alpha alpha", result.matches.first().lineText)
        assertEquals(9, outcome.searchedFiles)
        assertFalse(outcome.truncated)
    }

    @Test fun `ripgrep byte offset becomes editor utf16 column`() {
        val parser = RipgrepJsonParser("/workspace", maxMatchesPerFile = 20, maxTotalMatches = 100)
        // 'é' occupies two UTF-8 bytes but one UTF-16 code unit.
        parser.accept(match("./unicode.txt", "é alpha\n", line = 1, starts = listOf(3)))

        assertEquals(2, parser.outcome().results.single().matches.single().column)
    }

    @Test fun `text fields also accept ripgrep base64 bytes form`() {
        val parser = RipgrepJsonParser("/workspace", maxMatchesPerFile = 20, maxTotalMatches = 100)
        val path = Base64.getEncoder().encodeToString("./line\nbreak.txt".toByteArray())
        val text = Base64.getEncoder().encodeToString("needle\n".toByteArray())
        val json = """{"type":"match","data":{"path":{"bytes":"$path"},"lines":{"bytes":"$text"},"line_number":2,"submatches":[{"match":{"text":"needle"},"start":0,"end":6}]}}"""

        parser.accept(json)

        assertEquals("line\nbreak.txt", parser.outcome().results.single().name)
    }

    @Test fun `one extra global match stops streaming and reports truncation`() {
        val parser = RipgrepJsonParser("/workspace", maxMatchesPerFile = 20, maxTotalMatches = 2)

        assertTrue(parser.accept(match("./a.txt", "needle needle\n", 1, listOf(0, 7))))
        assertFalse(parser.accept(match("./b.txt", "needle\n", 1, listOf(0))))
        assertEquals(2, parser.outcome().results.sumOf { it.matches.size })
        assertTrue(parser.outcome().truncated)
    }

    @Test fun `per file cap keeps accepting later files`() {
        val parser = RipgrepJsonParser("/workspace", maxMatchesPerFile = 1, maxTotalMatches = 20)

        assertTrue(parser.accept(match("./a.txt", "needle needle\n", 1, listOf(0, 7))))
        assertTrue(parser.accept(match("./b.txt", "needle\n", 1, listOf(0))))

        val outcome = parser.outcome()
        assertEquals(listOf(1, 1), outcome.results.map { it.matches.size })
        assertTrue(outcome.truncated)
    }

    private fun match(path: String, text: String, line: Int, starts: List<Int>): String {
        val escapedPath = path.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")
        val escapedText = text.replace("\\", "\\\\").replace("\n", "\\n").replace("\"", "\\\"")
        val submatches = starts.joinToString(",") { start ->
            """{"match":{"text":"x"},"start":$start,"end":${start + 1}}"""
        }
        return """{"type":"match","data":{"path":{"text":"$escapedPath"},"lines":{"text":"$escapedText"},"line_number":$line,"submatches":[$submatches]}}"""
    }
}
