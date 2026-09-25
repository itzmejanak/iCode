package dev.icode.mobile.data.git

import java.io.File

/**
 * Reads the current branch straight from `.git/HEAD`.
 *
 * The launcher shows a branch for every recent workspace. Running `git` for
 * each one would cost a PRoot start apiece and stall entry, so this reads the
 * one file Git guarantees is there. It answers only "which branch", never
 * repository status — anything more still goes through the real client.
 */
internal object GitHeadReader {

    /** Branch name, `null` when the path is not a repository or HEAD is detached. */
    fun branchOf(workspacePath: String): String? {
        val head = headFile(File(workspacePath)) ?: return null
        val contents = runCatching { head.readText().trim() }.getOrNull() ?: return null
        // "ref: refs/heads/main" for a branch; a bare SHA means detached.
        val reference = contents.removePrefix("ref:").trim()
        if (reference == contents) return null
        return reference.substringAfter("refs/heads/", missingDelimiterValue = "").takeIf { it.isNotEmpty() }
    }

    fun isRepository(workspacePath: String): Boolean = headFile(File(workspacePath)) != null

    /**
     * Finds `.git/HEAD` for [directory], walking up so a subdirectory of a
     * repository is still recognised (PR-GIT-01).
     *
     * A `.git` *file* rather than a directory means a worktree or submodule,
     * whose real git directory is named inside it.
     */
    private fun headFile(directory: File): File? {
        var current: File? = runCatching { directory.canonicalFile }.getOrNull()
        var depth = 0
        while (current != null && depth < MAX_PARENT_DEPTH) {
            val marker = File(current, ".git")
            when {
                marker.isDirectory -> return File(marker, "HEAD").takeIf { it.isFile }
                marker.isFile -> return worktreeHead(marker)
            }
            current = current.parentFile
            depth += 1
        }
        return null
    }

    /** `.git` file form: `gitdir: /path/to/real/git/dir`. */
    private fun worktreeHead(marker: File): File? {
        val pointer = runCatching { marker.readText().trim() }.getOrNull() ?: return null
        val gitDir = pointer.substringAfter("gitdir:", missingDelimiterValue = "").trim()
        if (gitDir.isEmpty()) return null
        val resolved = File(gitDir).let { if (it.isAbsolute) it else File(marker.parentFile, gitDir) }
        return File(resolved, "HEAD").takeIf { it.isFile }
    }

    private const val MAX_PARENT_DEPTH = 24
}
