package dev.icode.mobile.domain.files

/** How the explorer draws what it lists (docs/explorer-gaps-plan.md §3.12): rows with detail, single lines, or tiles. */
enum class FilesView { Detailed, Compact, Grid }

/** A list's view and order: what the ☰ panel chooses. */
data class FilesLook(
    val view: FilesView = FilesView.Detailed,
    val sort: FilesSort = FilesSort.Date,
    val descending: Boolean = sort.descendingFirst,
) {
    /** Ordered by [sort] instead, the way it reads first: names A→Z, the largest and the newest first. */
    fun sortedBy(sort: FilesSort): FilesLook = copy(sort = sort, descending = sort.descendingFirst)

    /** As settings keep it: "Grid,Size,desc". */
    val encoded: String get() = "${view.name},${sort.name},${if (descending) DESCENDING else ASCENDING}"

    companion object {
        private const val DESCENDING = "desc"
        private const val ASCENDING = "asc"

        /** What [encoded] kept; null for anything else, which reads as no choice made. */
        fun decode(text: String): FilesLook? {
            val (view, sort, order) = text.split(',').takeIf { it.size == 3 } ?: return null
            return FilesLook(
                FilesView.entries.firstOrNull { it.name == view } ?: return null,
                FilesSort.entries.firstOrNull { it.name == sort } ?: return null,
                when (order) {
                    DESCENDING -> true
                    ASCENDING -> false
                    else -> return null
                },
            )
        }
    }
}

/** Where Manage storage looks (§3.10): both places, or one. */
enum class ScanScope(val label: String) { All("All"), Private("Private"), Shared("Shared") }

/**
 * A list the explorer shows that is no folder (§3.10): files gathered from anywhere. Each keeps its
 * own look, under [key], and opens with [opensWith] until it is changed.
 */
sealed interface ExplorerView {
    val key: String
    val title: String
    val opensWith: FilesLook

    /** The files opened in iCode, newest first. */
    data object Recent : ExplorerView {
        override val key = "recent"
        override val title = "Recent"
        override val opensWith = FilesLook(sort = FilesSort.Date)
    }

    /** What was deleted, from both places, newest first. */
    data object Trash : ExplorerView {
        override val key = "trash"
        override val title = "Trash"
        override val opensWith = FilesLook(sort = FilesSort.Date)
    }

    /** Every file of [category] where Manage storage looked: pictures and videos as tiles, the largest first. */
    data class Category(val category: FileCategory, val scope: ScanScope) : ExplorerView {
        override val key get() = "category-${category.name}"
        override val title get() = category.label
        override val opensWith get() = FilesLook(
            view = if (category == FileCategory.Images || category == FileCategory.Videos) FilesView.Grid else FilesView.Detailed,
            sort = FilesSort.Size,
        )
    }

    /** The largest files where Manage storage looked. */
    data class Largest(val scope: ScanScope) : ExplorerView {
        override val key = "largest"
        override val title = "Largest files"
        override val opensWith = FilesLook(sort = FilesSort.Size)
    }

    /** Files with the same contents, in groups. */
    data class Duplicates(val scope: ScanScope) : ExplorerView {
        override val key = "duplicates"
        override val title = "Duplicates"
        override val opensWith = FilesLook(sort = FilesSort.Size)
    }
}

/**
 * The explorer's settings, app-wide (§3.12, §3.13): whether names starting with "." are listed, the
 * folders' one look, and each view's own once changed.
 */
data class ExplorerSettings(
    val showHidden: Boolean = true,
    val folders: FilesLook = FilesLook(),
    val views: Map<String, FilesLook> = emptyMap(),
) {
    /** The look of [view], or of folders for none. */
    fun look(view: ExplorerView?): FilesLook = if (view == null) folders else views[view.key] ?: view.opensWith

    /** [look] kept for [view], or for folders. */
    fun with(view: ExplorerView?, look: FilesLook): ExplorerSettings =
        if (view == null) copy(folders = look) else copy(views = views + (view.key to look))

    companion object {
        /** A name the hidden-files setting hides: one starting with a dot. */
        fun hidden(name: String): Boolean = name.startsWith('.')
    }
}
