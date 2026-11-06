package dev.icode.mobile.data.runtime

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/agent-control-plan.md §4.1: a terminal's commands from its marks. The stream is what bash 5.2
 * printed in a pty with /etc/icode/bashrc and iCode's prompt: a three-line paste (`cd /tmp`, `echo
 * two`, `ls /nonexistent`), an empty Enter, `sleep 30` stopped with Ctrl-C, `printf 'a\tb\n'`.
 */
class TerminalCommandsTest {
    private val finished = mutableListOf<TerminalCommands.Command>()
    private val started = mutableListOf<TerminalCommands.Command>()

    private fun commands(chunk: Int = Int.MAX_VALUE): TerminalCommands {
        val commands = TerminalCommands { finished += it }
        val bytes = RECORDED.toByteArray(Charsets.UTF_8)
        var at = 0
        while (at < bytes.size) {
            val next = commands.nextCommand()
            val count = minOf(chunk, bytes.size - at)
            commands.feed(bytes.copyOfRange(at, at + count), count)
            if (next.isCompleted) started += next.getCompleted()
            at += count
        }
        return commands
    }

    @Test
    fun `each command is its output from its C to its D, its code and where it ended`() {
        val commands = commands()
        assertTrue(commands.integrated)
        assertEquals(listOf(2, 130, 0), finished.map { it.exitCode })
        // The three lines are one command; the empty Enter is none.
        assertEquals("two\nls: cannot access '/nonexistent': No such file or directory\n", finished[0].text())
        assertEquals("/tmp", finished[0].directory)
        assertEquals("a\tb\n", finished[2].text())
        // At the end a prompt waits, nothing typed at it.
        assertTrue(commands.ready)
        assertFalse(commands.running)
        assertEquals("/tmp", commands.directory)
    }

    @Test
    fun `read a byte at a time, it is the same`() {
        commands(chunk = 1)
        assertEquals(listOf(2, 130, 0), finished.map { it.exitCode })
        // Each start is the command that ends next.
        assertEquals(finished.map { it.number }, started.map { it.number })
        assertEquals("two\nls: cannot access '/nonexistent': No such file or directory\n", finished[0].text())
    }

    @Test
    fun `what is printed is kept as printed, for the replay`() {
        commands()
        assertEquals("two\r\nls: cannot access '/nonexistent': No such file or directory\r\n", finished[0].printed())
        assertEquals("^C\r\n", finished[1].printed())
    }

    @Test
    fun `typing at the prompt makes it busy, and a shell that prints no marks is not integrated`() {
        val commands = TerminalCommands()
        commands.feed(PROMPT.toByteArray(), PROMPT.length)
        assertTrue(commands.ready)
        commands.feed("l".toByteArray(), 1)
        assertFalse(commands.ready)
        val plain = TerminalCommands()
        plain.feed("$ ls\r\n".toByteArray(), 6)
        assertFalse(plain.integrated)
        assertFalse(plain.ready)
    }

    @Test
    fun `a session that ends ends its command, its code unknown`() = runBlocking {
        val commands = TerminalCommands()
        val next = commands.nextCommand()
        val bytes = (PROMPT + "\u001B]133;C\u0007working").toByteArray()
        commands.feed(bytes, bytes.size)
        val command = next.await()
        assertTrue(command.running)
        commands.close()
        assertNull(command.await())
        assertEquals("working", command.text())
    }

    private companion object {
        const val PROMPT = "\u001B]133;A\u0007$ \u001B]133;B\u0007"
        val RECORDED =
            "\u001B]7;file://localhost/home/user/icodeTest\u0007\u001B]133;D;0\u0007\u001B]133;A\u0007\u001B[?2004h\u001B[38;5;258m/home/user/icodeTest\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007\u001B[7mcd /tmp\u001B[27m\r\n" +
            "\r\u001B[7mecho two\u001B[27m\r\n" +
            "\r\u001B[7mls /nonexistent\u001B[27m\u001B[A\u001B[A\r\u001B[38;5;258m/home/user/icodeTest\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007cd /tmp\r\n" +
            "\recho two\r\n" +
            "\rls /nonexistent\r\n" +
            "\u001B[?2004l\r\u001B]133;C\u0007\u001B]133;C\u0007two\r\n" +
            "\u001B]133;C\u0007ls: cannot access '/nonexistent': No such file or directory\r\n" +
            "\u001B]7;file://localhost/tmp\u0007\u001B]133;D;2\u0007\u001B]133;A\u0007\u001B[?2004h\u001B[38;5;258m/tmp\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007\u001B[K\r\n" +
            "\u001B[?2004l\r\u001B]7;file://localhost/tmp\u0007\u001B]133;D;2\u0007\u001B]133;A\u0007\u001B[?2004h\u001B[38;5;258m/tmp\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007\u001B[7msleep 30\u001B[27m\b\b\b\b\b\b\b\bsleep 30\r\n" +
            "\u001B[?2004l\r\u001B]133;C\u0007^C\r\n" +
            "\u001B]7;file://localhost/tmp\u0007\u001B]133;D;130\u0007\u001B]133;A\u0007\u001B[?2004h\u001B[38;5;258m/tmp\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007\u001B[7mprintf 'a\\tb\\n'\u001B[27m\b\b\b\b\b\b\b\b\b\b\b\b\b\b\bprintf 'a\\tb\\n'\r\n" +
            "\u001B[?2004l\r\u001B]133;C\u0007a\tb\r\n" +
            "\u001B]7;file://localhost/tmp\u0007\u001B]133;D;0\u0007\u001B]133;A\u0007\u001B[?2004h\u001B[38;5;258m/tmp\u001B[0m \u001B[32m\$\u001B[0m \u001B]133;B\u0007"
    }
}
