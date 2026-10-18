package dev.icode.mobile.domain

/**
 * How many lines a change to a file added and removed, as `git diff --numstat` counts them: from a
 * shortest edit between the text before and after ([LineDiff]), so a line moved or retyped the
 * same is not counted.
 */
data class LineChanges(val added: Int, val removed: Int) {
    /** "+12 −3"; "+12" or "−3" when one side is none. */
    val label: String
        get() = listOfNotNull("+$added".takeIf { added > 0 }, "−$removed".takeIf { removed > 0 }).joinToString(" ").ifEmpty { "No changes" }

    companion object {
        /**
         * From [before] — null for a file that did not exist — to [after]. Line endings do not count:
         * a model writes `\n` into a `\r\n` file, which keeps its own when written.
         */
        fun between(before: String?, after: String): LineChanges {
            val hunks = LineDiff.hunks(EditorTextRules.withLineBreaks(before.orEmpty(), "\n"), EditorTextRules.withLineBreaks(after, "\n"))
            return LineChanges(added = hunks.sumOf { it.newCount }, removed = hunks.sumOf { it.oldCount })
        }
    }
}
