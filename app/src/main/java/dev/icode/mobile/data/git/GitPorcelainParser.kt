package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitStatusCodes

/** Branch and upstream facts carried by the `# branch.*` headers. */
internal data class GitStatusHeader(
    val oid: String? = null,
    val branch: String? = null,
    val upstream: String? = null,
    val ahead: Int = 0,
    val behind: Int = 0,
) {
    val detached: Boolean get() = branch == DETACHED
    val hasCommits: Boolean get() = oid != null && oid != INITIAL

    companion object {
        const val DETACHED = "(detached)"
        const val INITIAL = "(initial)"
    }
}

internal data class GitStatus(
    val header: GitStatusHeader,
    val changes: List<GitChange>,
)

/**
 * Parser for `git status --porcelain=v2 --branch -z`.
 *
 * Porcelain v2 is used rather than v1 because it reports rename scores, the
 * submodule field and branch/upstream headers in a format Git promises not to
 * change. NUL termination is what makes filenames containing spaces, quotes or
 * newlines safe — those are exactly the paths that break naive parsers, so the
 * records are split on NUL and never on whitespace beyond the fixed fields.
 */
internal object GitPorcelainParser {

    fun parse(output: String): GitStatus {
        var header = GitStatusHeader()
        val changes = mutableListOf<GitChange>()

        // Records are NUL-terminated. A rename record is followed by a second
        // NUL-terminated field holding the original path.
        val records = output.split(NUL)
        var index = 0
        while (index < records.size) {
            val record = records[index]
            index += 1
            if (record.isEmpty()) continue
            when (record.first()) {
                '#' -> header = header.applyHeader(record)
                '1' -> changes += parseOrdinary(record)
                '2' -> {
                    // The original path is the next NUL-separated field.
                    val originalPath = records.getOrNull(index)
                    index += 1
                    changes += parseRenamed(record, originalPath)
                }
                'u' -> parseUnmerged(record)?.let(changes::add)
                '?' -> changes += change(GitStatusCodes.UNTRACKED, record.substring(2), staged = false)
                '!' -> Unit // Ignored paths are not shown as changes.
            }
        }
        return GitStatus(header, changes.sortedWith(CHANGE_ORDER))
    }

    private fun GitStatusHeader.applyHeader(record: String): GitStatusHeader {
        val parts = record.split(' ', limit = 3)
        if (parts.size < 3) return this
        return when (parts[1]) {
            "branch.oid" -> copy(oid = parts[2])
            "branch.head" -> copy(branch = parts[2])
            "branch.upstream" -> copy(upstream = parts[2])
            "branch.ab" -> {
                // Format: "+3 -2"
                val counts = parts[2].split(' ')
                copy(
                    ahead = counts.getOrNull(0)?.removePrefix("+")?.toIntOrNull() ?: 0,
                    behind = counts.getOrNull(1)?.removePrefix("-")?.toIntOrNull() ?: 0,
                )
            }
            else -> this
        }
    }

    /** `1 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <path>` */
    private fun parseOrdinary(record: String): List<GitChange> {
        val fields = record.split(' ', limit = 9)
        if (fields.size < 9) return emptyList()
        return changesFor(xy = fields[1], path = fields[8])
    }

    /** `2 <XY> <sub> <mH> <mI> <mW> <hH> <hI> <Xscore> <path>` plus a NUL-separated original path. */
    private fun parseRenamed(record: String, originalPath: String?): List<GitChange> {
        val fields = record.split(' ', limit = 10)
        if (fields.size < 10) return emptyList()
        val renamed = changesFor(xy = fields[1], path = fields[9])
        if (originalPath.isNullOrEmpty()) return renamed
        // The original path is carried in the display name so Source Control
        // can show "old -> new" without widening the shared change contract.
        val previous = originalPath.substringAfterLast('/')
        return renamed.map { it.copy(name = "$previous \u2192 ${it.name}") }
    }

    /** `u <XY> <sub> <m1> <m2> <m3> <mW> <h1> <h2> <h3> <path>` */
    private fun parseUnmerged(record: String): GitChange? {
        val fields = record.split(' ', limit = 11)
        if (fields.size < 11) return null
        return change(GitStatusCodes.CONFLICTED, fields[10], staged = false)
    }

    /**
     * Maps the two-character XY code to changes.
     *
     * X is the index side and Y the worktree side, and Git reports both in one
     * record. A file that is staged *and* then edited again (`MM`) is genuinely
     * in both groups, so it produces two changes rather than one — otherwise
     * Source Control would hide half of what a commit is about to include.
     */
    private fun changesFor(xy: String, path: String): List<GitChange> {
        val indexCode = xy.getOrNull(0) ?: return emptyList()
        val worktreeCode = xy.getOrNull(1) ?: return emptyList()
        return listOfNotNull(
            statusFor(indexCode)?.let { change(it, path, staged = true) },
            statusFor(worktreeCode)?.let { change(it, path, staged = false) },
        )
    }

    /** `.` means "unchanged on this side" and yields no entry. */
    private fun statusFor(code: Char): String? = when (code) {
        'M' -> GitStatusCodes.MODIFIED
        'A' -> GitStatusCodes.ADDED
        'D' -> GitStatusCodes.DELETED
        'R' -> GitStatusCodes.MODIFIED
        'C' -> GitStatusCodes.ADDED
        'U' -> GitStatusCodes.CONFLICTED
        else -> null
    }

    private fun change(status: String, path: String, staged: Boolean) = GitChange(
        status = status,
        name = path.substringAfterLast('/'),
        path = path,
        staged = staged,
    )

    /** Conflicts first, then staged, then by path — matching Source Control. */
    private val CHANGE_ORDER = compareBy<GitChange> { it.status != GitStatusCodes.CONFLICTED }
        .thenBy { !it.staged }
        .thenBy { it.path }

    private const val NUL = '\u0000'
}

/** Reads the in-progress operation from the marker files Git leaves in `.git`. */
internal object GitInProgressDetector {
    fun detect(hasMergeHead: Boolean, hasRebase: Boolean, hasCherryPick: Boolean, hasRevert: Boolean, hasBisect: Boolean): GitInProgress =
        when {
            hasRebase -> GitInProgress.Rebase
            hasMergeHead -> GitInProgress.Merge
            hasCherryPick -> GitInProgress.CherryPick
            hasRevert -> GitInProgress.Revert
            hasBisect -> GitInProgress.Bisect
            else -> GitInProgress.None
        }
}
