package dev.icode.mobile.domain.files

/**
 * What every explorer shares (docs/explorer-gaps-plan.md §4): the one clipboard, the question an
 * operation waits on, what Undo and Redo would do, and what Trash holds.
 */
data class ExplorerState(
    val clipboard: FileClipboard? = null,
    /** The oldest question waiting; one asked by another operation meanwhile waits behind it. */
    val question: FileConflict? = null,
    val history: FileHistoryState = FileHistoryState(),
    /** Both places' Trash, newest first; Shared's only with All Files Access. */
    val trash: List<TrashedItem> = emptyList(),
    /** An archive Extract waits on the password of. */
    val password: PasswordAsk? = null,
    /** Properties open, as far as they are known. */
    val facts: FileFacts? = null,
    /** Manage storage: its last scan, or the one running. */
    val storage: StorageOverview? = null,
)

/** Extract of [archive] into [folder] waits on its password, which is never kept: the answer is Extract again, with it. */
data class PasswordAsk(val archive: String, val folder: String?)
