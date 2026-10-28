package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.CompressFormat
import dev.icode.mobile.domain.files.ConflictAnswer
import dev.icode.mobile.domain.files.RenameRule
import dev.icode.mobile.domain.files.ScanScope

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

    /**
     * [locations] into one archive of [format] in the folder they are in, under a free name: [name],
     * or as [dev.icode.mobile.domain.files.ArchiveFormats.archiveName] says (§3.11).
     */
    data class Compress(val locations: List<String>, val format: CompressFormat, val name: String? = null) : FilesIntent

    /**
     * What the archive at [location] holds, into [folder] — the folder it is in by default — as one
     * folder or file, a name already there asked about. A 7z that needs its [password] waits for it.
     */
    data class Extract(val location: String, val folder: String? = null, val password: String? = null) : FilesIntent

    /** The archive waiting on its password is not extracted after all. */
    data object DismissPassword : FilesIntent

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

    /** What the explorers learn of their files (§3.9, §3.10): each on its own job, never behind the lock. */
    sealed interface Insight : FilesIntent

    /** Properties of [locations]: one item, or many; a folder's size measured while it is open. */
    data class Properties(val locations: List<String>) : Insight

    data object CloseProperties : Insight

    /** Manage storage's scan of [scope], again; one already running is replaced. */
    data class Scan(val scope: ScanScope) : Insight

    data object StopScan : Insight

    /** The explorer's ways back (§3.14): Recent and Favorites. */
    sealed interface Shortcut : FilesIntent

    /** A file opened outside the editor — a viewer, Open with — kept in Recent as the editor's are. */
    data class Opened(val location: String) : Shortcut

    /** The folder at [location] pinned to Favorites, or let go. */
    data class Favorite(val location: String, val pinned: Boolean) : Shortcut
}
