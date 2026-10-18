package dev.icode.mobile.domain

/** How a change sits in the gutter, as VS Code classes its quick diff's changes. */
enum class LineChangeKind { Added, Modified, Deleted }

val GitDiffHunk.kind: LineChangeKind
    get() = when {
        oldCount == 0 -> LineChangeKind.Added
        newCount == 0 -> LineChangeKind.Deleted
        else -> LineChangeKind.Modified
    }

/** The editor's line, counted from 0, the change is marked at: its first line, or for a deletion the line after it. */
val GitDiffHunk.markedLine: Int get() = if (newCount == 0) newStart else newStart - 1

/**
 * A text's changes against a base text, line by line, as `git diff -U0` writes them
 * (docs/editor-plan.md §3.2): each line with its line break, so a last line without one, or one
 * whose break changed, is a changed line, as Git counts it.
 */
object LineDiff {
    /**
     * [text]'s changes against [base], in order. [checkpoint] is called as the search goes on, to
     * stop it — a coroutine's `ensureActive`.
     */
    fun hunks(base: String, text: String, checkpoint: () -> Unit = {}): List<GitDiffHunk> {
        val old = lines(base)
        val new = lines(text)
        // Equal lines as equal numbers: one string comparison each, not one per step.
        val ids = HashMap<String, Int>()
        val a = IntArray(old.size) { ids.getOrPut(old[it]) { ids.size } }
        val b = IntArray(new.size) { ids.getOrPut(new[it]) { ids.size } }
        val hunks = mutableListOf<GitDiffHunk>()
        var oldAt = 0
        var newAt = 0
        Myers(a, b, checkpoint).runs { oldFrom, newFrom, length ->
            if (oldFrom > oldAt || newFrom > newAt) hunks += hunk(old, oldAt, oldFrom, new, newAt, newFrom)
            oldAt = oldFrom + length
            newAt = newFrom + length
        }
        if (oldAt < old.size || newAt < new.size) hunks += hunk(old, oldAt, old.size, new, newAt, new.size)
        return hunks
    }

    /** [text] with [change] undone — the base's lines back in its place; null when [text] no longer holds it. */
    fun revert(text: String, base: String, change: GitDiffHunk): String? {
        if (change !in hunks(base, text)) return null
        return replaced(lines(text), change.newFrom, change.newCount, lines(base), change.oldFrom, change.oldCount)
    }

    /** [base] with only [change] applied — what staging it writes; null when [text] no longer holds it. */
    fun stage(base: String, text: String, change: GitDiffHunk): String? {
        if (change !in hunks(base, text)) return null
        return replaced(lines(base), change.oldFrom, change.oldCount, lines(text), change.newFrom, change.newCount)
    }

    /**
     * Which of [changes] the gutter marks on the editor's [line] (from 0): a change holding the line,
     * else a deletion just above or below it; null when none.
     */
    fun changeAt(changes: List<GitDiffHunk>, line: Int): Int? =
        changes.indexOfFirst { it.newCount > 0 && line in it.markedLine until it.markedLine + it.newCount }.takeIf { it >= 0 }
            ?: changes.indexOfFirst { it.newCount == 0 && (line == it.markedLine - 1 || line == it.markedLine) }.takeIf { it >= 0 }

    /** The first of [changes] at or after the editor's [line], else the first; null when there are none. */
    fun nextChange(changes: List<GitDiffHunk>, line: Int): Int? =
        if (changes.isEmpty()) null else changes.indexOfFirst { it.markedLine >= line }.takeIf { it >= 0 } ?: 0

    /** [text]'s lines, each with its line break — LF, CRLF or a lone CR; the last may have none. */
    fun lines(text: String): List<String> {
        val lines = mutableListOf<String>()
        var start = 0
        var at = 0
        while (at < text.length) {
            when (text[at]) {
                '\n' -> {
                    lines += text.substring(start, at + 1)
                    start = at + 1
                }
                '\r' -> {
                    val end = if (at + 1 < text.length && text[at + 1] == '\n') at + 2 else at + 1
                    lines += text.substring(start, end)
                    start = end
                    at = end - 1
                }
            }
            at++
        }
        if (start < text.length) lines += text.substring(start)
        return lines
    }

    /** [into]'s lines with [count] of them from [at] replaced by [fromCount] of [from]'s, from [fromAt]. */
    private fun replaced(into: List<String>, at: Int, count: Int, from: List<String>, fromAt: Int, fromCount: Int): String = buildString {
        into.subList(0, at).forEach(::append)
        from.subList(fromAt, fromAt + fromCount).forEach(::append)
        into.subList(at + count, into.size).forEach(::append)
    }

