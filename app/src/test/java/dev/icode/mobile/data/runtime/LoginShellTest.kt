package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginShellTest {
    private val base = Files.createTempDirectory("shell").toFile()
    private val rootfs = File(base, "rootfs").apply { mkdirs() }
    private val paths = GuestPaths(rootfs, WorkspacePaths(File(base, "workspaces")))

    @After fun clean() {
        base.deleteRecursively()
    }

    private fun passwd(rootShell: String) = File(rootfs, "etc/passwd").apply { parentFile.mkdirs() }.writeText(
        "root:x:0:0:root:/root:$rootShell\ndaemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n",
    )

    private fun install(path: String) = File(rootfs, path.removePrefix("/")).apply { parentFile.mkdirs() }.writeText("")

    @Test fun `a shell inside the runtime is available`() {
        install("/bin/bash")
        assertTrue(RuntimeLayout.shellAvailable(paths, "/bin/bash"))
    }

    @Test fun `a missing shell, a folder, or a path out of the runtime is not`() {
        File(rootfs, "bin/zsh").mkdirs()
        File(base, "outside").writeText("")
        Files.createSymbolicLink(File(rootfs, "bin/escape").toPath(), File(base, "outside").toPath())
        assertFalse(RuntimeLayout.shellAvailable(paths, "/bin/fish"))
        assertFalse(RuntimeLayout.shellAvailable(paths, "/bin/zsh"))
        assertFalse(RuntimeLayout.shellAvailable(paths, "/bin/escape"))
        assertFalse(RuntimeLayout.shellAvailable(paths, "/bin/../../outside"))
    }

    @Test fun `root's login shell is the one etc-passwd records, as chsh leaves it`() {
        install("/usr/bin/zsh")
        passwd("/usr/bin/zsh")
        assertEquals("/usr/bin/zsh", RuntimeLayout.recordedShell(paths))
        assertEquals("/usr/bin/zsh", RuntimeLayout.loginShell(paths))
    }

    @Test fun `a shell installed behind a link made in the runtime is found, as the runtime finds it`() {
        install("/opt/fish/bin/fish")
        File(rootfs, "usr/local/bin").mkdirs()
        Files.createSymbolicLink(File(rootfs, "usr/local/bin/fish").toPath(), File("/opt/fish/bin/fish").toPath())
        passwd("/usr/local/bin/fish")
        assertEquals("/usr/local/bin/fish", RuntimeLayout.loginShell(paths))
    }

    @Test fun `a recorded shell that is not installed, or none at all, leaves bash`() {
        passwd("/usr/bin/fish")
        assertEquals("/usr/bin/fish", RuntimeLayout.recordedShell(paths))
        assertEquals("/bin/bash", RuntimeLayout.loginShell(paths))
        File(rootfs, "etc/passwd").delete()
        assertNull(RuntimeLayout.recordedShell(paths))
        assertEquals("/bin/bash", RuntimeLayout.loginShell(paths))
    }
}
