package dev.icode.mobile.data.files

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

class PlaceFilesTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private lateinit var root: File
    private lateinit var files: PlaceFiles

    @Before fun setUp() {
        root = temporaryFolder.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/a.kt").writeText("a")
        File(root, "docs").mkdirs()
        File(root, ".git").mkdirs()
        File(root, ".git/HEAD").writeText("ref")
        files = PlaceFiles(root, gitOwnsDotGit = true)
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

    @Test fun `move puts it exactly where it is asked, never into itself, never over another`() {
        files.move(path("src/a.kt"), path("docs/a.kt"))
        assertTrue(File(root, "docs/a.kt").isFile)
        assertFalse(File(root, "src/a.kt").exists())
        File(root, "docs/inner").mkdirs()
        assertThrows(IOException::class.java) { files.move(path("docs"), path("docs/inner/docs")) }
        File(root, "src/a.kt").writeText("other")
        assertThrows(IOException::class.java) { files.move(path("src/a.kt"), path("docs/a.kt")) }
        assertEquals("a", File(root, "docs/a.kt").readText())
    }

    @Test fun `a move back makes the folders that have gone since`() {
        files.move(path("src/a.kt"), path("gone/deeper/a.kt"))
        assertEquals("a", File(root, "gone/deeper/a.kt").readText())
    }

    @Test fun `a copy is made exactly as asked, never over another, never into itself`() {
        files.copy(path("src/a.kt"), path("src/b.kt"))
        assertEquals("a", File(root, "src/b.kt").readText())
        assertThrows(IOException::class.java) { files.copy(path("src/a.kt"), path("src/b.kt")) }
        files.copy(path("src"), path("lib"))
        assertEquals("a", File(root, "lib/a.kt").readText())
        assertThrows(IOException::class.java) { files.copy(path("src"), path("src/inner")) }
        assertTrue(File(root, "src/a.kt").isFile)
    }

    /** Private, rooted at the workspaces' folder: a workspace is an item at its top, `.git` anywhere Git's. */
    @Test fun `rooted at the workspaces, a folder moves between workspaces and out to the top`() {
        val workspaces = temporaryFolder.newFolder("workspaces")
        val app = File(workspaces, "app").apply { mkdirs() }
        File(app, "lib").mkdirs()
        File(app, "lib/x.kt").writeText("x")
        File(workspaces, "other").mkdirs()
        val all = PlaceFiles(workspaces, gitOwnsDotGit = true)

        all.move(File(app, "lib").path, File(workspaces, "other/lib").path)
        assertEquals("x", File(workspaces, "other/lib/x.kt").readText())
        assertFalse(File(app, "lib").exists())
        all.move(File(workspaces, "other/lib").path, File(workspaces, "lib").path)
        assertTrue(File(workspaces, "lib/x.kt").isFile)

        File(app, ".git").mkdirs()
        assertThrows(IOException::class.java) { all.copy(File(workspaces, "lib").path, File(app, ".git/lib").path) }
        assertThrows(IOException::class.java) { all.move(workspaces.path, File(workspaces, "other/all").path) }
        assertEquals("The .git folder belongs to Git", all.refusal(File(app, ".git/HEAD").path))
        assertEquals("This folder itself cannot be changed here", all.refusal(workspaces.path))
        assertEquals(null, all.refusal(app.path))
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

    @Test fun `a delete is counted first and reports each entry from the folder it is in`() {
        File(root, "src/lib").mkdirs()
        File(root, "src/lib/b.kt").writeText("b")
        assertEquals(4, files.count(path("src")))
        val reported = mutableListOf<String>()

        files.delete(path("src")) { reported += it }

        assertEquals(setOf("src/a.kt", "src/lib/b.kt", "src/lib", "src"), reported.toSet())
        assertEquals(4, reported.size)
        assertEquals("src", reported.last())
        assertFalse(File(root, "src").exists())
    }

    @Test fun `a copy reports each entry, and one stopped midway leaves nothing half-made`() {
        File(root, "src/b.kt").writeText("b")
        val reported = mutableListOf<String>()
        files.copy(path("src"), path("docs/src")) { reported += it }
        assertEquals(setOf("src", "src/a.kt", "src/b.kt"), reported.toSet())
        assertTrue(File(root, "docs/src/b.kt").isFile)

        var entries = 0
        assertThrows(IllegalStateException::class.java) {
            files.copy(path("src"), path("docs/src copy")) { if (++entries == 2) throw IllegalStateException("Cancelled") }
        }
        assertFalse(File(root, "docs/src copy").exists())
        assertEquals("a", File(root, "src/a.kt").readText())
    }

    @Test fun `create makes an empty folder or file, never over one, and never in Git's own folder`() {
        assertTrue(files.create(path("src"), "lib", isFolder = true).isDirectory)
        assertEquals(0L, files.create(path("src"), "b.kt", isFolder = false).length())
        assertThrows(IOException::class.java) { files.create(path("src"), "a.kt", isFolder = false) }
        assertEquals("a", File(root, "src/a.kt").readText())
        assertThrows(IOException::class.java) { files.create(path(".git"), "hooks", isFolder = true) }
    }

    @Test fun `on phone storage a dot-git folder is the user's, and nothing leaves it`() {
        val phone = PlaceFiles(root, gitOwnsDotGit = false)
        assertTrue(phone.create(path(".git"), "notes", isFolder = true).isDirectory)
        phone.delete(path(".git"))
        assertFalse(File(root, ".git").exists())
        val outside = temporaryFolder.newFolder("outside")
        assertThrows(IOException::class.java) { phone.copy(path("src"), File(outside, "src").path) }
        assertThrows(IOException::class.java) { phone.rename(root.path, "other") }
    }

    @Test fun `a free name is the name, then name copy, then name copy 2, a file's extension last`() {
        val folder = root.toPath().resolve("src")
        assertEquals("b.kt", freeName(folder, "b.kt", isDirectory = false).fileName.toString())
        assertEquals("a copy.kt", freeName(folder, "a.kt", isDirectory = false).fileName.toString())
        File(root, "src/a copy.kt").writeText("")
        assertEquals("a copy 2.kt", freeName(folder, "a.kt", isDirectory = false).fileName.toString())
        assertEquals("src copy", freeName(root.toPath(), "src", isDirectory = true).fileName.toString())
    }
}
