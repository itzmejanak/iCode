package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RelativeTime

/** An answer to a name already there (docs/explorer-gaps-plan.md §3.6). */
enum class ConflictAnswer(val label: String) {
    /** The one there goes to Trash, and the one coming takes its place. */
    Replace("Replace"),
    /** Two folders: what is in the one coming goes into the one there, each name asked the same way. */
    Merge("Merge"),
    /** The one coming takes a free name: "name copy". */
    KeepBoth("Keep both"),
    /** The one coming stays where it is. */
    Skip("Skip"),
}

/** One side of a conflict, as the question shows it. */
data class ConflictSide(val folder: Boolean, val bytes: Long?, val changedAt: Long) {
    /** "1.2 MB · 2h ago", "Folder · just now". */
    fun said(now: Long): String = "${if (folder) "Folder" else bytes?.let(ByteSize::format) ?: "File"} · ${RelativeTime.since(changedAt, now)}"
}

/**
 * The question an operation waits on: [name] is already in [into] (as the runtime names it).
 * [others] more conflicts may follow, which "Do this for the other N" answers too.
 */
data class FileConflict(
    val id: Long,
    val name: String,
    val into: String,
    val coming: ConflictSide,
    val there: ConflictSide,
    val others: Int,
) {
    /** The answer given for the rest too, offered while there are [others]. */
    val forOthers: String? get() = when (others) {
        0 -> null
        1 -> "Do this for the next one too"
        else -> "Do this for the other $others"
    }

    /** Two folders merge; anything else is replaced. */
    val answers: List<ConflictAnswer>
        get() = listOf(if (coming.folder && there.folder) ConflictAnswer.Merge else ConflictAnswer.Replace, ConflictAnswer.KeepBoth, ConflictAnswer.Skip)
}
