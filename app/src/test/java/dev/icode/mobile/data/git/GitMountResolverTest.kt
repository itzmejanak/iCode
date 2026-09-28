package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.GuestMount
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

/** Built from the layouts Git itself writes: a `.git` folder, a submodule's and a worktree's `.git` file. */
class GitMountResolverTest {
    private val storage = Files.createTempDirectory("storage").toFile().canonicalFile

    @After fun clean() {
        storage.deleteRecursively()
    }

    private fun folder(path: String) = File(storage, path).apply { mkdirs() }

    @Test fun `a folder outside any repository is bound as it is`() {
        val plain = folder("notes")
        assertEquals(GuestMount(plain.path), GitMountResolver.resolve(plain.path))
    }

    @Test fun `a repository opened at its top`() {
        val repository = folder("app").also { File(it, ".git").mkdirs() }
        assertEquals(GuestMount(repository.path), GitMountResolver.resolve(repository.path))
    }

    @Test fun `a folder inside a repository runs Git at the repository's top`() {
        val repository = folder("app").also { File(it, ".git").mkdirs() }
        val opened = folder("app/src/main")
        assertEquals(GuestMount(repository.path), GitMountResolver.resolve(opened.path))
    }

    @Test fun `the nearest repository wins over the one around it`() {
        folder("outer/.git")
        val inner = folder("outer/vendor/lib").also { File(it, ".git").mkdirs() }
        assertEquals(GuestMount(inner.path), GitMountResolver.resolve(inner.path))
    }

    @Test fun `a submodule is bound with the superproject that keeps its history`() {
        val superproject = folder("app")
        folder("app/.git/modules/lib")
        val submodule = folder("app/lib").also { File(it, ".git").writeText("gitdir: ../.git/modules/lib\n") }
        assertEquals(
            GuestMount(superproject.path, workingDirectory = submodule.path),
            GitMountResolver.resolve(submodule.path),
        )
    }

    @Test fun `a linked worktree also sees the main repository at its own path`() {
        val mainGit = folder("app/.git")
        val worktreeGit = folder("app/.git/worktrees/feature").also { File(it, "commondir").writeText("../..\n") }
        val worktree = folder("app-feature").also { File(it, ".git").writeText("gitdir: ${worktreeGit.path}\n") }
        assertEquals(
            GuestMount(worktree.path, samePath = listOf(mainGit.path)),
            GitMountResolver.resolve(worktree.path),
        )
    }

    @Test fun `a git file naming a folder this phone lacks leaves the folder as it is`() {
        val worktree = folder("copied").also { File(it, ".git").writeText("gitdir: /workspace/app/.git/worktrees/copied\n") }
        assertEquals(GuestMount(worktree.path), GitMountResolver.resolve(worktree.path))
    }

    @Test fun `a folder inside the bound one is found at the same place in the guest`() {
        val mount = GuestMount("/storage/emulated/0/app", workingDirectory = "/storage/emulated/0/app/lib")
        assertEquals("/workspace/app/lib", mount.guestPathOf(mount.workingDirectory))
        assertEquals("/workspace/app", mount.guestPathOf(mount.root))
    }
}
