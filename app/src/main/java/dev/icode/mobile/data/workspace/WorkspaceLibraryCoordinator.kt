package dev.icode.mobile.data.workspace

import android.content.Context
import android.os.Environment
import dev.icode.mobile.data.git.GitHeadReader
import dev.icode.mobile.data.io.measureTree
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.StorageDetails
import dev.icode.mobile.domain.WorkspaceSummary
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The user's library of workspaces (S2): recent workspaces and recent files —
 * the one writer of the workspace store — and what they and the runtime take
 * up in storage.
 */
internal class WorkspaceLibraryCoordinator(
    private val appContext: Context,
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val source: DirectFileWorkspaceSource,
    private val store: WorkspaceStore,
    private val runtimeLayout: RuntimeLayout,
    /** Clearing the cache under a running task would pull files out from under it. */
    private val tasksRunning: () -> Boolean,
) {
    private var storageJob: Job? = null

    fun refreshRecents() {
        // Computed before the update so the atomic section stays short and no
        // concurrent writer's fields are lost.
        val recents = recentWorkspaces()
        val storage = runCatching(source::storageSummary).getOrNull()
        workbench.update { it.copy(workspaces = recents, storage = storage) }
    }

    /** Puts [root] at the top of the recent workspaces. */
    fun remember(root: WorkspaceRoot) {
        store.touch(
            StoredWorkspace(
                name = root.name,
                location = root.location,
                displayPath = root.displayPath,
                lastOpenedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun recentWorkspaces(): List<WorkspaceSummary> = store.load().map { workspace ->
        val available = Environment.isExternalStorageManager() && source.isAvailable(workspace.location)
        WorkspaceSummary(
            name = workspace.name,
            path = workspace.displayPath,
            // Reading .git/HEAD keeps the launcher instant; starting a Git
            // process for every recent workspace would cost a PRoot launch each.
            branch = if (available) runCatching { GitHeadReader.branchOf(workspace.location) }.getOrNull() else null,
            lastOpenedAt = workspace.lastOpenedAt,
            available = available,
            location = workspace.location,
        )
    }

    suspend fun removeRecent(location: String) {
        runCatching { store.remove(location) }
            .onSuccess { workbench.update { state ->
                state.copy(workspaces = state.workspaces.filterNot { it.location == location })
            } }
            .onFailure { error ->
                workbench.report("workspace", "Unable to remove recent workspace", error)
            }
    }

    suspend fun removeStale() {
        if (!Environment.isExternalStorageManager() || Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            workbench.message("Storage access is needed before checking recent workspace paths")
            return
        }
        runCatching {
            val missing = store.load().filter { !File(it.location).exists() }
            missing.forEach { store.remove(it.location) }
            refreshRecents()
            refreshStorage()
            missing.size
        }.onSuccess { removed ->
            workbench.message(
                if (removed == 0) "No stale recent workspaces found"
                else "Removed $removed stale recent shortcuts; no workspace files were deleted",
            )
        }.onFailure { error ->
            workbench.report("workspace", "Unable to remove stale recents", error)
        }
    }

    /** The newest measurement replaces one still running. */
    fun refreshStorage() {
        storageJob?.cancel()
        storageJob = scope.launch(Dispatchers.IO) {
            val device = runCatching(source::storageSummary).getOrNull()
            val workspaces = store.load()
            val details = device?.let { summary -> StorageDetails(
                device = summary,
                runtime = measureTree(runtimeLayout.runtimeRoot, maxEntries = 400_000),
                cache = measureTree(appContext.cacheDir, maxEntries = 50_000),
                workspaceSizes = workspaces.associate { item ->
                    item.location to measureTree(File(item.location), maxEntries = 25_000)
                },
            ) }
            currentCoroutineContext().ensureActive()
            workbench.update { it.copy(storage = device, storageDetails = details) }
        }
    }

    suspend fun clearCache() {
        if (tasksRunning()) {
            workbench.message("Finish runtime, Git, terminal and language service tasks before clearing cache")
            return
        }
        runCatching {
            StorageInspector.clearRuntimeCache(appContext.cacheDir, runtimeLayout.prootTemp)
        }.onSuccess { removed ->
            workbench.message(
                if (removed == 0) "Runtime cache is already clear" else "Removed $removed cached runtime items",
            )
            refreshStorage()
        }.onFailure { error ->
            workbench.report("storage", "Unable to clear runtime cache", error)
        }
    }

    suspend fun recordRecentDocument(root: String, location: String) {
        runCatching {
            store.touchRecentDocument(
                StoredRecentDocument(File(location).name, File(location).canonicalPath, root, System.currentTimeMillis()),
            )
            workbench.update { it.copy(recentDocuments = recentDocuments(root)) }
        }.onFailure { error ->
            workbench.report("documents", "File opened, but recent files could not be saved", error)
        }
    }

    /** Recent files of [root] only; an entry that has left the workspace is dropped. */
    fun recentDocuments(root: String): List<RecentDocument> = store.loadRecentDocuments(root)
        .mapNotNull { item ->
            val relative = runCatching { File(root).canonicalFile.toPath().relativize(File(item.location).canonicalFile.toPath()) }.getOrNull()
                ?: return@mapNotNull null
            if (relative.isAbsolute || relative.any { it.toString() == ".." || it.toString() == ".git" }) return@mapNotNull null
            RecentDocument(
                name = item.name,
                location = item.location,
                path = relative.toString(),
                lastOpenedAt = item.lastOpenedAt,
                available = File(item.location).isFile,
            )
        }
}
