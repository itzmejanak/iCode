package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.GitDiffLine
import dev.icode.mobile.domain.GitDiffLineKind
import dev.icode.mobile.domain.GitFileDiff

/**
 * Parser for Git's unified diff output.
 *
 * Only Git's own patch text is parsed — never a snapshot taken from the editor
 * — so what the diff shows is what Git would commit (PR-GIT-04, US-GIT-003).
 *
 * Old and new line numbers are tracked separately while walking a hunk, which
 * is what lets the gutter show `15 15` for context, `16  -` for a removal and
 * `17  +` for an addition.
 */
internal object GitDiffParser {

    /** Parses a patch that may describe several files. */
    fun parse(patch: String, staged: Boolean = false): List<GitFileDiff> {
        if (patch.isBlank()) return emptyList()
        val diffs = mutableListOf<GitFileDiff>()
        var path: String? = null
        var oldPath: String? = null
        var binary = false
        val hunks = mutableListOf<GitDiffHunk>()

        var hunkHeader: String? = null
        var hunkOldStart = 0
        var hunkOldCount = 0
        var hunkNewStart = 0
        var hunkNewCount = 0
        val hunkLines = mutableListOf<GitDiffLine>()
        var oldLine = 0
        var newLine = 0

        fun flushHunk() {
            val header = hunkHeader ?: return
            hunks += GitDiffHunk(
                oldStart = hunkOldStart,
                oldCount = hunkOldCount,
                newStart = hunkNewStart,
                newCount = hunkNewCount,
                header = header,
                lines = hunkLines.toList(),
            )
            hunkHeader = null
            hunkLines.clear()
        }

        fun flushFile() {
            flushHunk()
            val current = path ?: return
            diffs += GitFileDiff(
                path = current,
                oldPath = oldPath?.takeIf { it != current },
                hunks = hunks.toList(),
                binary = binary,
                staged = staged,
            )
            path = null
            oldPath = null
            binary = false
            hunks.clear()
        }

        for (line in patch.lineSequence()) {
            when {
                line.startsWith("diff --git ") -> {
                    flushFile()
                    parseDiffHeader(line)?.let { (from, to) ->
                        oldPath = from
                        path = to
                    }
                }
                // Rename/copy headers carry the authoritative paths when the
                // quoted form in `diff --git` is ambiguous.
                line.startsWith("--- ") -> stripPrefix(line.removePrefix("--- "))?.let { oldPath = it }
                line.startsWith("+++ ") -> stripPrefix(line.removePrefix("+++ "))?.let { path = it }
                line.startsWith("rename from ") -> oldPath = line.removePrefix("rename from ")
                line.startsWith("rename to ") -> path = line.removePrefix("rename to ")
                line.startsWith("Binary files ") || line.startsWith("GIT binary patch") -> binary = true
                line.startsWith("@@") -> {
                    flushHunk()
                    val range = parseHunkHeader(line)
                    if (range != null) {
                        hunkHeader = line
                        hunkOldStart = range.oldStart
                        hunkOldCount = range.oldCount
                        hunkNewStart = range.newStart
                        hunkNewCount = range.newCount
                        oldLine = range.oldStart
                        newLine = range.newStart
                    }
                }
                hunkHeader != null -> when {
                    line.startsWith("+") -> {
                        hunkLines += GitDiffLine(GitDiffLineKind.Added, null, newLine, line.substring(1))
                        newLine += 1
                    }
                    line.startsWith("-") -> {
                        hunkLines += GitDiffLine(GitDiffLineKind.Removed, oldLine, null, line.substring(1))
                        oldLine += 1
                    }
                    line.startsWith(" ") -> {
                        hunkLines += GitDiffLine(GitDiffLineKind.Context, oldLine, newLine, line.substring(1))
                        oldLine += 1
                        newLine += 1
                    }
                    // "\ No newline at end of file" annotates the previous line
                    // and is not itself content.
                    line.startsWith("\\") -> Unit
                    // A genuinely empty line is never diff content: Git writes
                    // an empty context line as a single space. This is the
                    // trailing artifact of the patch's final newline.
                    line.isEmpty() -> Unit
                }
            }
        }
        flushFile()
        return diffs
    }

    /** `diff --git a/path b/path`, handling quoted paths with spaces. */
    private fun parseDiffHeader(line: String): Pair<String, String>? {
        val body = line.removePrefix("diff --git ")
        val aIndex = body.indexOf("a/")
        if (aIndex != 0) return null
        // Git writes ` b/` between the two paths; the last occurrence is the
        // separator, since a path may itself contain " b/".
        val separator = body.lastIndexOf(" b/")
        if (separator <= 0) return null
        val from = body.substring(2, separator)
        val to = body.substring(separator + 3)
        return unquote(from) to unquote(to)
    }

    private fun stripPrefix(value: String): String? {
        val trimmed = value.trim()
        if (trimmed == "/dev/null") return null
        return unquote(trimmed.removePrefix("a/").removePrefix("b/"))
    }

    /** Git quotes paths containing unusual bytes; the quotes are not part of the path. */
    private fun unquote(value: String): String =
        if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
            value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
        } else {
            value
        }

    private data class HunkRange(val oldStart: Int, val oldCount: Int, val newStart: Int, val newCount: Int)

    /** `@@ -oldStart,oldCount +newStart,newCount @@ optional context` */
    private fun parseHunkHeader(line: String): HunkRange? {
        val match = HUNK_PATTERN.find(line) ?: return null
        val (oldStart, oldCount, newStart, newCount) = match.destructured
        return HunkRange(
            oldStart = oldStart.toIntOrNull() ?: return null,
            // A missing count means exactly one line.
            oldCount = oldCount.toIntOrNull() ?: 1,
            newStart = newStart.toIntOrNull() ?: return null,
            newCount = newCount.toIntOrNull() ?: 1,
        )
    }

    private val HUNK_PATTERN = Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@""")
}
