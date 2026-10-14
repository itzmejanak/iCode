package dev.icode.mobile.domain

// The file explorer of iCode's storage and phone storage (docs/storage-explorer-plan.md §3): where
// it is, what is there, and how it is ordered and found.

/** One entry where the file explorer is: a folder — a workspace, at the top — or a file. */
data class BrowsedItem(
    val name: String,
    val location: String,
    val folder: Boolean,
    /** A file's size; a folder's is measured apart (a workspace's, by the Workspaces page). */
    val bytes: Long? = null,
    /** When it changed; for a workspace at the top, when it was last opened. */
    val changedAt: Long,
    /** How many entries a folder holds. */
    val entries: Int? = null,
    /** A workspace at the top that is a repository: its branch. */
    val branch: String? = null,
)

/**
 * The two places the file explorer shows (docs/storage-explorer-plan.md §3.1): iCode's own storage,
 * where workspaces live, and phone storage, which other apps see.
 */
enum class StoragePlace(val label: String) {
    Private("Private"),
    Shared("Shared"),
}

/**
 * Where the file explorer is, and what is there. On [StoragePlace.Shared] without All Files Access
 * ([access] false) it lists nothing and waits there for it. [sharedRoot] is phone storage's top,
 * wherever the explorer is: the places menu names it.
 */
data class FilesBrowserState(
    val place: StoragePlace,
    val rootPath: String,
    val path: String,
    val items: List<BrowsedItem> = emptyList(),
    val access: Boolean = true,
    val sharedRoot: String = "",
) {
    val atRoot: Boolean get() = path == rootPath

    /** The folder shown; at the top, the place. */
    val name: String get() = if (atRoot) place.label else path.substringAfterLast('/')

    /** The folder above, where `..` and Back go; null at the top. */
    val parent: String? get() = if (atRoot) null else path.substringBeforeLast('/')

    /** The workspace it is in; null at the top, and on phone storage. */
    val workspace: String?
        get() = if (place == StoragePlace.Shared || atRoot) null else "$rootPath/" + path.removePrefix("$rootPath/").substringBefore('/')

    /** Where the folder shown is, as ZArchiver says it under the name: its parent's path; at the top, the place's. */
    val where: String get() = runtimePathOf(parent ?: path)

    /**
     * [location] as the runtime names it — `/workspace/tools/cli/README.md` — or, on phone storage,
     * as Android does: `/storage/emulated/0/Download/notes.txt`.
     */
    fun runtimePathOf(location: String): String =
        if (place == StoragePlace.Shared) location else WorkspaceRuntimePath.ROOT + location.removePrefix(rootPath)
}

enum class FilesSort { Name, Date, Size }

object FilesRules {
    /** Those whose name holds [query], ignoring case; all of them for a blank one. */
    fun matching(items: List<BrowsedItem>, query: String): List<BrowsedItem> {
        val wanted = query.trim()
        return if (wanted.isEmpty()) items else items.filter { it.name.contains(wanted, ignoreCase = true) }
    }

    /**
     * Folders first, as a file explorer lists them, then by [sort]: name ignoring case; the newest
     * first; the largest first — a folder by its measured size in [sizes], the unmeasured last.
     */
    fun sorted(items: List<BrowsedItem>, sort: FilesSort, sizes: Map<String, StorageMeasurement>): List<BrowsedItem> {
        val folderFirst = compareBy<BrowsedItem> { !it.folder }
        return items.sortedWith(
            when (sort) {
                FilesSort.Name -> folderFirst.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                FilesSort.Date -> folderFirst.thenByDescending { it.changedAt }
                FilesSort.Size -> folderFirst.thenByDescending { (if (it.folder) sizes[it.location]?.bytes else it.bytes) ?: -1L }
            },
        )
    }

    /**
     * A row's detail: a file's size; a folder's "12 items" or "Empty" — "Folder" when it cannot be
     * read — and a workspace's branch after it.
     */
    fun detail(item: BrowsedItem): String {
        val what = when {
            !item.folder -> ByteSize.format(item.bytes ?: 0)
            item.entries == null -> "Folder"
            item.entries == 0 -> "Empty"
            else -> countOf(item.entries, "item")
        }
        return item.branch?.let { "$what · $it" } ?: what
    }
}
