package dev.icode.mobile.data.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorkspaceStagingTest {
    @get:Rule val temp = TemporaryFolder()

    private val root by lazy { File(temp.root, "workspaces") }
    private val staging by lazy { WorkspaceStaging(root) }

    @Test fun `a staging folder is iCode's own and never one in use`() {
        val first = staging.reserve("clone").apply { mkdir() }
        val second = staging.reserve("clone")
        assertTrue(first.name.startsWith(".clone-"))
        assertNotEquals(first, second)
    }

    @Test fun `a complete one becomes the workspace in one rename, and never over one made meanwhile`() {
        val folder = staging.reserve("import").apply { mkdir() }
        File(folder, "notes.md").writeText("kept")
        val workspace = staging.complete(folder, "notes")
        assertEquals("kept", File(workspace, "notes.md").readText())
        assertFalse(folder.exists())

        val late = staging.reserve("import").apply { mkdir() }
        assertTrue(runCatching { staging.complete(late, "notes") }.isFailure)
        assertEquals("kept", File(workspace, "notes.md").readText())
    }

    @Test fun `a project can land in a workspace's folder, checked where it lands, never outside or in Git's`() {
        val tools = File(root, "tools/cli").apply { mkdirs() }
        File(tools, "taken").mkdir()
        assertEquals(null, staging.nameProblem("api", tools))
        assertEquals("“taken” is already in cli", staging.nameProblem("taken", tools))
        assertEquals("A workspace named tools already exists", staging.nameProblem("tools"))

        val folder = staging.reserve("clone").apply { mkdir() }
        File(folder, "README.md").writeText("api")
        val project = staging.complete(folder, "api", tools)
        assertEquals(File(tools, "api"), project)
        assertEquals("api", File(project, "README.md").readText())

        val outside = temp.newFolder("elsewhere")
        val stray = staging.reserve("clone").apply { mkdir() }
        assertTrue(runCatching { staging.complete(stray, "x", outside) }.isFailure)
        val git = File(root, "tools/.git").apply { mkdirs() }
        assertTrue(runCatching { staging.complete(stray, "x", git) }.isFailure)
        assertTrue("a refused one is left for the caller to discard", stray.exists())
    }

    @Test fun `what an ended process left goes at the next start, and nothing else`() {
        val left = File(root, ".clone-17").apply { mkdirs() }
        File(left, "half").writeText("x")
        File(root, ".import-3").mkdirs()
        val workspace = File(root, "app").apply { mkdirs() }
        val hidden = File(root, ".cache").apply { mkdirs() }
        val outside = temp.newFolder("outside")
        File(outside, "precious").writeText("safe")
        Files.createSymbolicLink(File(root, ".import-4").toPath(), outside.toPath())
        staging.sweep()
        assertEquals(listOf(".cache", "app"), root.list()!!.sorted())
        assertTrue(workspace.isDirectory && hidden.isDirectory)
        assertEquals("safe", File(outside, "precious").readText())
    }
}
