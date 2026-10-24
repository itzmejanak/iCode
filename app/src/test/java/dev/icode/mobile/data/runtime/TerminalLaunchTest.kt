package dev.icode.mobile.data.runtime

import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class TerminalLaunchTest {
    @Test fun `root's login shell starts as a login shell, whichever it is`() {
        assertEquals(listOf("/bin/bash", "-l"), TerminalLaunch.shellCommand("/bin/bash", "/bin/bash"))
        assertEquals(listOf("/usr/bin/zsh", "-l"), TerminalLaunch.shellCommand("/usr/bin/zsh", "/usr/bin/zsh"))
        assertEquals(listOf("/bin/bash", "-l"), TerminalLaunch.shellCommand(null, "/bin/bash"))
    }

    @Test fun `a recorded shell that is not installed leaves bash, and the tab's first line says why`() {
        val command = TerminalLaunch.shellCommand("/usr/bin/zsh", "/bin/bash")
        assertEquals("iCode: your login shell /usr/bin/zsh (from /etc/passwd) is not installed — `apt install zsh`, or `chsh -s /bin/bash`", command[4])
        assertEquals(listOf("/bin/bash", "-l"), command.takeLast(2))
        assumeTrue(java.io.File("/bin/sh").canExecute())
        // As the runtime runs it: the line, then the login shell — here one that says how it was started.
        val process = ProcessBuilder(command.take(4) + "why" + listOf("/bin/echo", "started", "-l")).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        process.waitFor(10, TimeUnit.SECONDS)
        assertEquals("why\nstarted -l\n", output)
    }
}
