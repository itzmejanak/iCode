package dev.icode.mobile.data.workspace

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var files: WorkspaceFiles

    @Before fun setUp() {
        root = temporaryFolder.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/a.kt").writeText("a")
        File(root, "docs").mkdirs()
        File(root, ".git").mkdirs()
        File(root, ".git/HEAD").writeText("ref")
        files = WorkspaceFiles(root)
    }

    private fun path(relative: String) = File(root, relative).path

    @Test fun `rename keeps the item in its folder and refuses a taken name`() {
        val renamed = files.rename(path("src/a.kt"), "b.kt")
        assertEquals(File(root, "src/b.kt").canonicalPath, renamed.canonicalPath)
        assertEquals("a", renamed.readText())
        File(root, "src/c.kt").writeText("c")
        assertThrows(IOException::class.java) { files.rename(path("src/b.kt"), "c.kt") }
        assertEquals("c", File(root, "src/c.kt").readText())
    }

    @Test fun `move puts it in another folder, never into itself, never over another`() {
        files.move(path("src/a.kt"), path("docs"))
        assertTrue(File(root, "docs/a.kt").isFile)
        assertFalse(File(root, "src/a.kt").exists())
        assertThrows(IOException::class.java) { files.move(path("docs"), path("docs")) }
        File(root, "docs/inner").mkdirs()
        assertThrows(IOException::class.java) { files.move(path("docs"), path("docs/inner")) }
        File(root, "src/a.kt").writeText("other")
        assertThrows(IOException::class.java) { files.move(path("src/a.kt"), path("docs")) }
        assertEquals("a", File(root, "docs/a.kt").readText())
    }

    @Test fun `a copy that would collide becomes name copy, then name copy 2`() {
        assertEquals("a copy.kt", files.copy(path("src/a.kt"), path("src")).name)
        assertEquals("a copy 2.kt", files.copy(path("src/a.kt"), path("src")).name)
        val folderCopy = files.copy(path("src"), path(""))
        assertEquals("src copy", folderCopy.name)
        assertEquals("a", File(folderCopy, "a.kt").readText())
        assertTrue(File(root, "src/a.kt").isFile)
    }

    @Test fun `a folder cannot be copied into itself`() {
        assertThrows(IOException::class.java) { files.copy(path("src"), path("src")) }
    }

    @Test fun `delete removes a folder and everything in it`() {
        File(root, "src/deep/deeper").mkdirs()
        File(root, "src/deep/deeper/z.txt").writeText("z")
        files.delete(path("src"))
        assertFalse(File(root, "src").exists())
    }

    @Test fun `deleting a link removes the link, never what it points to`() {
        val outside = temporaryFolder.newFile("outside.txt").apply { writeText("keep me") }
        Files.createSymbolicLink(File(root, "link.txt").toPath(), outside.toPath())
        files.delete(path("link.txt"))
        assertFalse(Files.exists(File(root, "link.txt").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS))
        assertEquals("keep me", outside.readText())
    }

    @Test fun `the root, Git's folder and anything outside are refused`() {
        assertThrows(IOException::class.java) { files.delete(root.path) }
        assertThrows(IOException::class.java) { files.delete(path(".git")) }
        assertThrows(IOException::class.java) { files.delete(path(".git/HEAD")) }
        assertThrows(IOException::class.java) { files.rename(path("src/a.kt"), ".git") }
        assertThrows(IOException::class.java) { files.move(path("src/a.kt"), path(".git")) }
        val outside = temporaryFolder.newFile("elsewhere.txt")
        assertThrows(IOException::class.java) { files.delete(outside.path) }
        assertThrows(IOException::class.java) { files.delete(path("src/../../elsewhere.txt")) }
        assertTrue(outside.exists())
        assertTrue(File(root, ".git/HEAD").isFile)
    }

    @Test fun `a location follows its folder, and only its folder`() {
        assertTrue("/w/src/a.kt".isAtOrUnder("/w/src"))
        assertTrue("/w/src".isAtOrUnder("/w/src"))
        assertFalse("/w/srcx/a.kt".isAtOrUnder("/w/src"))
        assertEquals("/w/lib/a.kt", "/w/src/a.kt".relocated("/w/src", "/w/lib"))
        assertEquals("/w/srcx/a.kt", "/w/srcx/a.kt".relocated("/w/src", "/w/lib"))
    }
}
