package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.storage.LogicalFiles
import dev.icode.mobile.domain.LogicalWorkspace
import dev.icode.mobile.domain.StorageClass
import dev.icode.mobile.domain.WorkspaceMount
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
    private lateinit var private: File
    private lateinit var files: WorkspaceFiles

    @Before fun setUp() {
        root = temporaryFolder.newFolder("project")
        File(root, "src").mkdirs()
        File(root, "src/a.kt").writeText("a")
        File(root, "docs").mkdirs()
        File(root, ".git").mkdirs()
        File(root, ".git/HEAD").writeText("ref")
        private = temporaryFolder.newFolder("private")
        files = withMounts()
    }

    private fun path(relative: String) = File(root, relative).path

    /** The workspace with LINUX mounts at [paths], each private folder made ready as WorkspaceStorage does. */
    private fun withMounts(vararg paths: String): WorkspaceFiles {
        val workspace = LogicalWorkspace(root.path, private.path, paths.map { WorkspaceMount(it, StorageClass.LINUX) })
        workspace.mounts.forEach { File(workspace.privatePath(it)).mkdirs() }
        return WorkspaceFiles(LogicalFiles(workspace))
    }

    /** Where a mounted path's bytes are. */
    private fun kept(relative: String) = File(private, "linux/$relative")

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
        files.copy(path("src"), path("docs")) { reported += it }
        assertEquals(setOf("src", "src/a.kt", "src/b.kt"), reported.toSet())
        assertTrue(File(root, "docs/src/b.kt").isFile)

        var entries = 0
        assertThrows(IllegalStateException::class.java) {
            files.copy(path("src"), path("docs")) { if (++entries == 2) throw IllegalStateException("Cancelled") }
        }
        assertFalse(File(root, "docs/src copy").exists())
        assertEquals("a", File(root, "src/a.kt").readText())
    }

    @Test fun `a mount, and a folder holding one, stays where it is, while inside a mount things move`() {
        File(root, "packages/app").mkdirs()
        val files = withMounts("node_modules", "packages/app/node_modules")
        assertThrows(IOException::class.java) { files.rename(path("node_modules"), "nm") }
        assertThrows(IOException::class.java) { files.move(path("node_modules"), path("docs")) }
        assertThrows(IOException::class.java) { files.rename(path("packages"), "pkgs") }
        assertThrows(IOException::class.java) { files.move(path("packages/app"), path("docs")) }
        assertThrows(IOException::class.java) { files.rename(path("src"), "node_modules") }
        assertTrue(File(root, "packages/app").isDirectory)

        kept("node_modules/a.txt").writeText("a")
        assertEquals(path("node_modules/b.txt"), files.rename(path("node_modules/a.txt"), "b.txt").path)
        assertEquals("a", kept("node_modules/b.txt").readText())
        assertFalse(File(root, "node_modules/b.txt").exists())
    }

    @Test fun `a move across the storage boundary copies then deletes, both ways, saying each entry twice`() {
        val files = withMounts("node_modules")
        File(root, "src/lib").mkdirs()
        File(root, "src/lib/b.kt").writeText("b")
        assertTrue(files.crossesStorage(path("src/lib"), path("node_modules")))
        assertFalse(files.crossesStorage(path("src/lib"), path("docs")))
        val reported = mutableListOf<String>()

        assertEquals(path("node_modules/lib"), files.move(path("src/lib"), path("node_modules")) { reported += it }.path)

        assertEquals("b", kept("node_modules/lib/b.kt").readText())
        assertFalse(File(root, "src/lib").exists())
        assertEquals(listOf("lib", "lib/b.kt", "lib/b.kt", "lib"), reported)
        assertEquals(2, files.count(path("node_modules/lib")))

        assertTrue(files.crossesStorage(path("node_modules/lib"), path("docs")))
        files.move(path("node_modules/lib"), path("docs"))
        assertEquals("b", File(root, "docs/lib/b.kt").readText())
        assertFalse(kept("node_modules/lib").exists())
    }

    @Test fun `a copy and a delete see a mount's contents, and a folder deleted takes its placeholder with it`() {
        File(root, "packages/app/node_modules").mkdirs()
        File(root, "packages/app/index.js").writeText("i")
        val files = withMounts("packages/app/node_modules")
        kept("packages/app/node_modules/semver").mkdirs()
        kept("packages/app/node_modules/semver/x.js").writeText("x")
        assertEquals(6, files.count(path("packages")))

        files.copy(path("packages"), path("docs"))
        assertEquals("x", File(root, "docs/packages/app/node_modules/semver/x.js").readText())

        val reported = mutableListOf<String>()
        files.delete(path("packages")) { reported += it }
        assertFalse(File(root, "packages").exists())
        assertFalse(kept("packages/app/node_modules").exists())
        assertEquals(6, reported.size)
    }

    @Test fun `deleting a mount empties it, and a placeholder holding the user's own files is never deleted unseen`() {
        val files = withMounts("node_modules", "app/node_modules")
        kept("node_modules/semver").mkdirs()
        files.delete(path("node_modules"))
        assertFalse(kept("node_modules").exists())

        File(root, "app/node_modules").mkdirs()
        File(root, "app/node_modules/mine.txt").writeText("mine")
        assertThrows(IOException::class.java) { files.delete(path("app")) }
        assertEquals("mine", File(root, "app/node_modules/mine.txt").readText())
    }
}
