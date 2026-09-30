package dev.icode.mobile.data.git

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Built from the layouts Git itself writes: a `.git` folder, a submodule's and a worktree's `.git` file. */
class GitLocatorTest {
    private val files = Files.createTempDirectory("files").toFile().canonicalFile
    private val root = File(files, "workspaces").apply { mkdirs() }
    private val locator = GitLocator(WorkspacePaths(root))

    @After fun clean() {
        files.deleteRecursively()
    }

    private fun folder(path: String) = File(root, path).apply { mkdirs() }

    private fun repository(path: String, head: String = "ref: refs/heads/main") = folder(path).also {
        File(it, ".git").mkdirs()
        File(it, ".git/HEAD").writeText("$head\n")
    }

    @Test fun `a folder outside any repository has no top and no branch`() {
        val plain = folder("notes")
        assertNull(locator.topOf(plain.path))
        assertNull(locator.branchOf(plain.path))
        assertFalse(locator.isRepository(plain.path))
    }

    @Test fun `a repository is found at its top, and from a folder inside it`() {
        val app = repository("app")
        val inside = folder("app/src/main")
        assertEquals(app, locator.topOf(app.path))
        assertEquals(app, locator.topOf(inside.path))
        assertEquals("main", locator.branchOf(inside.path))
        assertTrue(locator.isRepository(inside.path))
    }

    @Test fun `the nearest repository wins over the one around it`() {
        repository("outer")
        val inner = repository("outer/vendor/lib", head = "ref: refs/heads/stable")
        assertEquals(inner, locator.topOf(inner.path))
        assertEquals("stable", locator.branchOf(inner.path))
    }

    @Test fun `a detached HEAD names no branch`() {
        val app = repository("app", head = "7d466e4c47d969352f2bc7f569d10ef8b72d2170")
        assertNull(locator.branchOf(app.path))
        assertTrue(locator.isRepository(app.path))
    }

    @Test fun `a submodule is its own top, its history in the superproject`() {
        folder("app/.git/modules/lib")
        File(root, "app/.git/modules/lib/HEAD").writeText("ref: refs/heads/lib-main\n")
        val submodule = folder("app/lib").also { File(it, ".git").writeText("gitdir: ../.git/modules/lib\n") }
        assertEquals(submodule, locator.topOf(submodule.path))
        assertEquals("lib-main", locator.branchOf(submodule.path))
    }

    @Test fun `a linked worktree names its Git folder by its path in the runtime`() {
        repository("app")
        folder("app/.git/worktrees/feature")
        File(root, "app/.git/worktrees/feature/HEAD").writeText("ref: refs/heads/feature\n")
        val worktree = folder("app-feature").also { File(it, ".git").writeText("gitdir: /workspace/app/.git/worktrees/feature\n") }
        assertEquals(worktree, locator.topOf(worktree.path))
        assertEquals("feature", locator.branchOf(worktree.path))
    }

    @Test fun `a git file naming a folder this phone lacks is no repository to read`() {
        val copied = folder("copied").also { File(it, ".git").writeText("gitdir: /workspace/gone/.git/worktrees/copied\n") }
        assertNull(locator.branchOf(copied.path))
        assertFalse(locator.isRepository(copied.path))
    }

    @Test fun `nothing above the workspaces counts`() {
        File(files, ".git").mkdirs()
        File(files, ".git/HEAD").writeText("ref: refs/heads/main\n")
        val plain = folder("notes")
        assertNull(locator.topOf(plain.path))
        assertNull(locator.branchOf(plain.path))
    }

    @Test fun `a workspace's repositories are the one it is in, then those in its folders, as far as three down`() {
        val projects = folder("projects")
        val api = repository("projects/api")
        val cli = repository("projects/tools/cli")
        val deep = repository("projects/a/b/c/too-deep")
        repository("projects/node_modules/dep")
        repository("projects/.cache/hidden")
        File(folder("projects/vendor/sub"), ".git").writeText("gitdir: ../../.git/modules/sub\n")
        assertEquals(
            listOf(api.path, cli.path, File(projects, "vendor/sub").path),
            locator.repositoriesIn(projects.path).sortedBy { it },
        )
        assertFalse(deep.path in locator.repositoriesIn(projects.path))

        val app = repository("app")
        val lib = repository("app/libs/core")
        assertEquals(listOf(app.path, lib.path), locator.repositoriesIn(app.path))
    }

    @Test fun `Git runs in the chosen repository of a workspace, else the one it is in, else its first`() {
        val projects = folder("projects")
        val api = repository("projects/api")
        val cli = repository("projects/cli")
        assertEquals("before a scan, the workspace itself", projects.path, locator.workingFolderOf(projects.path))
        locator.repositoriesIn(projects.path)
        assertEquals(api.path, locator.workingFolderOf(projects.path))
        locator.choose(projects.path, cli.path)
        assertEquals(cli.path, locator.workingFolderOf(projects.path))
        assertEquals("a folder inside one is its repository's, whatever is chosen", api.path, locator.workingFolderOf(File(api, "src").apply { mkdirs() }.path))

        cli.deleteRecursively()
        locator.repositoriesIn(projects.path)
        assertEquals("a chosen one gone, the first again", api.path, locator.workingFolderOf(projects.path))

        val app = repository("app")
        repository("app/libs/core")
        locator.repositoriesIn(app.path)
        assertEquals("a workspace that is a repository, itself", app.path, locator.workingFolderOf(app.path))
    }
}

