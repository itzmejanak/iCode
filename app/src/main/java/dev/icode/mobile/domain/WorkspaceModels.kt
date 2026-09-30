package dev.icode.mobile.domain

// Workspaces: the folders in iCode's storage a user works in (docs/workspace-storage-plan.md), and storage.

/** Where the workspaces are in the runtime; each is at `/workspace/<name>`, the path people see. */
object WorkspaceRuntimePath {
    const val ROOT = "/workspace"

    fun of(name: String) = "$ROOT/$name"
}

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

/** A folder on phone storage, as the Import browser lists it. */
data class PhoneFolder(
    val name: String,
    val path: String,
    val directoryCount: Int,
    val fileCount: Int,
)

/**
 * Import's view of phone storage (docs/workspace-storage-plan.md §4.5): where it is and the folders
 * there. Without All Files Access ([access] false) it lists nothing and waits at [currentPath]
 * until the permission is granted.
 */
data class ImportBrowserState(
    val access: Boolean,
    val rootPath: String,
    val currentPath: String,
    /** "Internal storage / Projects / RevPlayer". */
    val displayPath: String,
    val folders: List<PhoneFolder> = emptyList(),
) {
    /** The root of internal storage is where folders are, not a folder to import. */
    val atRoot: Boolean get() = currentPath == rootPath
    val name: String get() = currentPath.substringAfterLast('/')
}

/** A folder chosen on phone storage for Import: where it is, and the name it proposes. */
data class ImportSource(val path: String, val name: String, val displayPath: String)

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
