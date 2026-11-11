package dev.icode.mobile.data.workspace

import android.content.Context
import dev.icode.mobile.data.files.FileShortcuts
import dev.icode.mobile.data.io.measureTree
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacesProvider
import dev.icode.mobile.domain.notices.NoticeTone
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.ProjectDestination
import dev.icode.mobile.domain.StorageDetails
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.WorkspaceSummary
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The user's library of workspaces (docs/workspace-storage-plan.md §4.3): every folder in iCode's
 * workspaces, most recently opened first — the folder is the registry, so nothing in the list can
 * go stale — and what they and the runtime take up in storage. And the file explorer's listing, of
 * iCode's storage and of phone storage (docs/storage-explorer-plan.md §4.3), with Recent and
 * Favorites, kept in the same store.
 */
internal class WorkspaceLibraryCoordinator(
    private val appContext: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val source: WorkspaceFileSource,
    private val store: WorkspaceStore,
    private val runtimeLayout: RuntimeLayout,
    private val storage: PhoneStorage,
    /** The branch `HEAD` names, read from disk: the launcher names it without starting Git. */
    private val branchOf: (location: String) -> String?,
    /** Clearing the cache under a running task would pull files out from under it. */
    private val tasksRunning: () -> Boolean,
) {
    /** Recent and Favorites (docs/explorer-gaps-plan.md §3.14). */
    val shortcuts = FileShortcuts(stateHolder, store, runtimeLayout.workspaces)

    private var storageJob: Job? = null
    private var sizesJob: Job? = null
    private val paths get() = runtimeLayout.workspaces

    /** Where the explorer goes next, decided as each request arrives. */
    @Volatile private var browsing: String? = null
    private var browseJob: Job? = null
    private val browseLock = Any()

    /** Where each place was last, for [ICodeIntent.BrowsePlace]. */
    private val lastIn = ConcurrentHashMap<StoragePlace, String>()

    /**
     * After a workspace was made, renamed or deleted, and whenever iCode refreshes after a change —
     * its own, or one its watcher saw — so Android's Files app reads its listings again too, and
     * Recent and Favorites leave out what went.
     */
    fun refresh() {
        // Computed before the update so the atomic section stays short and no
        // concurrent writer's fields are lost.
        val workspaces = workspaces()
        val storage = runCatching(source::storageSummary).getOrNull()
        stateHolder.update { it.copy(workspaces = workspaces, workspacesLoaded = true, storage = storage) }
        refreshFiles()
        shortcuts.publish()
        runCatching { WorkspacesProvider.changed(appContext) }
    }

    /** The file explorer's folder, read again after a change — iCode's, or one the watcher saw. */
    fun refreshFiles() {
        browsing?.let(::browse)
    }

    /** Phone storage lists its folder once All Files Access is granted, and waits for it again once it is taken back. */
    fun followAccess() = scope.launch {
        stateHolder.snapshot.map { it.systemAccess?.storage == true }.distinctUntilChanged().collect {
            if (stateHolder.current.filesBrowser?.place == StoragePlace.Shared) refreshFiles()
        }
    }

    /**
     * Every workspace, most recently opened first; one never opened in iCode — made in the
     * terminal, say — by its folder's time. Names starting with a dot are iCode's staging, and a
     * link is not a workspace.
     */
    fun workspaces(): List<WorkspaceSummary> {
        val opened = store.lastOpened()
        return paths.root.listFiles().orEmpty()
            .filter { !it.name.startsWith('.') && Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
            .map { folder ->
                WorkspaceSummary(
                    name = folder.name,
                    path = paths.runtimePathOf(folder.path),
                    // Reading .git/HEAD keeps the launcher instant; starting a Git
                    // process for every workspace would cost a PRoot launch each.
                    branch = runCatching { branchOf(folder.path) }.getOrNull(),
                    lastOpenedAt = opened[folder.path] ?: folder.lastModified(),
                    location = folder.path,
                )
            }
            .sortedByDescending { it.lastOpenedAt }
    }

    /** [location] was opened: it moves to the top of the list. */
    fun remember(location: String) = store.touch(location, System.currentTimeMillis())

    /** A workspace renamed: its place in the list and saved session follow. */
    suspend fun relocate(from: String, to: String) {
        runCatching { store.relocate(from, to) }
            .onFailure { error -> stateHolder.report("workspace", "The workspace's history could not follow the rename", error) }
        refresh()
    }

    /** A workspace deleted: what was kept about it goes. */
    suspend fun forget(location: String) {
        runCatching { store.forget(location) }
            .onFailure { error -> stateHolder.report("workspace", "The deleted workspace's history could not be cleared", error) }
        refresh()
    }

    fun read(intent: ICodeIntent.StorageIntent) {
        when (intent) {
            is ICodeIntent.BrowseFiles -> {
                showing(null)
                browse(intent.path ?: browsing ?: paths.root.path)
            }
            is ICodeIntent.BrowsePlace -> {
                showing(null)
                browse(lastIn[intent.place] ?: if (intent.place == StoragePlace.Shared) storage.root.path else paths.root.path)
            }
            is ICodeIntent.BrowseView -> showing(intent.view)
            is ICodeIntent.ChooseDestination -> scope.launch(Dispatchers.IO) { chooseDestination(intent.path) }
            ICodeIntent.RefreshStorage -> refreshStorage()
            ICodeIntent.MeasureWorkspaces -> measureWorkspaces()
        }
    }

    /** A folder of a workspace as the next project's place, with what it holds for the name's check; none, a new workspace. */
    private fun chooseDestination(path: String?) {
        val destination = path?.let { location ->
            val shown = paths.guestOf(location)?.takeIf { paths.workspaceOf(location) != null && File(location).isDirectory } ?: return
            ProjectDestination(location, shown, File(location).list()?.toSet().orEmpty())
        }
        stateHolder.update { it.copy(projectDestination = destination) }
    }

    /** The explorer showing [view] in place of a folder (docs/explorer-gaps-plan.md §3.10); none, its folder again. */
    private fun showing(view: ExplorerView?) = stateHolder.update { it.copy(explorer = it.explorer.copy(view = view)) }

    /** The explorer goes to [path]; an older listing still running stops, and is never shown after a newer one. */
    private fun browse(path: String): Unit = synchronized(browseLock) {
        browsing = path
        browseJob?.cancel()
        browseJob = scope.launch(Dispatchers.IO) {
            val listed = listing(path)
            synchronized(browseLock) {
                if (!isActive) return@launch
                browsing = listed.path
                lastIn[listed.place] = listed.path
                stateHolder.update { it.copy(filesBrowser = listed) }
            }
        }
    }

    /**
     * What the explorer shows at [path]: on phone storage every entry, or nothing until All Files
     * Access; in iCode's storage the workspaces at the top, and inside one what the Explorer lists
     * (not `.git`, no link that leads out). Hidden files, as the setting says. A folder gone
     * meanwhile leaves it at its place's top.
     */
    private fun listing(path: String): FilesBrowserState {
        val shared = storage.root.path
        val showHidden = stateHolder.current.explorer.settings.showHidden
        if (storage.contains(path)) {
            if (!storage.hasAccess()) return FilesBrowserState(StoragePlace.Shared, shared, path, access = false, sharedRoot = shared)
            val at = storage.folder(path)?.path ?: shared
            return FilesBrowserState(StoragePlace.Shared, shared, at, runCatching { storage.list(at, showHidden) }.getOrDefault(emptyList()), sharedRoot = shared)
        }
        val root = paths.root.path
        val at = path.takeIf { it == root || (paths.workspaceOf(it) != null && File(it).isDirectory) } ?: root
        val items = if (at == root) {
            workspaces().map { BrowsedItem(it.name, it.location, it.path, StoragePlace.Private, folder = true, changedAt = it.lastOpenedAt, entries = File(it.location).list()?.size, branch = it.branch) }
        } else {
            runCatching { source.listChildren(at, showHidden) }.getOrDefault(emptyList()).map { node ->
                val file = File(node.location)
                val folder = node.kind == FileKind.Folder
                BrowsedItem(node.name, node.location, paths.runtimePathOf(node.location), StoragePlace.Private, folder, bytes = file.length().takeUnless { folder }, changedAt = file.lastModified(), entries = if (folder) file.list()?.size else null)
            }
        }
        return FilesBrowserState(StoragePlace.Private, root, at, items, sharedRoot = shared)
    }

    /** The device, the runtime and the cache; the newest measurement replaces one still running. */
    private fun refreshStorage() {
        storageJob?.cancel()
        storageJob = scope.launch(Dispatchers.IO) {
            val device = runCatching(source::storageSummary).getOrNull()
            val details = device?.let { summary -> StorageDetails(
                device = summary,
                runtime = measureTree(runtimeLayout.runtimeRoot, maxEntries = 400_000),
                cache = measureTree(appContext.cacheDir, maxEntries = 50_000),
            ) }
            currentCoroutineContext().ensureActive()
            stateHolder.update { it.copy(workspaces = workspaces(), storage = device, storageDetails = details) }
        }
    }

    /**
     * Each workspace's size, one after another, each shown as it is measured; the newest request
     * replaces one still running. A workspace gone meanwhile leaves the map.
     */
    private fun measureWorkspaces() {
        sizesJob?.cancel()
        sizesJob = scope.launch(Dispatchers.IO) {
            val workspaces = workspaces()
            stateHolder.update { snapshot -> snapshot.copy(workspaceSizes = snapshot.workspaceSizes.filterKeys { key -> workspaces.any { it.location == key } }) }
            workspaces.forEach { item ->
                val size = measureTree(File(item.location), maxEntries = WORKSPACE_ENTRIES)
                currentCoroutineContext().ensureActive()
                stateHolder.update { it.copy(workspaceSizes = it.workspaceSizes + (item.location to size)) }
            }
        }
    }

    suspend fun clearCache() {
        if (tasksRunning()) {
            stateHolder.notify(NoticeTone.HeadsUp, "Finish runtime, Git, terminal and language service tasks before clearing cache")
            return
        }
        runCatching {
            StorageInspector.clearRuntimeCache(appContext.cacheDir, runtimeLayout.prootTemp)
        }.onSuccess { removed ->
            stateHolder.notify(NoticeTone.Done, if (removed == 0) "Runtime cache is already clear" else "Removed $removed cached runtime items")
            refreshStorage()
        }.onFailure { error ->
            stateHolder.report("storage", "Unable to clear runtime cache", error)
        }
    }

    fun lastSession(): StoredSession? = store.loadSession()

    /** Kept as it changes, so a process Android ends can reopen the same workspace and tabs. */
    suspend fun saveSession(session: StoredSession?) {
        runCatching { store.saveSession(session) }
            .onFailure { error -> stateHolder.report("workspace", "The open workspace could not be remembered", error) }
    }

    /** The workspace [location] lies in, when it is there; null for anything outside the workspaces. */
    fun workspaceHolding(location: String): String? = paths.workspaceOf(location)?.takeIf { File(it).isDirectory }

    private companion object {
        /** A workspace's measurement stops here and says so: node_modules alone can hold 50,000. */
        const val WORKSPACE_ENTRIES = 400_000
    }
}
