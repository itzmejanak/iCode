package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.StorageMeasurement
import dev.icode.mobile.domain.WorkspaceRuntimePath
import dev.icode.mobile.domain.countOf

// The file explorer of iCode's storage and phone storage (docs/storage-explorer-plan.md §3): where
// it is, what is there, and how it is ordered and found.

/**
 * One entry the file explorer lists: in a folder, a folder — a workspace, at the top — or a file; in
 * a view (docs/explorer-gaps-plan.md §3.10), a file gathered from anywhere, or what Trash holds,
 * whose [location] is its id there.
 */
data class BrowsedItem(
    val name: String,
    val location: String,
    /** As the runtime names it — `/workspace/app/src/Main.kt` — or, on phone storage, as Android does; in Trash, where it was. */
    val path: String,
    val place: StoragePlace,
    val folder: Boolean,
    /** A file's size; a folder's is measured apart (a workspace's, by the Workspaces page), but in Trash, as it was deleted. */
    val bytes: Long? = null,
    /** The date its row says: when it changed; a workspace's at the top, and Recent's, when last opened; Trash's, when deleted. */
    val changedAt: Long,
    /** How many entries a folder holds. */
    val entries: Int? = null,
    /** A workspace at the top that is a repository: its branch. */
    val branch: String? = null,
    /** What a scan found it is, its bytes read where its name could not say; null: what its name says (docs/file-kinds-icons-plan.md D3). */
    val type: FileType? = null,
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
     * The breadcrumb (docs/explorer-gaps-plan.md §3.14): the folder shown's path in segments, each
     * where a tap goes — the place's top as the runtime names it, then each folder down to this one.
     */
    val crumbs: List<Crumb>
        get() {
            val names = path.removePrefix(rootPath).split('/').filter { it.isNotEmpty() }
            val below = names.runningFold(rootPath) { above, name -> "$above/$name" }.drop(1)
            return listOf(Crumb(runtimePathOf(rootPath), rootPath)) + names.zip(below) { name, location -> Crumb(name, location) }
        }

    /**
     * [location] as the runtime names it — `/workspace/tools/cli/README.md` — or, on phone storage,
     * as Android does: `/storage/emulated/0/Download/notes.txt`.
     */
    fun runtimePathOf(location: String): String =
        if (place == StoragePlace.Shared) location else WorkspaceRuntimePath.ROOT + location.removePrefix(rootPath)
}

/** A labelled run of a listing's rows (§3.10, §3.12): a type's — ".kt · 12" — or a set of copies; unlabelled, the whole listing. */
data class RowGroup(val label: String?, val rows: List<BrowsedItem>)

/** One segment of the breadcrumb: what it says, and the folder it goes to. */
data class Crumb(val label: String, val location: String)

/**
 * What the explorer orders by (docs/explorer-gaps-plan.md §3.12), in the panel's order. Each has the
 * way it reads first — names A→Z, the largest and the newest first — which Descending flips.
 */
enum class FilesSort(val descendingFirst: Boolean) {
    Name(false),
    Size(true),
    Date(true),
    Type(false),
}

object FilesRules {
    /** Those whose name holds [query], ignoring case; all of them for a blank one. */
    fun matching(items: List<BrowsedItem>, query: String): List<BrowsedItem> {
        val wanted = query.trim()
        return if (wanted.isEmpty()) items else items.filter { it.name.contains(wanted, ignoreCase = true) }
    }

    /**
     * Folders first, as a folder lists them — a view (§3.10) mixes them, by [foldersFirst] — then by
     * [sort], [descending] or not: the name ignoring case; the size ([sizeOf]), the unmeasured last
     * either way; the date its row says; the type, by [extension], then the name. Ties go by name.
     */
    fun sorted(
        items: List<BrowsedItem>,
        sort: FilesSort,
        descending: Boolean,
        sizes: Map<String, StorageMeasurement>,
        foldersFirst: Boolean = true,
    ): List<BrowsedItem> {
        val byName = compareBy(String.CASE_INSENSITIVE_ORDER) { item: BrowsedItem -> item.name }
        val byKey: Comparator<BrowsedItem> = when (sort) {
            FilesSort.Name -> byName
            FilesSort.Date -> compareBy { it.changedAt }
            FilesSort.Type -> compareBy<BrowsedItem> { extension(it.name) }.then(byName)
            FilesSort.Size -> compareBy { sizeOf(it, sizes) }
        }
        val ordered = if (descending) byKey.reversed() else byKey
        val unmeasuredLast = compareBy<BrowsedItem> { sort == FilesSort.Size && sizeOf(it, sizes) == null }
        return items.sortedWith(compareBy<BrowsedItem> { foldersFirst && !it.folder }.then(unmeasuredLast).then(ordered).then(byName))
    }

    /** [item]'s size: its own — a file's, or a folder's in Trash — else a folder's as measured in [sizes]; null until measured. */
    fun sizeOf(item: BrowsedItem, sizes: Map<String, StorageMeasurement>): Long? = item.bytes ?: sizes[item.location]?.bytes

    /** A file's extension, lower case, without the dot — "kt", "gz" — or "" for none (`Makefile`, `.gitignore`). */
    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0 || dot == name.length - 1) "" else name.substring(dot + 1).lowercase()
    }

    /** What sorting by type groups [item] under: "Folders", ".kt", or "No extension". */
    fun typeGroup(item: BrowsedItem): String = when {
        item.folder -> "Folders"
        else -> extension(item.name).let { if (it.isEmpty()) "No extension" else ".$it" }
    }

    /** [shown] as listed: sorted by Type, under a label for each type with how many it has — ".kt · 12"; else as one. */
    fun grouped(shown: List<BrowsedItem>, sort: FilesSort): List<RowGroup> =
        if (sort != FilesSort.Type) listOf(RowGroup(null, shown))
        else shown.groupBy(::typeGroup).map { (type, rows) -> RowGroup("$type · ${rows.size}", rows) }

    /**
     * A row's detail: a file's size, or a folder's as Trash measured it; else a folder's "12 items" or
     * "Empty" — "Folder" when it cannot be read — and a workspace's branch after it.
     */
    fun detail(item: BrowsedItem): String {
        val what = when {
            item.bytes != null || !item.folder -> ByteSize.format(item.bytes ?: 0)
            item.entries == null -> "Folder"
            item.entries == 0 -> "Empty"
            else -> countOf(item.entries, "item")
        }
        return listOfNotNull(what, item.branch).joinToString(" · ")
    }

    /** A view's row's detail (§3.10), gathered from many folders: the folder it is in — Trash's, was in — then [detail]. */
    fun gatheredDetail(item: BrowsedItem): String = "${item.path.substringBeforeLast('/').ifEmpty { "/" }} · ${detail(item)}"
}
