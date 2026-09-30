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
    val folders: List<PhoneFolder> = emptyList(),
) {
    /** The root of phone storage is where folders are, not a folder to import. */
    val atRoot: Boolean get() = currentPath == rootPath
    val name: String get() = currentPath.substringAfterLast('/')

    /** The breadcrumb: sdcard, then each folder down to here. */
    val crumbs: List<ImportCrumb> get() = PhoneStoragePaths.crumbs(rootPath, currentPath)
}

/** One step of Import's breadcrumb: a folder's name, and where it is. */
data class ImportCrumb(val name: String, val path: String)

/** How iCode names places on phone storage — the browser's breadcrumb and Import's "From" alike. */
object PhoneStoragePaths {
    /** The root, by the name developers know it (owner, 2026-09-30). */
    const val ROOT_NAME = "sdcard"

    /** Each folder from [rootPath] down to [path], the root first. */
    fun crumbs(rootPath: String, path: String): List<ImportCrumb> {
        var at = rootPath
        val below = path.removePrefix(rootPath).split('/').filter(String::isNotEmpty).map { name ->
            at = "$at/$name"
            ImportCrumb(name, at)
        }
        return listOf(ImportCrumb(ROOT_NAME, rootPath)) + below
    }

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
