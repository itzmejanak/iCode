package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.countOf

/**
 * A list the explorer shows that is no folder (docs/explorer-gaps-plan.md §3.10): files gathered
 * from anywhere. Each keeps its own look, under [key], and opens with [opensWith] until it is
 * changed. Manage storage's views list where it looked, [scope]; Recent and Trash hold what iCode
 * did anywhere.
 */
sealed interface ExplorerView {
    val key: String
    val title: String
    val opensWith: FilesLook
    val scope: ScanScope?

    /** As a screen keeps it while it is away: "recent", "category-Images:All". */
    val encoded: String get() = listOfNotNull(key, scope?.name).joinToString(":")

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

    companion object {
        /** What [encoded] kept; null for anything else. */
        fun decode(text: String): ExplorerView? {
            listOf(Recent, Trash).firstOrNull { it.encoded == text }?.let { return it }
            val (key, scoped) = text.split(':').takeIf { it.size == 2 } ?: return null
            val scope = ScanScope.entries.firstOrNull { it.name == scoped } ?: return null
            return (listOf(Largest(scope), Duplicates(scope)) + FileCategory.entries.map { Category(it, scope) }).firstOrNull { it.key == key }
        }
    }
}

object ExplorerViews {
    /**
     * What [view] lists, as the explorer's rows: Recent's files; Trash's items, each where it is held.
     * Manage storage's views list what its scan publishes (docs/explorer-gaps-plan.md §6, F6).
     */
    fun rows(view: ExplorerView, explorer: ExplorerState): List<BrowsedItem> = when (view) {
        ExplorerView.Recent -> explorer.recent
        ExplorerView.Trash -> explorer.trash.map { it.row }
        is ExplorerView.Category, is ExplorerView.Largest, is ExplorerView.Duplicates -> emptyList()
    }

    /**
     * A view's subtitle (§3.10): where it looked, and how much it found — "All · 1204 files · 3.2 GB";
     * Trash's, how long it keeps what it holds.
     */
    fun found(view: ExplorerView, rows: List<BrowsedItem>): String {
        val size = ByteSize.format(rows.sumOf { it.bytes ?: 0 })
        val count = if (view == ExplorerView.Trash) countOf(rows.size, "item") else countOf(rows.size, "file")
        val keeps = "removed after ${TrashRules.KEEP_DAYS} days".takeIf { view == ExplorerView.Trash }
        return listOfNotNull(view.scope?.label, count, size, keeps).joinToString(" · ")
    }
}
