package dev.icode.mobile.domain

// The workspace list (docs/workspaces-page-plan.md): the one list of workspaces, as the home screen
// and Storage settings show it; the Workspaces page orders and finds it as a file explorer (FilesRules).

object WorkspaceListRules {
    /** How many the home screen lists, as VS Code's welcome page lists its recent folders. */
    const val RECENT_ON_HOME = 5

    /** The home screen's window on the list: the most recently opened. */
    fun recent(workspaces: List<WorkspaceSummary>): List<WorkspaceSummary> =
        workspaces.sortedByDescending { it.lastOpenedAt }.take(RECENT_ON_HOME)

    /**
     * "3 workspaces · 1.2 GB": the total only once every one is measured whole — a partial sum
     * would be a number nobody measured.
     */
    fun summary(workspaces: List<WorkspaceSummary>, sizes: Map<String, StorageMeasurement>): String {
        val count = countOf(workspaces.size, "workspace")
        val measured = workspaces.map { sizes[it.location] }
        if (workspaces.isEmpty() || measured.any { it?.bytes == null || it.truncated }) return count
        return "$count · ${ByteSize.format(measured.sumOf { it!!.bytes!! })}"
    }
}

/** A measured size in the words every screen uses: the Storage and Runtime settings, the Workspaces page. */
fun storageSizeLabel(measurement: StorageMeasurement?): String = when {
    measurement == null -> "Measuring…"
    measurement.truncated -> "Size exceeds scan limit"
    measurement.bytes == null -> "Size unavailable"
    else -> ByteSize.format(measurement.bytes)
}
