package dev.icode.mobile.data.search

import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RipgrepFileParserTest {
    @Test fun `nul records preserve spaces quotes unicode and filename newlines`() {
        val parser = RipgrepFileParser("/workspace", maxFiles = 20)
        val names = listOf("./space name.kt", "./it's-é.kt", "./line\nbreak.txt")

        names.forEach { assertTrue(parser.accept(it.toByteArray(StandardCharsets.UTF_8))) }

        val outcome = parser.outcome()
        assertEquals(names.map { it.removePrefix("./") }.sorted(), outcome.files.map { it.path.removePrefix("/workspace/") })
        assertTrue(outcome.files.any { it.name == "line\nbreak.txt" })
        assertFalse(outcome.truncated)
    }

    @Test fun `one extra path proves the index was truncated`() {
        val parser = RipgrepFileParser(File("/workspace").path, maxFiles = 2)

        assertTrue(parser.accept("./a.txt".toByteArray()))
        assertTrue(parser.accept("./b.txt".toByteArray()))
        assertFalse(parser.accept("./c.txt".toByteArray()))

        assertEquals(2, parser.outcome().files.size)
        assertTrue(parser.outcome().truncated)
    }
}
