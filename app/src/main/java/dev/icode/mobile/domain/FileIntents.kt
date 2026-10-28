package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ConflictAnswer
import dev.icode.mobile.domain.files.RenameRule

/**
 * What the explorers ask of their files (docs/explorer-gaps-plan.md §3.3–§3.8), each on as many
 * items as are chosen. A change runs as a task, off the mutation lock, and is kept to undo. An
 * answer to the question never waits behind the lock: the operation asking holds what it works on.
 */
sealed interface FilesIntent : ICodeIntent {
    /** Held for Paste; nothing moves yet. */
    data class Cut(val locations: List<String>) : FilesIntent

    data class Copy(val locations: List<String>) : FilesIntent

    /** The clipboard's items into [folder]; into the workspaces' folder, each becomes a workspace. */
    data class Paste(val folder: String) : FilesIntent

    /** Nothing is waiting to be pasted any more. */
    data object ClearClipboard : FilesIntent

    /** Move to… ([move]) and Copy to…: [locations] into [folder], as a paste of them would go. */
    data class Transfer(val locations: List<String>, val folder: String, val move: Boolean) : FilesIntent

    /** Each beside itself, under a free name: "name copy". */
    data class Duplicate(val locations: List<String>) : FilesIntent

    /** One [rule] renaming [locations], all in one folder, in the order shown (§3.4). */
    data class RenameMany(val locations: List<String>, val rule: RenameRule) : FilesIntent

    /** Delete: to Trash, without a dialog, since it can be undone (§3.7). */
    data class Trash(val locations: List<String>) : FilesIntent

    /** Trash's items back where they were. */
    data class Restore(val ids: List<String>) : FilesIntent

    /** Confirmed first: Trash's items gone for good. */
    data class DeleteForGood(val ids: List<String>) : FilesIntent

    /** Confirmed first: all of Trash gone for good. */
    data object EmptyTrash : FilesIntent

    /** Trash opened: read again, what has been there 30 days removed. */
    data object ReadTrash : FilesIntent

    /** The question [id] answered; a null [answer] stops the operation, which keeps what it did. [forAll] answers the rest alike. */
    data class Answer(val id: Long, val answer: ConflictAnswer?, val forAll: Boolean = false) : FilesIntent

    data object Undo : FilesIntent

    data object Redo : FilesIntent
}
