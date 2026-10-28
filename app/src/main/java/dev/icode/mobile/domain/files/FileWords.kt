package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.countOf

/**
 * How an operation is said, done and undone (docs/explorer-gaps-plan.md §3.8): the undo pill's line
 * and the ⋮'s Undo and Redo. One item by its name, more by how many.
 */
object FileWords {
    /** "README.md", or "3 items". */
    fun subject(names: List<String>): String = names.singleOrNull() ?: countOf(names.size, "item")

    fun trashed(names: List<String>) = FileChange("Moved ${subject(names)} to Trash", "Restored ${subject(names)}", emptyList())

    fun restored(names: List<String>) = FileChange("Restored ${subject(names)}", "Moved ${subject(names)} to Trash", emptyList())

    fun moved(names: List<String>, into: String) = FileChange("Moved ${subject(names)} to $into", "Moved ${subject(names)} back", emptyList())

    fun copied(names: List<String>, into: String) = FileChange("Copied ${subject(names)} to $into", "Moved the copies to Trash", emptyList())

    fun duplicated(names: List<String>) = FileChange("Duplicated ${subject(names)}", "Moved the duplicates to Trash", emptyList())

    fun renamed(from: String, to: String) = FileChange("Renamed $from to $to", "Renamed $to back to $from", emptyList())

    fun renamedMany(count: Int) = FileChange("Renamed ${countOf(count, "item")}", "Renamed ${countOf(count, "item")} back", emptyList())

    fun made(name: String, folder: Boolean) = FileChange("Made ${if (folder) "folder" else "file"} $name", "Moved $name to Trash", emptyList())

    fun compressed(names: List<String>, archive: String) = FileChange("Compressed ${subject(names)} into $archive", "Moved $archive to Trash", emptyList())

    fun extracted(archive: String) = FileChange("Extracted $archive", "Moved what $archive held to Trash", emptyList())
}
