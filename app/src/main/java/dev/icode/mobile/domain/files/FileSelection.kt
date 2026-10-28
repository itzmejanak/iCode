package dev.icode.mobile.domain.files

import dev.icode.mobile.domain.countOf

/**
 * What is ticked in an explorer (docs/explorer-gaps-plan.md §3.2): locations, of the folder shown or
 * of a view. Select all and Invert work on what is shown — with a search open, on what it found.
 */
object FileSelection {
    /** [location] ticked, or unticked when it was. */
    fun toggled(selected: Set<String>, location: String): Set<String> =
        if (location in selected) selected - location else selected + location

    /** Every item [shown]. */
    fun all(shown: List<String>): Set<String> = shown.toSet()

    /** Those [shown] that were not ticked; what was ticked and is not shown stays out. */
    fun inverted(selected: Set<String>, shown: List<String>): Set<String> = shown.filterNot { it in selected }.toSet()

    /**
     * What a selection acts on: each location once, and none under a folder that is itself ticked —
     * the IDE tree can tick a folder and what is in it, and nothing is acted on twice. In the order given.
     */
    fun covering(locations: List<String>): List<String> {
        val distinct = locations.distinct()
        return distinct.filter { location -> distinct.none { other -> other != location && location.startsWith("$other/") } }
    }

    /** "2 folders, 1 file", "1 folder", "3 files": what a selection holds. */
    fun counted(folders: Int, files: Int): String = listOfNotNull(
        countOf(folders, "folder").takeIf { folders > 0 },
        countOf(files, "file").takeIf { files > 0 || folders == 0 },
    ).joinToString(", ")
}
