package dev.icode.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDetectorTest {

    @Test
    fun `finds a development server url in terminal output`() {
        val line = "- ready started server on 0.0.0.0:3000, url: http://localhost:3000"
        assertEquals(listOf("http://localhost:3000"), UrlDetector.find(line))
    }

    @Test
    fun `keeps paths and query strings`() {
        val found = UrlDetector.find("open https://example.com/a/b?c=1&d=2 now")
        assertEquals(listOf("https://example.com/a/b?c=1&d=2"), found)
    }

    @Test
    fun `trims trailing sentence punctuation`() {
        assertEquals(listOf("https://example.com/docs"), UrlDetector.find("See https://example.com/docs."))
        assertEquals(listOf("https://example.com/x"), UrlDetector.find("(https://example.com/x)"))
    }

    @Test
    fun `ignores non http schemes`() {
        assertTrue(UrlDetector.find("git@github.com:owner/repo.git ftp://files.example.com").isEmpty())
    }

    @Test
    fun `deduplicates repeated urls`() {
        val found = UrlDetector.find("http://a.test/x and again http://a.test/x")
        assertEquals(listOf("http://a.test/x"), found)
    }

    @Test
    fun `finds several distinct urls`() {
        val found = UrlDetector.find("http://one.test https://two.test/path")
        assertEquals(listOf("http://one.test", "https://two.test/path"), found)
    }

    @Test
    fun `finds the URL covering a character`() {
        val line = "server on http://localhost:3000, ready"
        assertEquals("http://localhost:3000", UrlDetector.at(line, line.indexOf("localhost")))
        assertEquals("http://localhost:3000", UrlDetector.at(line, line.indexOf("http")))
    }

    @Test
    fun `a character outside every URL finds nothing`() {
        val line = "server on http://localhost:3000, ready"
        assertEquals(null, UrlDetector.at(line, line.indexOf("server")))
        assertEquals(null, UrlDetector.at(line, line.indexOf("ready")))
    }

    @Test
    fun `trailing punctuation is not part of the URL at a character`() {
        val line = "see https://example.com/docs."
        assertEquals("https://example.com/docs", UrlDetector.at(line, line.indexOf("docs")))
    }
}
