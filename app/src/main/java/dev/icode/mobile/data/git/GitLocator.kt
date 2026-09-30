package dev.icode.mobile.data.git

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File

/**
 * Finds a repository on the host the way Git does — the folder, then each parent, for a `.git`
 * directory or a `.git` file naming one — but never above the workspaces
 * (docs/workspace-storage-plan.md §4.2). Git still decides what is a repository: this gives its
 * commands the working directory whose root-relative paths they report, and gives the launcher a
 * branch without starting a process.
 */
internal class GitLocator(private val paths: WorkspacePaths) {

    /** The top of the repository holding [folder]: [folder] or a parent inside the workspaces; null when none does. */
    fun topOf(folder: String): File? {
        val root = paths.root.toPath()
        var directory: File? = File(folder).absoluteFile
        while (directory != null && directory.toPath().startsWith(root)) {
            if (File(directory, ".git").exists()) return directory
            directory = directory.parentFile
        }
        return null
    }

    /** The branch of the repository holding [folder], read from its `HEAD`; null when there is none or it is detached. */
    fun branchOf(folder: String): String? {
        val head = headOf(folder) ?: return null
        val contents = runCatching { head.readText().trim() }.getOrNull() ?: return null
        // "ref: refs/heads/main" for a branch; a bare SHA means detached.
        val reference = contents.removePrefix("ref:").trim()
        if (reference == contents) return null
        return reference.substringAfter("refs/heads/", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    }

    fun isRepository(folder: String): Boolean = headOf(folder) != null

    private fun headOf(folder: String): File? =
        topOf(folder)?.let(::gitDirectoryOf)?.let { File(it, "HEAD") }?.takeIf { it.isFile }

    /**
     * [top]'s Git directory: its `.git` folder, or the one a `.git` file names — a submodule by a
     * path relative to [top], a linked worktree by an absolute one, which Git in the runtime
     * writes as a guest path.
     */
    private fun gitDirectoryOf(top: File): File? {
        val marker = File(top, ".git")
        if (marker.isDirectory) return marker
        val named = runCatching { marker.readText() }.getOrNull()
            ?.lineSequence()?.firstOrNull { it.startsWith(GITDIR) }
            ?.removePrefix(GITDIR)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val directory = if (named.startsWith("/")) paths.locationOf(named)?.let(::File) else File(top, named)
        return directory?.normalize()?.takeIf { it.isDirectory }
    }

    private companion object {
        const val GITDIR = "gitdir:"
    }
}
