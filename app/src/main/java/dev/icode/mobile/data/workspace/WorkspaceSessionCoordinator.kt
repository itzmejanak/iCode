package dev.icode.mobile.data.workspace

import android.os.Environment
import dev.icode.mobile.data.git.GitWorkspaceCoordinator
import dev.icode.mobile.data.language.LanguageServiceCoordinator
import dev.icode.mobile.data.runtime.TerminalCoordinator
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchIntent
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The open workspace (X3): which folder is open — every feature that works in
 * it asks [activeRoot] — opening and switching it, keeping it current (the
 * watcher while iCode is seen, one settled refresh per burst of requests), and
 * bringing it back after Android ended the process. A switch reaches each
 * feature that holds something of the old workspace, so this orchestrates them;
 * the gateway only builds the features and routes intents.
 */
internal class WorkspaceSessionCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val source: DirectFileWorkspaceSource,
    private val tree: WorkspaceTree,
    private val library: WorkspaceLibraryCoordinator,
    private val documents: DocumentSessionCoordinator,
    private val recovery: DocumentRecoveryCoordinator,
    private val gitWorkspace: GitWorkspaceCoordinator,
    private val search: SearchCoordinator,
    private val terminal: TerminalCoordinator,
    private val languages: LanguageServiceCoordinator,
) {
    @Volatile var activeRoot: String? = null
        private set

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Quick Open's index is rebuilt only when entries may have come or gone, not after every save. */
    @Volatile private var indexStale = false
    @Volatile private var screenVisible = false
    private val watcher = WorkspaceWatcher(scope) { structure -> requestRefresh(rebuildIndex = structure) }

    /** The refresh loop, the watcher's folders and the last session; once, when the gateway starts. */
    fun start() {
        scope.launch { refreshOnRequest() }
        scope.launch { followWatchedFolders() }
        scope.launch { restoreThenKeepSession() }
    }

    suspend fun open(
        location: String,
        persist: Boolean,
        openTerminal: Boolean = false,
        dirtyChoiceResolved: Boolean = false,
        /** False when reopening the last session: nothing navigates. */
        announce: Boolean = true,
    ) {
        if (!Environment.isExternalStorageManager()) {
            workbench.message("All files access is needed to open this folder")
            return
        }
        if (!source.isAvailable(location)) {
            val reason = if (!File(location).exists()) "This folder no longer exists" else "This folder is not accessible"
            workbench.message(reason)
            return
        }
        if (location == activeRoot) {
            if (openTerminal) terminal.openIn(location)
            if (announce) {
                workbench.emit(
                    if (openTerminal) WorkbenchEffect.WorkspaceTerminalOpened(location)
                    else WorkbenchEffect.WorkspaceOpened(location),
                )
            }
            return
        }
        val dirtyCount = workbench.current.openDocuments.count { it.dirty }
        if (dirtyCount > 0 && !dirtyChoiceResolved) {
            workbench.emit(WorkbenchEffect.ConfirmWorkspaceSwitch(location, openTerminal, dirtyCount))
            return
        }
        runCatching {
            val root = source.describeRoot(location)
            val children = source.listChildren(root.location, root = root.location)
            val gitPicture = gitWorkspace.read(root.location)
            val closingDocuments = workbench.current.openDocuments
                .filter { it.dirty || !recovery.isUnresolved(it.location) }
                .map { it.location }
            languages.stopAll()
            activeRoot = root.location
            tree.reset(root.location, children)
            if (persist) library.remember(root)
            val recents = library.recentWorkspaces()
            workbench.update { snapshot -> snapshot.copy(
                workspaces = recents,
                files = tree.rows(root.location, gitPicture.state),
                gitChanges = gitPicture.state.changes,
                gitRepository = gitPicture.state.repository,
                localGitAuthorName = null,
                localGitAuthorEmail = null,
                gitIdentityLoaded = false,
                activeWorkspace = root.name,
                activePath = root.displayPath,
                activeWorkspaceLocation = root.location,
                gitAvailable = gitPicture.available,
                activeBranch = gitPicture.branch,
                activeDocument = null,
                openDocuments = emptyList(),
                recentDocuments = library.recentDocuments(root.location),
                fileIndex = emptyList(),
                fileIndexTruncated = false,
                contentSearch = ContentSearchState.Empty,
                externalChanges = emptyList(),
            ) }
            documents.clear()
            recovery.workspaceClosing(closingDocuments)
            search.workspaceClosing()
            gitWorkspace.workspaceClosing()
            if (openTerminal) terminal.openIn(root.location)
            if (announce) {
                workbench.emit(
                    if (openTerminal) WorkbenchEffect.WorkspaceTerminalOpened(location)
                    else WorkbenchEffect.WorkspaceOpened(location),
                )
            }
        }.onFailure { error ->
            library.refreshRecents()
            workbench.report("workspace", "Unable to open folder", error)
        }
    }

    suspend fun resolveSwitch(intent: WorkbenchIntent.ResolveWorkspaceSwitch) {
        if (intent.saveChanges) {
            val dirty = workbench.current.openDocuments.filter { it.dirty }
            for (document in dirty) if (!documents.save(document.location)) return
        }
        open(intent.path, persist = true, openTerminal = intent.openTerminal, dirtyChoiceResolved = true)
    }

    fun requestRefresh(rebuildIndex: Boolean) {
        if (rebuildIndex) indexStale = true
        refreshRequests.tryEmit(Unit)
    }

    /** The watcher runs only while iCode is seen. */
    fun screenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) watcher.watch(physical(watchedFolders(workbench.current))) else watcher.stop()
    }

    fun stopWatching() = watcher.stop()

    /** Watched where their bytes are: a mount's own folder, not the empty placeholder in the user's. */
    private fun physical(folders: Set<File>): Set<File> {
        val tree = activeRoot?.let(source::workspace) ?: return folders
        return folders.mapTo(mutableSetOf()) { tree.bytes(it.path) }
    }

    /**
     * Opens a draft's file for the recovery question, switching to its
     * workspace first when it belongs to another one the user has opened.
     */
    suspend fun openRecoverableDraft(location: String) {
        val root = activeRoot
        if (root == null || !location.isAtOrUnder(root)) {
            val home = library.workspaceHolding(location) ?: run {
                workbench.message("Open the folder that holds ${File(location).name} first; its draft is kept")
                return
            }
            open(home, persist = true)
            // A switch that waits on unsaved edits, or failed, has said so already.
            if (activeRoot != home) return
        }
        documents.open(location)
    }

    /** A file changed on disk under unsaved edits: the two versions, in the diff view. */
    suspend fun compareWithDisk(location: String) {
        val change = workbench.current.externalChanges.firstOrNull { it.location == location } ?: return
        val document = workbench.current.openDocuments.firstOrNull { it.location == location } ?: return
        val read = documents.sourceOf(location) ?: return
        gitWorkspace.compareWithEditor(change.path, DocumentCodec.encode(document.text, read.encoding, read.hasByteOrderMark))
    }

    /**
     * Resume, leaving the terminal and a finished command often ask together;
     * one refresh answers every request made while the last one ran.
     */
    private suspend fun refreshOnRequest() {
        refreshRequests.conflate().collect {
            delay(REFRESH_SETTLE_MILLIS)
            workbench.mutations.withLock { refreshActive() }
        }
    }

    /** The folders shown and those of open files follow the state, while iCode is seen. */
    private suspend fun followWatchedFolders() {
        workbench.snapshot.map(::watchedFolders).distinctUntilChanged().collect { folders ->
            if (screenVisible) watcher.watch(physical(folders))
        }
    }

    /**
     * Reopens what was open when iCode last ran — after Android ended the
     * process in the background, the screens come back and so must their
     * workspace and tabs — then keeps it as it changes. Quietly: nothing
     * navigates, recents are not bumped, a file gone from disk is skipped, and
     * a leftover draft still asks its question. Terminals are never reopened.
     */
    private suspend fun restoreThenKeepSession() {
        library.lastSession()?.let { session ->
            workbench.mutations.withLock {
                if (activeRoot != null || !Environment.isExternalStorageManager()) return@withLock
                if (!File(session.workspaceLocation).isDirectory) return@withLock
                open(session.workspaceLocation, persist = false, announce = false)
                if (activeRoot != session.workspaceLocation) return@withLock
                session.documents.filter { source.physical(it).isFile }.forEach { documents.open(it, announce = false) }
                session.activeDocument?.let(documents::select)
            }
        }
        workbench.snapshot
            .map { state ->
                state.activeWorkspaceLocation?.let { root ->
                    StoredSession(root, state.openDocuments.map { it.location }, state.activeDocument?.location)
                }
            }
            .distinctUntilChanged()
            .collect { session -> library.saveSession(session) }
    }

    private suspend fun refreshActive() {
        val root = activeRoot
        if (root == null) {
            library.refreshRecents()
            return
        }
        runCatching {
            tree.reload(root)
            gitWorkspace.refresh(root)
            // Files may have come and gone: Quick Open's index is rebuilt, not kept.
            if (indexStale) {
                indexStale = false
                search.workspaceChanged(root)
            }
            documents.reconcileWithDisk()
        }.onFailure { error ->
            workbench.report("workspace", "Unable to refresh workspace", error)
        }
    }

    private companion object {
        /** Lets requests that arrive together (resume and a panel change) become one refresh. */
        const val REFRESH_SETTLE_MILLIS = 300L
    }
}
