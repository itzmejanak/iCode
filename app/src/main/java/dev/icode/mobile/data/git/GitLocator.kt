package dev.icode.mobile.data.git

import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a repository on the host the way Git does — the folder, then each parent, for a `.git`
 * directory or a `.git` file naming one — but never above the workspaces
 * (docs/workspace-storage-plan.md §4.2). Git still decides what is a repository: this gives its
 * commands the working directory whose root-relative paths they report, and gives the launcher a
 * branch without starting a process.
 */
internal class GitLocator(private val paths: WorkspacePaths) {

    /** Each workspace's repositories as last read, and the one Source Control's picker chose in it. */
    private val found = ConcurrentHashMap<String, List<String>>()
    private val chosen = ConcurrentHashMap<String, String>()

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

    /**
     * Where Git runs for [path]. For a workspace itself, the repository chosen in it (Source
     * Control's picker) while it is still there — else the one the workspace is in, else the first
     * in its folders; for anything in a workspace, the repository holding it.
     */
    fun workingFolderOf(path: String): String {
        if (paths.workspaceOf(path) == path) {
            chosen[path]?.takeIf { it in found[path].orEmpty() }?.let { return it }
            return topOf(path)?.path ?: found[path]?.firstOrNull() ?: path
        }
        return topOf(path)?.path ?: path
    }

    /** Git works in [repository] of [workspace] from now on. */
    fun choose(workspace: String, repository: String) {
        chosen[workspace] = repository
    }

    /**
     * The repositories in [workspace] (docs/workspace-destinations-plan.md §2.6), read again from
     * disk: the one it is in, if any, then each folder below it with a `.git` of its own, [depth]
     * levels down — not in a hidden folder, `node_modules` or through a link. Where Git runs follows.
     */
    fun repositoriesIn(workspace: String, depth: Int = SCAN_DEPTH): List<String> = scan(workspace, depth).also { found[workspace] = it }

    private fun scan(workspace: String, depth: Int): List<String> = buildList {
        topOf(workspace)?.let { add(it.path) }
        fun visit(folder: File, level: Int) {
            if (level > depth) return
            folder.listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith('.') && it.name != "node_modules" && !Files.isSymbolicLink(it.toPath()) }
                .sortedBy { it.name.lowercase() }
                .forEach { child ->
                    if (File(child, ".git").exists()) add(child.path)
                    visit(child, level + 1)
                }
        }
        visit(File(workspace), 1)
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

        /** `workspace/group/team/repo`: deep enough for projects kept in folders, cheap enough to read at each refresh. */
        const val SCAN_DEPTH = 3
    }
}
