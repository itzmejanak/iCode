package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.RelativeTime
import dev.icode.mobile.domain.countOf

/** One file Manage storage counted (docs/explorer-gaps-plan.md §3.10). */
data class ScannedFile(
    val location: String,
    val bytes: Long,
    val changedAt: Long,
    /** What it is: by its name, and by its first bytes where the name could not say (docs/file-kinds-icons-plan.md D3). */
    val type: FileType,
) {
    val category: FileCategory = FileCategories.of(type, location.substringAfterLast('/'))
}

/** One category's share of where Manage storage looked. */
data class CategoryShare(val category: FileCategory, val files: Int, val bytes: Long)

/** Files with the same contents, [bytes] each, the oldest first. */
data class DuplicateGroup(val bytes: Long, val locations: List<String>) {
    /** What every copy but the oldest takes. */
    val extraBytes: Long get() = bytes * (locations.size - 1)
}

/**
 * One of Manage storage's views as its scan lists it, while it is shown (§3.10): its [rows] — for
 * Duplicates, each set's copies, the oldest first, the sets in [groups].
 */
data class ScannedView(val view: ExplorerView, val rows: List<BrowsedItem>, val groups: List<DuplicateGroup> = emptyList())

/**
 * What Manage storage shows (§3.10): every category's share, Git's history apart, the largest files'
 * and the extra copies' space, and when it was scanned — or, while it scans, how many files it has
 * seen so far.
 */
data class StorageOverview(
    val scope: ScanScope,
    val categories: List<CategoryShare>,
    val gitBytes: Long,
    val largestBytes: Long,
    val duplicateGroups: Int,
    val duplicateBytes: Long,
    val scannedAt: Long?,
    val scanning: Long? = null,
    /** Shared was to be looked at, but iCode has no All Files Access: its part is a line asking for it. */
    val sharedUnread: Boolean = false,
) {
    val files: Int get() = categories.sumOf { it.files }
    val bytes: Long get() = categories.sumOf { it.bytes } + gitBytes
}

object StorageInsights {
    /** Largest files lists this many. */
    const val LARGEST = 100

    fun overview(scope: ScanScope, files: List<ScannedFile>, gitBytes: Long, duplicates: List<DuplicateGroup>, scannedAt: Long?, sharedUnread: Boolean) =
        StorageOverview(
            scope = scope,
            categories = FileCategory.entries.map { category ->
                files.filter { it.category == category }.let { CategoryShare(category, it.size, it.sumOf(ScannedFile::bytes)) }
            },
            gitBytes = gitBytes,
            largestBytes = largest(files).sumOf { it.bytes },
            duplicateGroups = duplicates.size,
            duplicateBytes = duplicates.sumOf { it.extraBytes },
            scannedAt = scannedAt,
            sharedUnread = sharedUnread,
        )

    fun largest(files: List<ScannedFile>): List<ScannedFile> = files.sortedByDescending { it.bytes }.take(LARGEST)

    /** Under Manage storage's bar: how much, in how many files — "9.4 GB in 12340 files". */
    fun total(overview: StorageOverview): String = "${ByteSize.format(overview.bytes)} in ${countOf(overview.files, "file")}"

    /** Beside it: how far the scan has got — "Scanning… 4210 files" — or when it last scanned. */
    fun status(overview: StorageOverview, now: Long): String = when {
        overview.scanning != null -> "Scanning… ${countOf(overview.scanning, "file")}"
        overview.scannedAt != null -> "Scanned ${RelativeTime.since(overview.scannedAt, now)}"
        else -> "Not scanned yet"
    }

    /** Each category's share of the bar, and Git's history last: fractions of all it found. */
    fun shares(overview: StorageOverview): List<Pair<FileCategory?, Float>> {
        val all = overview.bytes.takeIf { it > 0 } ?: return emptyList()
        return overview.categories.map { it.category to it.bytes.toFloat() / all } + (null to overview.gitBytes.toFloat() / all)
    }

    /**
     * What [view] lists of what the scan found: a category's files; the largest; every copy of each
     * set of [duplicates], set by set. Null for a view that is not the scan's.
     */
    fun listed(view: ExplorerView, files: List<ScannedFile>, duplicates: List<DuplicateGroup>): List<ScannedFile>? = when (view) {
        is ExplorerView.Category -> files.filter { it.category == view.category }
        is ExplorerView.Largest -> largest(files)
        is ExplorerView.Duplicates -> files.associateBy { it.location }.let { found -> duplicates.flatMap { it.locations }.mapNotNull(found::get) }
        ExplorerView.Recent, ExplorerView.Trash -> null
    }

    /** Files that may be copies of each other: more than one of the same size, none empty. Their contents decide. */
    fun sameSize(files: List<ScannedFile>): List<List<ScannedFile>> =
        files.filter { it.bytes > 0 }.groupBy { it.bytes }.values.filter { it.size > 1 }

    /** "Select extra copies": every copy but the oldest in each group. */
    fun extraCopies(groups: List<DuplicateGroup>): List<String> = groups.flatMap { it.locations.drop(1) }

    /** Where [location] is after iCode's own [steps]: moved with what moved, or null once it went. */
    fun after(location: String, steps: List<FileStep>): String? = steps.fold<FileStep, String?>(location) { at, step ->
        at?.let { here ->
            fun under(path: String) = here == path || here.startsWith("$path/")
            when (step) {
                is FileStep.Moved -> if (under(step.from)) step.to + here.removePrefix(step.from) else here
                is FileStep.Trashed -> here.takeUnless { under(step.from) }
                is FileStep.Removed -> here.takeUnless { under(step.location) }
                is FileStep.Made, is FileStep.Restored -> here
            }
        }
    }

    /**
     * [file] after iCode's own [steps]: where it is now, or null once it went. Its kind stays what was
     * found while its extension does; a new extension is a new kind, by the name.
     */
    fun after(file: ScannedFile, steps: List<FileStep>): ScannedFile? = after(file.location, steps)?.let { location ->
        val name = location.substringAfterLast('/')
        when {
            location == file.location -> file
            FilesRules.extension(name) == FilesRules.extension(file.location.substringAfterLast('/')) -> file.copy(location = location)
            else -> file.copy(location = location, type = FileTypes.of(name))
        }
    }

    /** [group] after iCode's own [steps]: its copies where they are now; null once fewer than two are left. */
    fun after(group: DuplicateGroup, steps: List<FileStep>): DuplicateGroup? =
        group.copy(locations = group.locations.mapNotNull { after(it, steps) }).takeIf { it.locations.size > 1 }
}
