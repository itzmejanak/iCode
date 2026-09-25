package dev.icode.mobile.data.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgramNameTest {

    @Test
    fun `an interpreter running a script is named by the script`() {
        // Captured on the device: freebuff's foreground leader, whose thread name is "MainThread".
        assertEquals("freebuff", ProgramName.of(listOf("node", "/usr/local/bin/freebuff")))
        assertEquals("claude", ProgramName.of(listOf("/usr/bin/env", "node", "/usr/local/bin/claude")))
        assertEquals("server.py", ProgramName.of(listOf("python3", "-u", "server.py")))
        assertEquals("http.server", ProgramName.of(listOf("python3.12", "-m", "http.server", "8000")))
        assertEquals("build.sh", ProgramName.of(listOf("bash", "build.sh")))
    }

    @Test
    fun `a program run directly keeps its own name`() {
        assertEquals("freebuff", ProgramName.of(listOf("/root/.config/manicode/freebuff")))
        assertEquals("sleep", ProgramName.of(listOf("sleep", "60")))
        assertEquals("npm run dev", ProgramName.of(listOf("npm run dev")))
    }

    @Test
    fun `an interpreter or shell on its own is the program`() {
        assertEquals("bash", ProgramName.of(listOf("/bin/bash", "-l")))
        assertEquals("node", ProgramName.of(listOf("node")))
        assertEquals("env", ProgramName.of(listOf("env", "FOO=1")))
    }

    @Test
    fun `a shell given a command string is the shell`() {
        // Captured on the device: freebuff's terminal-reset helper.
        val argv = listOf("/bin/sh", "-c", "cat >/dev/null 2>&1; printf '\\033[?1049l'", "terminal-reset-watchdog")
        assertEquals("sh", ProgramName.of(argv))
    }

    @Test
    fun `nothing readable is no name, and names are cleaned`() {
        assertNull(ProgramName.of(emptyList()))
        assertNull(ProgramName.of(listOf("")))
        assertEquals("evil", ProgramName.clean("\u001b[31mevil\u0007".replace("[31m", "")))
        assertEquals(32, ProgramName.clean("x".repeat(80))?.length)
    }
}
