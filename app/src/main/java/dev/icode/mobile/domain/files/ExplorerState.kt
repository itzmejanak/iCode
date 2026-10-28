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
    /** The explorers' settings, as kept: what each listing hides, and each look. */
    val settings: ExplorerSettings = ExplorerSettings(),
    /**
     * Recent (§3.14): the files opened in iCode, in workspaces and on phone storage, newest first, each
     * dated when last opened or saved; a file gone left out.
     */
    val recent: List<BrowsedItem> = emptyList(),
    /** Folders pinned app-wide, in the order pinned; one gone is kept, not shown. */
    val favorites: List<FavoriteFolder> = emptyList(),
)

/** A folder pinned to Favorites (docs/explorer-gaps-plan.md §3.14): its name, where it is, and its path as the runtime names it. */
data class FavoriteFolder(val name: String, val location: String, val path: String)

/** Extract of [archive] into [folder] waits on its password, which is never kept: the answer is Extract again, with it. */
data class PasswordAsk(val archive: String, val folder: String?)
