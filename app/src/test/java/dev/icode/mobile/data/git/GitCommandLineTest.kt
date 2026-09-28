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
    fun `every argument stays one argument after the base options`() {
        val argv = GitCommandLine.argv(listOf("diff", "--", "my file.txt"))
        assertEquals("git", argv.first())
        assertEquals(listOf("diff", "--", "my file.txt"), argv.takeLast(3))
    }

    @Test
    fun `quotepath is disabled so non-ascii paths come back as utf-8`() {
        val argv = GitCommandLine.argv(listOf("status"))
        assertEquals("core.quotepath=false", argv[argv.indexOf("-c") + 1])
    }

    @Test
    fun `status never takes the index lock from the user's own shell`() {
        assertTrue("--no-optional-locks" in GitCommandLine.argv(listOf("status")))
    }

    @Test
    fun `dubious ownership is waived for the bound workspace`() {
        // Without this Git refuses every command with "detected dubious
        // ownership", because the workspace is an Android directory whose
        // owner can never match the guest's fake root.
        assertTrue("safe.directory=*" in GitCommandLine.argv(listOf("status")))
    }

    @Test
    fun `a path that looks like an option is still passed after the separator`() {
        val argv = GitCommandLine.argv(listOf("diff", "--", "--weird-name.txt"))
        assertEquals(listOf("--", "--weird-name.txt"), argv.takeLast(2))
    }

    @Test
    fun `the command bridge quotes each argument whole`() {
        val argv = GitCommandLine.argv(listOf("commit", "-m", "it's \$HOME `x`"))
        val command = argv.joinToString(" ", transform = GitCommandLine::quote)
        assertTrue(command.endsWith("'commit' '-m' 'it'\\''s \$HOME `x`'"))
    }

    @Test
    fun `one command's configuration comes before its subcommand`() {
        val argv = GitCommandLine.argv(listOf("fetch", "--prune"), config = listOf("credential.helper=", "credential.helper=store --file=/tmp/x"))
        val fetch = argv.indexOf("fetch")
        assertEquals(listOf("-c", "credential.helper=", "-c", "credential.helper=store --file=/tmp/x"), argv.subList(fetch - 4, fetch))
    }
}
