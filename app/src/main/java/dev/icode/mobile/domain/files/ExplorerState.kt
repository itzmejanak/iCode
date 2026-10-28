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
)
