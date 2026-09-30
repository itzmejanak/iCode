package dev.icode.mobile.domain

// Workspaces: the folders in iCode's storage a user works in (docs/workspace-storage-plan.md), and storage.

data class WorkspaceSummary(
    val name: String,
    /** Where it is in the runtime, `/workspace/<name>`: the path people see. */
    val path: String,
    val branch: String?,
    /** When it was last opened; screens say how long ago, so it never goes stale in the snapshot. */
    val lastOpenedAt: Long,
    /** Stable adapter-owned location. [path] remains presentation-safe. */
    val location: String = path,
)

/** A folder picked in Android's folder picker for Import: its grant, and the name it proposes. */
data class ImportSource(val uri: String, val name: String)

data class StorageSummary(
    val availableBytes: Long,
    val totalBytes: Long,
)

data class StorageMeasurement(val bytes: Long?, val truncated: Boolean = false)

data class StorageDetails(
    val device: StorageSummary,
    val runtime: StorageMeasurement,
    val cache: StorageMeasurement,
    val workspaceSizes: Map<String, StorageMeasurement>,
)
