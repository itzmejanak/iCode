package dev.icode.mobile.domain

// The Workspaces page as the file explorer of the workspaces (docs/workspace-destinations-plan.md
// §2.2): where it is, what is there, and how it is ordered and found.

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

/** Where the file explorer is, and what is there. */
data class FilesBrowserState(val rootPath: String, val path: String, val items: List<BrowsedItem> = emptyList()) {
    val atRoot: Boolean get() = path == rootPath
    val name: String get() = path.substringAfterLast('/')

    /** `workspace › tools › cli`: each step a tap back. */
    val crumbs: List<FolderCrumb> get() = FolderCrumbs.of(ROOT_NAME, rootPath, path)

    /** The folder above, where Back goes; null at the top. */
    val parent: String? get() = FolderCrumbs.parentOf(crumbs)

    /** The workspace it is in; null at the top. */
    val workspace: String? get() = crumbs.getOrNull(1)?.path

    /** [location] as the runtime names it: `/workspace/tools/cli/README.md`. */
    fun runtimePathOf(location: String): String = WorkspaceRuntimePath.ROOT + location.removePrefix(rootPath)

    private companion object {
        /** The runtime's name for the workspaces folder: `/workspace`. */
        val ROOT_NAME = WorkspaceRuntimePath.ROOT.removePrefix("/")
    }
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

    /** "3 folders, 12 files": what a folder inside a workspace holds. */
    fun contents(items: List<BrowsedItem>): String {
        val folders = items.count { it.folder }
        return "${countOf(folders, "folder")}, ${countOf(items.size - folders, "file")}"
    }
}
