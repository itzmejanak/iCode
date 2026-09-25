package dev.icode.mobile.data.git

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitCommandLineTest {

    @Test
    fun `plain arguments are quoted`() {
        assertEquals("'status'", GitCommandLine.quote("status"))
    }

    @Test
    fun `spaces survive quoting`() {
        assertEquals("'my file.txt'", GitCommandLine.quote("my file.txt"))
    }

    @Test
    fun `a single quote is closed, escaped and reopened`() {
        // The POSIX '\'' idiom; anything else would end the quoted string early.
        assertEquals("'it'\\''s.txt'", GitCommandLine.quote("it's.txt"))
    }

    @Test
    fun `shell metacharacters stay literal`() {
        val hostile = "\$(rm -rf /);`whoami`;a|b&c>d"
        val quoted = GitCommandLine.quote(hostile)
        assertTrue(quoted.startsWith("'") && quoted.endsWith("'"))
        // Nothing inside was allowed to break out of the quotes.
        assertEquals(hostile, quoted.substring(1, quoted.length - 1))
    }

    @Test
    fun `the script quotes every argument and redirects stderr`() {
        val script = GitCommandLine.script(listOf("diff", "--", "my file.txt"))
        assertTrue(script.contains("'diff'"))
        assertTrue(script.contains("'my file.txt'"))
        assertTrue(script.endsWith("2>'${GitCommandLine.GUEST_STDERR_PATH}'"))
    }

    @Test
    fun `quotepath is disabled so non-ascii paths come back as utf-8`() {
        val script = GitCommandLine.script(listOf("status"))
        assertTrue(script.contains("'core.quotepath=false'"))
    }

    @Test
    fun `status never takes the index lock from the user's own shell`() {
        val script = GitCommandLine.script(listOf("status"))
        assertTrue(script.contains("'--no-optional-locks'"))
    }

    @Test
    fun `dubious ownership is waived for the bound workspace`() {
        // Without this Git refuses every command with "detected dubious
        // ownership", because the workspace is an Android directory whose
        // owner can never match the guest's fake root.
        val script = GitCommandLine.script(listOf("status"))
        assertTrue(script.contains("'safe.directory=*'"))
    }

    @Test
    fun `a path that looks like an option is still passed after the separator`() {
        val script = GitCommandLine.script(listOf("diff", "--", "--weird-name.txt"))
        assertTrue(script.contains("'--' '--weird-name.txt'"))
    }
}
