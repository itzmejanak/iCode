package dev.icode.mobile.domain

// The workspace list (docs/workspaces-page-plan.md): the one list of workspaces, as the home screen,
// the Workspaces page and Storage settings each show it.

enum class WorkspaceSort { Recent, Name, Size }

object WorkspaceListRules {
    /** How many the home screen lists, as VS Code's welcome page lists its recent folders. */
    const val RECENT_ON_HOME = 5

    /** The home screen's window on the list: the most recently opened. */
    fun recent(workspaces: List<WorkspaceSummary>): List<WorkspaceSummary> =
        workspaces.sortedByDescending { it.lastOpenedAt }.take(RECENT_ON_HOME)

    /** Those whose name or `/workspace/` path holds [query], ignoring case; all of them for a blank one. */
    fun matching(workspaces: List<WorkspaceSummary>, query: String): List<WorkspaceSummary> {
        val wanted = query.trim()
        if (wanted.isEmpty()) return workspaces
        return workspaces.filter { it.name.contains(wanted, ignoreCase = true) || it.path.contains(wanted, ignoreCase = true) }
    }

    /** By [sort]; by size, the measured ones first, largest on top, then the rest by recency. */
    fun sorted(
        workspaces: List<WorkspaceSummary>,
        sort: WorkspaceSort,
        sizes: Map<String, StorageMeasurement>,
    ): List<WorkspaceSummary> = when (sort) {
        WorkspaceSort.Recent -> workspaces.sortedByDescending { it.lastOpenedAt }
        WorkspaceSort.Name -> workspaces.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        WorkspaceSort.Size -> workspaces.sortedWith(
            compareByDescending<WorkspaceSummary> { sizes[it.location]?.bytes ?: -1L }.thenByDescending { it.lastOpenedAt },
        )
    }

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
