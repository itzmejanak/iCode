package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Links as PRoot stores them on the phone (docs/runtime-contract-plan.md §1.3): runtime paths, and its hard-link stand-ins' phone paths. */
class GuestPathsTest {
    private val base = Files.createTempDirectory("paths").toFile().canonicalFile
    private val rootfs = File(base, "files/runtime/rootfs").apply { mkdirs() }
    private val workspaces = File(base, "files/workspaces").apply { mkdirs() }
    private val paths = GuestPaths(rootfs, WorkspacePaths(workspaces))

    @After fun clean() {
        base.deleteRecursively()
    }

    private fun file(at: File, text: String = "") = at.apply { parentFile.mkdirs(); writeText(text) }
    private fun link(at: File, target: String) = at.apply { parentFile.mkdirs() }.also { Files.createSymbolicLink(it.toPath(), File(target).toPath()) }

    @Test fun `each part of the tree is where every launch binds it`() {
        assertEquals(File(workspaces, "app/src/Main.kt"), paths.hostOf("/workspace/app/src/Main.kt"))
        assertEquals(workspaces, paths.hostOf("/workspace"))
        assertEquals(File(rootfs, "etc/hosts"), paths.hostOf("/etc/hosts"))
        assertEquals(File(rootfs, "root/.bashrc"), paths.hostOf("/root/./x/../.bashrc"))
        assertEquals(rootfs, paths.hostOf("/"))
        assertEquals(File("/proc/version"), paths.hostOf("/proc/version"))
    }

    @Test fun `phone storage is bound where Android has it, and sdcard names it`() {
        val shared = File(base, "storage/emulated/0").apply { mkdirs() }
        val withStorage = GuestPaths(rootfs, WorkspacePaths(workspaces), shared)
        link(File(rootfs, "sdcard"), shared.path)
        assertEquals(File(shared, "DCIM/a.jpg"), withStorage.hostOf("${shared.path}/DCIM/a.jpg"))
        assertEquals(File(shared, "DCIM/a.jpg"), withStorage.hostOf("/sdcard/DCIM/a.jpg"))
        assertEquals("${shared.path}/DCIM", withStorage.guestOf("${shared.path}/DCIM/"))
        assertTrue(withStorage.inShared(File(shared, "DCIM")))
        assertFalse(withStorage.inShared(File(rootfs, "root")))
        assertFalse(paths.inShared(File(shared, "DCIM")))
    }

    @Test fun `a link made in the runtime names a runtime path, and is followed inside the runtime`() {
        file(File(rootfs, "usr/lib/python3.12/python"), "elf")
        link(File(rootfs, "usr/bin/python3"), "/usr/lib/python3.12/python")
        assertEquals(File(rootfs, "usr/lib/python3.12/python"), paths.hostOf("/usr/bin/python3"))
        // Into a workspace, and on through it.
        file(File(workspaces, "app/README.md"), "hello")
        link(File(rootfs, "root/projects/app"), "/workspace/app")
        assertEquals(File(workspaces, "app/README.md"), paths.hostOf("/root/projects/app/README.md"))
        // Out of a workspace into the runtime: a virtual environment's interpreter.
        link(File(workspaces, "app/venv/bin/python"), "/usr/bin/python3")
        assertEquals(File(rootfs, "usr/lib/python3.12/python"), paths.hostOf("/workspace/app/venv/bin/python"))
    }

    @Test fun `a relative link is followed from its own folder`() {
        file(File(workspaces, "other/README.md"))
        link(File(workspaces, "app/docs"), "../other")
        assertEquals(File(workspaces, "other/README.md"), paths.hostOf("/workspace/app/docs/README.md"))
    }

    @Test fun `PRoot's stand-in for a hard link names the phone's path, and is followed as it is`() {
        val kept = file(File(workspaces, "app/.l2s.a0001.0002"), "data")
        link(File(workspaces, "app/.l2s.a0001"), kept.path)
        link(File(workspaces, "app/a"), File(workspaces, "app/.l2s.a0001").path)
        assertEquals(kept, paths.hostOf("/workspace/app/a"))
        val inRuntime = file(File(rootfs, "usr/share/doc/.l2s.x0001.0002"))
        link(File(rootfs, "usr/share/doc/x"), inRuntime.path)
        assertEquals(inRuntime, paths.hostOf("/usr/share/doc/x"))
    }

    @Test fun `inside a workspace is decided with its links followed as in the runtime`() {
        val app = File(workspaces, "app").apply { mkdirs() }
        file(File(app, "src/real.kt"))
        link(File(app, "alias.kt"), "/workspace/app/src/real.kt")
        link(File(app, "away.kt"), "/etc/hosts")
        assertEquals(File("src/real.kt").toPath(), paths.within(app.path, File(app, "alias.kt").path))
        assertNull(paths.within(app.path, File(app, "away.kt").path))
        assertNull(paths.within(app.path, File(workspaces, "other/x.kt").path))
    }

    @Test fun `a loop is told as Linux tells it`() {
        link(File(rootfs, "tmp/a"), "/tmp/b")
        link(File(rootfs, "tmp/b"), "/tmp/a")
        assertThrows(IOException::class.java) { paths.hostOf("/tmp/a") }
    }

    @Test fun `a file on the phone has its runtime path, where the runtime holds it`() {
        assertEquals("/workspace/app/src", paths.guestOf(File(workspaces, "app/src").path))
        assertEquals("/etc/hosts", paths.guestOf(File(rootfs, "etc/hosts").path))
        assertEquals("/", paths.guestOf(rootfs.path))
        assertEquals("/proc/self", paths.guestOf("/proc/self"))
        assertNull(paths.guestOf("/storage/emulated/0/Download"))
    }

    @Test fun `following a phone path resolves runtime links, and a path the runtime does not hold as the phone does`() {
        file(File(workspaces, "app/src/real.kt"))
        link(File(workspaces, "app/alias.kt"), "/workspace/app/src/real.kt")
        assertEquals(File(workspaces, "app/src/real.kt"), paths.follow(File(workspaces, "app/alias.kt").path))
        // Java's canonicalFile reads the same link as a phone path, which does not exist: it never reaches the file.
        assertNotEquals(File(workspaces, "app/src/real.kt"), File(workspaces, "app/alias.kt").canonicalFile)
        val outside = File(base, "storage").apply { mkdirs() }
        assertEquals(outside.canonicalFile, paths.follow(outside.path))
    }
}
