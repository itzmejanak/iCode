package dev.icode.mobile.data.runtime

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What iCode writes into the runtime says so (docs/runtime-and-terminal.md), and the README says what the runtime is. */
class GuestSystemFilesTest {
    private val rootfs = Files.createTempDirectory("system-files").toFile()

    @After fun clean() {
        rootfs.deleteRecursively()
    }

    @Test fun `each base-system file iCode writes opens with the marker, then what it sets`() {
        GuestSystemFiles.write(rootfs)
        GuestSystemFiles.FILES.forEach { (path, text) ->
            val lines = File(rootfs, path).readLines()
            assertTrue(path, lines.first().startsWith("# ${RuntimeLayout.MANAGED} "))
            assertEquals(path, text, File(rootfs, path).readText())
        }
        assertEquals(listOf("nameserver 8.8.8.8", "nameserver 8.8.4.4"), File(rootfs, "etc/resolv.conf").readLines().drop(1))
        assertEquals(listOf("[core]", "\tcreateObject = rename"), File(rootfs, "etc/gitconfig").readLines().drop(1))
    }

    @Test fun `the README is iCode's, and names every context there is`() {
        val readme = File("src/main/assets/runtime/README.md").readText()
        assertTrue(readme.lines().first(), readme.lines().first().startsWith("<!-- ${RuntimeLayout.MANAGED} "))
        GuestContext.entries.forEach { assertTrue(it.id, "| `${it.id}` |" in readme) }
        assertTrue("grep -rl \"${RuntimeLayout.MANAGED.removeSuffix(":")}\"" in readme)
    }
}
