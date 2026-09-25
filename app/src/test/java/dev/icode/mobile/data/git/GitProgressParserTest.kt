package dev.icode.mobile.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are literal stderr from `git clone --progress` and `git push`. */
class GitProgressParserTest {

    private val cloneStderr = buildString {
        append("Cloning into 'icodeTest'...\n")
        append("remote: Enumerating objects: 1040, done.\r")
        append("remote: Counting objects:  30% (312/1040)\r")
        append("remote: Counting objects: 100% (1040/1040), done.\r\n")
        append("Receiving objects:   4% (42/1040)\r")
        append("Receiving objects:  45% (468/1040), 1.21 MiB | 480.00 KiB/s\r")
        append("Receiving objects: 100% (1040/1040), 3.40 MiB | 512.00 KiB/s, done.\r\n")
        append("Resolving deltas:  61% (380/620)\r")
        append("Resolving deltas: 100% (620/620), done.\r\n")
    }

    @Test
    fun `reports the newest phase and its percentage`() {
        val progress = GitProgressParser.latest(cloneStderr)
        assertEquals("Resolving deltas", progress?.phase)
        assertEquals(1f, progress?.fraction!!, 0.001f)
    }

    @Test
    fun `reads a percentage mid-stream, not only at the end`() {
        val partial = cloneStderr.substringBefore("Receiving objects: 100%")
        val progress = GitProgressParser.latest(partial)
        assertEquals("Receiving objects", progress?.phase)
        assertEquals(0.45f, progress?.fraction!!, 0.001f)
    }

    @Test
    fun `strips git's remote prefix from the phase name`() {
        assertEquals("Counting objects", GitProgressParser.parse("remote: Counting objects:  30% (312/1040)")?.phase)
    }

    @Test
    fun `a line without a percentage is not progress`() {
        assertNull(GitProgressParser.parse("Cloning into 'icodeTest'..."))
        assertNull(GitProgressParser.parse("fatal: repository not found"))
    }

    @Test
    fun `no output at all reports no progress rather than zero`() {
        assertNull(GitProgressParser.latest(""))
    }

    @Test
    fun `redraws of one phase collapse onto the newest line`() {
        val lines = GitProgressParser.outputLines(cloneStderr)
        assertEquals(1, lines.count { it.startsWith("Receiving objects") })
        assertTrue(lines.any { it.contains("Receiving objects: 100% (1040/1040)") })
    }

    @Test
    fun `non-progress lines are kept verbatim and in order`() {
        val lines = GitProgressParser.outputLines(cloneStderr)
        assertEquals("Cloning into 'icodeTest'...", lines.first())
    }

    @Test
    fun `output is bounded so a long clone cannot grow without limit`() {
        val noisy = (1..500).joinToString("\n") { "line $it" }
        assertEquals(4, GitProgressParser.outputLines(noisy, limit = 4).size)
    }

    @Test
    fun `push progress is read the same way`() {
        val pushStderr = "Enumerating objects: 5, done.\rWriting objects:  80% (4/5)\rWriting objects: 100% (5/5), 412 bytes | 412.00 KiB/s, done.\n"
        assertEquals("Writing objects", GitProgressParser.latest(pushStderr)?.phase)
    }
}
