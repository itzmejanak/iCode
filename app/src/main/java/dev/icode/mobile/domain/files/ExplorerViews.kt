package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.countOf

/**
 * A list the explorer shows that is no folder (docs/workspaces-and-files.md): files gathered
 * from anywhere. Each keeps its own look, under [key], and opens with [opensWith] until it is
 * changed. Manage storage's views list where it looked, [scope]; Recent and Trash hold what iCode
 * did anywhere.
 */
sealed interface ExplorerView {
    val key: String
    val title: String
    val opensWith: FilesLook
    val scope: ScanScope?

    /** The files opened in iCode, newest first. */
    data object Recent : ExplorerView {
        override val key = "recent"
        override val title = "Recent"
        override val opensWith = FilesLook(sort = FilesSort.Date)
        override val scope: ScanScope? = null
    }

    /** What was deleted, from both places, newest first. */
    data object Trash : ExplorerView {
        override val key = "trash"
        override val title = "Trash"
        override val opensWith = FilesLook(sort = FilesSort.Date)
        override val scope: ScanScope? = null
    }

    /** Every file of [category] where Manage storage looked: pictures and videos as tiles, the largest first. */
    data class Category(val category: FileCategory, override val scope: ScanScope) : ExplorerView {
        override val key get() = "category-${category.name}"
        override val title get() = category.label
        override val opensWith get() = FilesLook(
            view = if (category == FileCategory.Images || category == FileCategory.Videos) FilesView.Grid else FilesView.Detailed,
            sort = FilesSort.Size,
        )
    }

    /** The largest files where Manage storage looked. */
    data class Largest(override val scope: ScanScope) : ExplorerView {
        override val key = "largest"
        override val title = "Largest files"
        override val opensWith = FilesLook(sort = FilesSort.Size)
    }

    /** Files with the same contents, in groups. */
    data class Duplicates(override val scope: ScanScope) : ExplorerView {
        override val key = "duplicates"
        override val title = "Duplicates"
        override val opensWith = FilesLook(sort = FilesSort.Size)
    }
}

object ExplorerViews {
    /**
     * What [view] lists, as the explorer's rows: Recent's files; Trash's items, each where it is held;
     * Manage storage's, what its scan lists for it while it is shown ([ExplorerState.scanned]).
     */
    fun rows(view: ExplorerView, explorer: ExplorerState): List<BrowsedItem> = when (view) {
        ExplorerView.Recent -> explorer.recent
        ExplorerView.Trash -> explorer.trash.map { it.row }
        is ExplorerView.Category, is ExplorerView.Largest, is ExplorerView.Duplicates -> explorer.scanned?.takeIf { it.view == view }?.rows.orEmpty()
    }

    /** Whether [view]'s order is the panel's to choose: Duplicates keeps its own, set by set. */
    fun sortable(view: ExplorerView): Boolean = view !is ExplorerView.Duplicates

    /**
     * How [view] groups its [shown] rows: Duplicates by its sets of copies ([groups]) — the largest
     * waste first, each set's copies the oldest first, under "3 copies · 24 MB each"; any other as a
     * folder is ([FilesRules.grouped]).
     */
    fun grouped(view: ExplorerView, shown: List<BrowsedItem>, sort: FilesSort, groups: List<DuplicateGroup>): List<RowGroup> {
        if (view !is ExplorerView.Duplicates) return FilesRules.grouped(shown, sort)
        val listed = shown.associateBy { it.location }
        return groups.mapNotNull { group ->
            val rows = group.locations.mapNotNull(listed::get).ifEmpty { return@mapNotNull null }
            RowGroup("${countOf(group.locations.size, "copy", "copies")} · ${ByteSize.format(group.bytes)} each", rows)
        }
    }

    /**
     * A view's subtitle: where it looked, and how much it found — "All · 1204 files · 3.2 GB";
     * Trash's, how long it keeps what it holds.
     */
    fun found(view: ExplorerView, rows: List<BrowsedItem>): String {
        val size = ByteSize.format(rows.sumOf { it.bytes ?: 0 })
        val count = if (view == ExplorerView.Trash) countOf(rows.size, "item") else countOf(rows.size, "file")
        val keeps = "removed after ${TrashRules.KEEP_DAYS} days".takeIf { view == ExplorerView.Trash }
        return listOfNotNull(view.scope?.label, count, size, keeps).joinToString(" · ")
    }
}