    private fun hunk(old: List<String>, oldFrom: Int, oldTo: Int, new: List<String>, newFrom: Int, newTo: Int): GitDiffHunk {
        val oldCount = oldTo - oldFrom
        val newCount = newTo - newFrom
        // Git numbers lines from 1, and an empty side by the line before it.
        val oldStart = if (oldCount == 0) oldFrom else oldFrom + 1
        val newStart = if (newCount == 0) newFrom else newFrom + 1
        return GitDiffHunk(
            oldStart = oldStart,
            oldCount = oldCount,
            newStart = newStart,
            newCount = newCount,
            header = "@@ -${range(oldStart, oldCount)} +${range(newStart, newCount)} @@",
            lines = (oldFrom until oldTo).map { GitDiffLine(GitDiffLineKind.Removed, it + 1, null, withoutBreak(old[it])) } +
                (newFrom until newTo).map { GitDiffLine(GitDiffLineKind.Added, null, it + 1, withoutBreak(new[it])) },
        )
    }

    /** As Git writes a range: a count of one is left out. */
    private fun range(start: Int, count: Int) = if (count == 1) "$start" else "$start,$count"

    private fun withoutBreak(line: String) = line.removeSuffix("\n").removeSuffix("\r")

    /** Where the change's base lines start, counted from 0. */
    private val GitDiffHunk.oldFrom: Int get() = if (oldCount == 0) oldStart else oldStart - 1

    /** Where the change's lines of the text start, counted from 0. */
    private val GitDiffHunk.newFrom: Int get() = if (newCount == 0) newStart else newStart - 1
}

/**
 * Myers' shortest edit between [a] and [b], in linear space: the middle snake splits each part in
 * two until what is left is only kept, only removed or only added ("An O(ND) Difference Algorithm
 * and Its Variations", 1986, §4b).
 */
private class Myers(private val a: IntArray, private val b: IntArray, private val checkpoint: () -> Unit) {
    private val offset = a.size + b.size + 1
    private val forward = IntArray(2 * offset + 1)
    private val backward = IntArray(2 * offset + 1)
    private val snake = IntArray(4)

    /** Each run of lines both keep, in order: where it starts in [a] and in [b], and how long it is. */
    fun runs(each: (aFrom: Int, bFrom: Int, length: Int) -> Unit) = compare(0, a.size, 0, b.size, each)

    private fun compare(aFrom: Int, aTo: Int, bFrom: Int, bTo: Int, each: (Int, Int, Int) -> Unit) {
        var aStart = aFrom
        var bStart = bFrom
        while (aStart < aTo && bStart < bTo && a[aStart] == b[bStart]) {
            aStart++
            bStart++
        }
        if (aStart > aFrom) each(aFrom, bFrom, aStart - aFrom)
        var aEnd = aTo
        var bEnd = bTo
        while (aEnd > aStart && bEnd > bStart && a[aEnd - 1] == b[bEnd - 1]) {
            aEnd--
            bEnd--
        }
        // Both sides left: their first lines differ and their last lines differ, so the edit is at
        // least two long and the middle snake parts it into two shorter ones.
        if (aStart < aEnd && bStart < bEnd) {
            middleSnake(aStart, aEnd, bStart, bEnd)
            val (x, y, u, v) = snake
            compare(aStart, x, bStart, y, each)
            if (u > x) each(x, y, u - x)
            compare(u, aEnd, v, bEnd, each)
        }
        if (aEnd < aTo) each(aEnd, bEnd, aTo - aEnd)
    }

    /** Leaves in [snake] the middle snake of a[aFrom, aTo) and b[bFrom, bTo): from (x, y) to (u, v). */
    private fun middleSnake(aFrom: Int, aTo: Int, bFrom: Int, bTo: Int) {
        val n = aTo - aFrom
        val m = bTo - bFrom
        val delta = n - m
        val odd = delta % 2 != 0
        forward[offset + 1] = 0
        backward[offset + 1] = 0
        for (d in 0..(n + m + 1) / 2) {
            checkpoint()
            var k = -d
            while (k <= d) {
                var x = if (k == -d || (k != d && forward[offset + k - 1] < forward[offset + k + 1])) forward[offset + k + 1] else forward[offset + k - 1] + 1
                var y = x - k
                val startX = x
                val startY = y
                while (x < n && y < m && a[aFrom + x] == b[bFrom + y]) {
                    x++
                    y++
                }
                forward[offset + k] = x
                // The reverse path on the same diagonal, from the round before, meets this one.
                if (odd && delta - k in -(d - 1)..(d - 1) && x + backward[offset + delta - k] >= n) {
                    return found(aFrom + startX, bFrom + startY, aFrom + x, bFrom + y)
                }
                k += 2
            }
            k = -d
            while (k <= d) {
                // From the ends backwards: x and y count lines from the end.
                var x = if (k == -d || (k != d && backward[offset + k - 1] < backward[offset + k + 1])) backward[offset + k + 1] else backward[offset + k - 1] + 1
                var y = x - k
                val startX = x
                val startY = y
                while (x < n && y < m && a[aTo - 1 - x] == b[bTo - 1 - y]) {
                    x++
                    y++
                }
                backward[offset + k] = x
                if (!odd && delta - k in -d..d && x + forward[offset + delta - k] >= n) {
                    return found(aTo - x, bTo - y, aTo - startX, bTo - startY)
                }
                k += 2
            }
        }
        error("The two paths of an edit always meet")
    }

    private fun found(x: Int, y: Int, u: Int, v: Int) {
        snake[0] = x
        snake[1] = y
        snake[2] = u
        snake[3] = v
    }
}
