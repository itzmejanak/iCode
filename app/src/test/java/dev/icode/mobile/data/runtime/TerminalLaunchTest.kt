package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalLaunchTest {
    private val base = Files.createTempDirectory("launch").toFile()
    private val rootfs = File(base, "rootfs").apply { mkdirs() }

    @After fun clean() {
        base.deleteRecursively()
    }

    @Test fun `a shell inside the runtime is available`() {
        File(rootfs, "bin").mkdirs()
        File(rootfs, "bin/bash").writeText("")
        assertTrue(TerminalLaunch.shellAvailable(rootfs, "/bin/bash"))
    }

    @Test fun `a missing shell, a folder, or a path out of the runtime is not`() {
        File(rootfs, "bin/zsh").mkdirs()
        File(base, "outside").writeText("")
        Files.createSymbolicLink(File(rootfs, "bin/escape").toPath(), File(base, "outside").toPath())
        assertFalse(TerminalLaunch.shellAvailable(rootfs, "/bin/fish"))
        assertFalse(TerminalLaunch.shellAvailable(rootfs, "/bin/zsh"))
        assertFalse(TerminalLaunch.shellAvailable(rootfs, "/bin/escape"))
        assertFalse(TerminalLaunch.shellAvailable(rootfs, "/bin/../../outside"))
    }

    @Test fun `bash starts as a login shell, others as they are`() {
        assertEquals(listOf("/bin/bash", "-l"), TerminalLaunch.shellCommand("/bin/bash"))
        assertEquals(listOf("/usr/bin/zsh"), TerminalLaunch.shellCommand("/usr/bin/zsh"))
    }
}
