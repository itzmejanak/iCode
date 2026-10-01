package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessOutputTest {
    @Test fun `escape codes go, even one cut between two writes`() {
        val output = ProcessOutput()
        output.append("\u001B[32mINFO\u001B[0m: ready\n\u001B]0;title\u0007done \u001B[1")
        output.append(";31mred\u001B[0m\n")
        assertEquals("INFO: ready\ndone red\n", output.since(0).first)
    }

    @Test fun `a carriage return rewrites the line, and a CRLF cut between two writes ends it`() {
        val output = ProcessOutput()
        output.append("Downloading 10%\rDownloading 50%\rDownloading 100%\n")
        output.append("Windows line\r")
        output.append("\nnext")
        assertEquals("Downloading 100%\nWindows line\nnext", output.since(0).first)
    }

    @Test fun `a reader gets what came since its last look, the open line each time until it ends`() {
        val output = ProcessOutput()
        output.append("one\nPassword: ")
        assertEquals("one\nPassword: " to false, output.since(0))
        val seen = output.end
        assertEquals("Password: " to false, output.since(seen))
        output.append("\ntwo\n")
        assertEquals("Password: \ntwo\n", output.since(seen).first)
    }

    @Test fun `only the last of it is kept, and a reader that fell behind is told`() {
        val output = ProcessOutput(limit = 10)
        output.append("aaaa\nbbbb\ncccc\n")
        val (text, missed) = output.since(0)
        assertTrue(missed)
        assertTrue(text.length <= 10)
        assertEquals("cccc\n", text.takeLast(5))
        assertFalse(output.since(output.end).second)
    }

    @Test fun `a line that never ends is kept in pieces`() {
        val output = ProcessOutput()
        output.append("x".repeat(40_000))
        assertTrue(output.end >= 32 * 1024)
        assertEquals(40_000, output.since(0).first.count { it == 'x' })
    }

    @Test fun `the tail starts at a line where one starts inside it`() {
        val output = ProcessOutput()
        output.append("first line\nsecond line\nthird\n")
        assertEquals("third\n", output.tail(8))
        assertEquals("first line\nsecond line\nthird\n", output.tail(100))
    }
}
