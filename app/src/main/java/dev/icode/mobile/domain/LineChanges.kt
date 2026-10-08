package dev.icode.mobile.domain

/**
 * How many lines a change to a file added and removed, as `git diff --numstat` counts them: from a
 * shortest edit between the text before and after (Myers' algorithm), so a line moved or retyped
 * the same is not counted.
 */
data class LineChanges(val added: Int, val removed: Int) {
    /** "+12 −3"; "+12" or "−3" when one side is none. */
    val label: String
        get() = listOfNotNull("+$added".takeIf { added > 0 }, "−$removed".takeIf { removed > 0 }).joinToString(" ").ifEmpty { "No changes" }

    companion object {
        /** From [before] — null for a file that did not exist — to [after]. */
        fun between(before: String?, after: String): LineChanges {
            val old = lines(before.orEmpty())
            val new = lines(after)
            // A change is usually somewhere in the middle: what both start and end with is no edit.
            var start = 0
            while (start < old.size && start < new.size && old[start] == new[start]) start++
            var oldEnd = old.size
            var newEnd = new.size
            while (oldEnd > start && newEnd > start && old[oldEnd - 1] == new[newEnd - 1]) {
                oldEnd--
                newEnd--
            }
            val common = start + (old.size - oldEnd) + commonLines(old.subList(start, oldEnd), new.subList(start, newEnd))
            return LineChanges(added = new.size - common, removed = old.size - common)
        }

        /**
         * A text's lines, as Git counts them: a line break at its very end starts no line of its own,
         * and a last line without one is not the same line with one ("\ No newline at end of file").
         */
        private fun lines(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val lines = text.split("\r\n", "\n", "\r")
            // No line holds a line break, so one marks the line that has none.
            return if (lines.last().isEmpty()) lines.dropLast(1) else lines.dropLast(1) + (lines.last() + "\n")
        }

        /**
         * The lines [a] and [b] keep in common along a shortest edit between them: Myers' greedy
         * search for the fewest insertions and deletions `d`, in O((n + m) · d).
         */
        private fun commonLines(a: List<String>, b: List<String>): Int {
            val n = a.size
            val m = b.size
            if (n == 0 || m == 0) return 0
            // Equal lines as equal numbers: one string comparison each, not one per step.
            val ids = HashMap<String, Int>()
            val x = IntArray(n) { ids.getOrPut(a[it]) { ids.size } }
            val y = IntArray(m) { ids.getOrPut(b[it]) { ids.size } }
            val max = n + m
            val offset = max + 1
            val reach = IntArray(2 * max + 3)
            for (d in 0..max) {
                var k = -d
                while (k <= d) {
                    var i = if (k == -d || (k != d && reach[offset + k - 1] < reach[offset + k + 1])) reach[offset + k + 1] else reach[offset + k - 1] + 1
                    var j = i - k
                    while (i < n && j < m && x[i] == y[j]) {
                        i++
                        j++
                    }
                    reach[offset + k] = i
                    if (i >= n && j >= m) return (n + m - d) / 2
                    k += 2
                }
            }
            return 0
        }
    }
}
