package dev.icode.mobile.domain

// Workspaces: the folders in iCode's storage a user works in (docs/workspace-storage-plan.md), and storage.

/** Where the workspaces are in the runtime; each is at `/workspace/<name>`, the path people see. */
object WorkspaceRuntimePath {
    const val ROOT = "/workspace"

    fun of(name: String) = "$ROOT/$name"

    /** Where a new project named [name] will be: a new workspace, or inside [destination]. */
    fun of(name: String, destination: ProjectDestination?) = destination?.let { "${it.displayPath}/$name" } ?: of(name)

    /** [location], inside the workspace at [root] whose runtime path is [rootPath], as the runtime names it: what Copy path gives. */
    fun within(root: String, rootPath: String, location: String): String = rootPath + location.removePrefix(root)
}

/**
 * Where the next clone or import goes (docs/workspace-destinations-plan.md): a folder in a
 * workspace. None means a new workspace, as ever.
 */
data class ProjectDestination(
    val location: String,
    /** `/workspace/tools/cli`: as the runtime names it. */
    val displayPath: String,
    /** What it held when chosen, for the name's live check; the backend checks again. */
    val taken: Set<String> = emptySet(),
)

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

data class StorageSummary(
    val availableBytes: Long,
    val totalBytes: Long,
)

data class StorageMeasurement(val bytes: Long?, val truncated: Boolean = false)

data class StorageDetails(
    val device: StorageSummary,
    val runtime: StorageMeasurement,
    val cache: StorageMeasurement,
)
