package dev.icode.mobile.domain

// Workspaces (S2): the folders a user works in, the browser that finds them, and storage.

data class WorkspaceSummary(
    val name: String,
    val path: String,
    val branch: String?,
    val lastOpened: String,
    val available: Boolean = true,
    /** Stable adapter-owned location. [path] remains presentation-safe. */
    val location: String = path,
)

enum class FolderBrowserPurpose { OpenWorkspace, CreateFolderDestination }

data class FolderBrowserEntry(
    val name: String,
    val path: String,
    val directoryCount: Int,
    val fileCount: Int,
)

data class FolderBrowserState(
    val purpose: FolderBrowserPurpose,
    val rootPath: String,
    val currentPath: String,
    val displayPath: String,
    val folders: List<FolderBrowserEntry>,
)

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
