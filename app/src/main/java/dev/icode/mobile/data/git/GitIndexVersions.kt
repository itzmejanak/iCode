package dev.icode.mobile.data.git

/** A file's version in Git's index as a checkout writes it, and the entry's mode (`100644`, `100755`). */
internal class IndexVersion(val mode: String, val bytes: ByteArray)

/**
 * Files' versions in Git's index, in one process (docs/editor-plan.md §3.1): [script] prints the
 * index's entries for the paths (`ls-files -s -z`) and writes each one's checkout form
 * (`cat-file --filters`) to a numbered file of a folder in the guest's /tmp; [read] takes them
 * back. Both halves of the protocol live here, as [stageScript] writes one back.
 */
internal object GitIndexVersions {
    /** The script for [paths], relative to the repository's top, writing into [guestFolder]. */
    fun script(paths: List<String>, guestFolder: String): String = buildString {
        append("mkdir -p ").append(GitCommandLine.quote(guestFolder)).append(" && ")
        // Pathspecs taken as names: a file called `a*b` is that file alone.
        append(git(listOf("--literal-pathspecs", "ls-files", "-s", "-z", "--") + paths))
        paths.forEachIndexed { index, path ->
            val file = GitCommandLine.quote("$guestFolder/$index")
            append(" && { ").append(git(listOf("cat-file", "--filters", ":$path"))).append(" > ").append(file)
            append(" 2>/dev/null || rm -f ").append(file).append("; }")
        }
    }

    /**
     * Each path's version from [lsFiles], the script's stdout, and [written], the file it wrote for
     * the path at that index. A path has one when the index holds it at stage 0 as a regular
     * file: not untracked, not in a conflict (stages 1–3), not a link or a submodule.
     */
    fun read(lsFiles: String, paths: List<String>, written: (index: Int) -> ByteArray?): Map<String, IndexVersion> {
        val entries = lsFiles.split('\u0000').filter { it.isNotEmpty() }.groupBy({ it.substringAfter('\t') }, { it.substringBefore('\t').split(' ') })
        return buildMap {
            paths.forEachIndexed { index, path ->
                val entry = entries[path]?.singleOrNull() ?: return@forEachIndexed
                val (mode, _, stage) = entry.takeIf { it.size == ENTRY_FIELDS } ?: return@forEachIndexed
                if (stage != STAGE_MERGED || mode !in REGULAR_MODES) return@forEachIndexed
                written(index)?.let { put(path, IndexVersion(mode, it)) }
            }
        }
    }

    /**
     * Writes [guestFile] as [path]'s version in the index, cleaned as Git cleans the file at that
     * path (`hash-object --path`), with [mode] — as VS Code's Stage Change writes it.
     */
    fun stageScript(path: String, mode: String, guestFile: String): String =
        "object=$(" + git(listOf("hash-object", "-w", "--path=$path", "--", guestFile)) + ") && " +
            git(listOf("update-index", "--cacheinfo", mode)) + " \"\$object\" " + GitCommandLine.quote(path)

    private fun git(arguments: List<String>) = GitCommandLine.argv(arguments).joinToString(" ", transform = GitCommandLine::quote)

    private const val ENTRY_FIELDS = 3
    private const val STAGE_MERGED = "0"
    private val REGULAR_MODES = setOf("100644", "100755")
}
