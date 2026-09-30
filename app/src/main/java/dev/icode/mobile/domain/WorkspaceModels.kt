package dev.icode.mobile.domain

// Workspaces: the folders in iCode's storage a user works in (docs/workspace-storage-plan.md), and storage.

/** Where the workspaces are in the runtime; each is at `/workspace/<name>`, the path people see. */
object WorkspaceRuntimePath {
    const val ROOT = "/workspace"

    fun of(name: String) = "$ROOT/$name"

    /** Where a new project named [name] will be: a new workspace, or inside [destination]. */
    fun of(name: String, destination: ProjectDestination?) = destination?.let { "${it.displayPath}/$name" } ?: of(name)
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
    val folders: List<PhoneFolder> = emptyList(),
) {
    /** The root of phone storage is where folders are, not a folder to import. */
    val atRoot: Boolean get() = currentPath == rootPath
    val name: String get() = currentPath.substringAfterLast('/')

    /** The breadcrumb: sdcard, then each folder down to here. */
    val crumbs: List<FolderCrumb> get() = PhoneStoragePaths.crumbs(rootPath, currentPath)
}

/** One step of a breadcrumb — Import's browser, the workspaces' file explorer: a folder's name, and where it is. */
data class FolderCrumb(val name: String, val path: String)

object FolderCrumbs {
    /** Each folder from [rootPath] — named [rootName] — down to [path], the root first. */
    fun of(rootName: String, rootPath: String, path: String): List<FolderCrumb> {
        var at = rootPath
        val below = path.removePrefix(rootPath).split('/').filter(String::isNotEmpty).map { name ->
            at = "$at/$name"
            FolderCrumb(name, at)
        }
        return listOf(FolderCrumb(rootName, rootPath)) + below
    }
}

/** How iCode names places on phone storage — the browser's breadcrumb and Import's "From" alike. */
object PhoneStoragePaths {
    /** The root, by the name developers know it (owner, 2026-09-30). */
    const val ROOT_NAME = "sdcard"

    fun crumbs(rootPath: String, path: String): List<FolderCrumb> = FolderCrumbs.of(ROOT_NAME, rootPath, path)

    /** "sdcard / Projects / RevPlayer". */
    fun display(rootPath: String, path: String): String = crumbs(rootPath, path).joinToString(" / ") { it.name }
}

/**
 * What is cut or copied, anywhere in iCode's workspaces, waiting for Paste
 * (docs/workspaces-page-plan.md §2.7). A cut item moves once; a copied one pastes again.
 */
data class ItemClipboard(
    val location: String,
    val name: String,
    val cut: Boolean,
    val folder: Boolean,
    /** A whole workspace, not something in one. */
    val workspace: Boolean,
)

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
)
