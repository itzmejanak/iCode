package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.GuestMount
import java.io.File

/**
 * Which part of the phone's storage Git needs to see to find the repository
 * that holds a folder, the way Git itself looks for it: the folder, then each
 * parent, for a `.git` directory or a `.git` file naming one.
 *
 * Git runs at the repository's top, so the root-relative paths it reports can
 * be handed back to it. Git still decides what is a repository; this only makes
 * sure the files it needs to decide are there.
 */
internal object GitMountResolver {

    fun resolve(folderPath: String): GuestMount {
        val folder = File(folderPath).absoluteFile
        var directory: File? = folder
        var depth = 0
        while (directory != null && depth < MAX_PARENT_DEPTH) {
            val marker = File(directory, ".git")
            when {
                // An ordinary repository; a parent of the opened folder counts too.
                marker.isDirectory -> return GuestMount(directory.path)
                marker.isFile -> return linked(directory, marker) ?: GuestMount(folder.path)
            }
            directory = directory.parentFile
            depth += 1
        }
        return GuestMount(folder.path)
    }

    /**
     * A `.git` file: a submodule (`gitdir: ../.git/modules/x`, relative) or a
     * linked worktree (`gitdir: /…/.git/worktrees/x`, absolute). Null when the
     * directory it names is not on this phone — a worktree made elsewhere.
     */
    private fun linked(top: File, marker: File): GuestMount? {
        val target = runCatching { marker.readText() }.getOrNull()
            ?.lineSequence()?.firstOrNull { it.startsWith(GITDIR) }
            ?.removePrefix(GITDIR)?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val absolute = File(target).isAbsolute
        val gitDir = (if (absolute) File(target) else File(top, target)).normalize()
        if (!gitDir.isDirectory) return null
        // A worktree's own git directory points at the repository's shared one.
        val common = runCatching { File(gitDir, "commondir").readText().trim() }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { File(it).let { dir -> if (dir.isAbsolute) dir else File(gitDir, it) }.normalize() }
            ?: gitDir
        if (absolute) {
            // Git reads the absolute path as written, so it is bound at that path.
            return GuestMount(top.path, samePath = listOf(common.path))
        }
        // Relative paths resolve inside one bind of the folder holding both.
        val root = commonAncestor(listOf(top, gitDir, common)) ?: return null
        return GuestMount(root.path, workingDirectory = top.path)
    }

    private fun commonAncestor(files: List<File>): File? {
        val paths = files.map { it.path.split('/') }
        val shared = paths.first().indices.takeWhile { index -> paths.all { it.getOrNull(index) == paths.first()[index] } }
        val prefix = paths.first().take(shared.size).joinToString("/")
        return prefix.takeIf { it.isNotEmpty() }?.let(::File)
    }

    private const val GITDIR = "gitdir:"
    private const val MAX_PARENT_DEPTH = 24
}
