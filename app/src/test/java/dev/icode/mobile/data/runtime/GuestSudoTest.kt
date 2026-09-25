package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runs the script iCode installs as the guest's `sudo` with a real POSIX
 * shell, so what is checked is its behaviour, not its text.
 */
class GuestSudoTest {

    private lateinit var sudo: File

    @Before
    fun install() {
        assumeTrue("needs a POSIX shell", File(SH).canExecute())
        val root = Files.createTempDirectory("guest-sudo").toFile().apply { deleteOnExit() }
        GuestSudo.install(root)
        sudo = File(root, GuestSudo.RELATIVE_PATH)
    }

    private data class Run(val code: Int, val out: String, val err: String)

    private fun run(vararg arguments: String, input: String = ""): Run {
        val process = ProcessBuilder(listOf(SH, sudo.absolutePath) + arguments)
            .apply { environment()["SHELL"] = SH }
            .start()
        process.outputStream.use { it.write(input.toByteArray()) }
        assertTrue("sudo did not finish", process.waitFor(10, TimeUnit.SECONDS))
        return Run(
            process.exitValue(),
            process.inputStream.bufferedReader().readText(),
            process.errorStream.bufferedReader().readText(),
        )
    }

    @Test
    fun `runs the command as it is`() {
        assertEquals(Run(0, "hi\n", ""), run("echo", "hi"))
    }

    @Test
    fun `keeps the command's exit status`() {
        assertEquals(7, run("sh", "-c", "exit 7").code)
    }

    @Test
    fun `the command reads the same input, so a y answer reaches it`() {
        assertEquals("got:y\n", run("sh", "-c", "read answer; echo got:\$answer", input = "y\n").out)
    }

    @Test
    fun `accepts the options scripts commonly pass`() {
        assertEquals("hi\n", run("-E", "echo", "hi").out)
        assertEquals("hi\n", run("-EH", "echo", "hi").out)
        assertEquals("hi\n", run("-n", "-S", "--", "echo", "hi").out)
        assertEquals("hi\n", run("-u", "root", "echo", "hi").out)
        assertEquals("hi\n", run("-uroot", "--user=root", "echo", "hi").out)
    }

    @Test
    fun `refuses another user, since the runtime has only root`() {
        val result = run("-u", "nobody", "id")
        assertEquals(1, result.code)
        assertTrue(result.err, result.err.contains("one user, root"))
    }

    @Test
    fun `NAME=value arguments set the command's environment`() {
        assertEquals("bar\n", run("FOO=bar", "sh", "-c", "echo \$FOO").out)
    }

    @Test
    fun `a missing command is reported the way sudo does`() {
        val result = run("no-such-command-icode")
        assertEquals(1, result.code)
        assertEquals("sudo: no-such-command-icode: command not found\n", result.err)
    }

    @Test
    fun `no command is a usage error, but forgetting credentials alone succeeds`() {
        assertEquals(1, run().code)
        assertEquals(0, run("-k").code)
    }

    @Test
    fun `sudo -s runs the command through a shell`() {
        assertEquals("a b\n", run("-s", "echo", "a", "b").out)
    }

    @Test
    fun `an unsupported option is refused rather than ignored`() {
        val result = run("-Z", "echo", "hi")
        assertEquals(1, result.code)
        assertTrue(result.err, result.err.contains("-Z"))
    }

    private companion object {
        const val SH = "/bin/sh"
    }
}
