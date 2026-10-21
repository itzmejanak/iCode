package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitWorkspaceCoordinator
import dev.icode.mobile.data.git.withGit
import dev.icode.mobile.data.language.LanguageServiceCoordinator
import dev.icode.mobile.data.runtime.TerminalCoordinator
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.NoticeTone
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
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val source: WorkspaceFileSource,
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
        if (!source.isWorkspace(location)) {
            stateHolder.notify(NoticeTone.HeadsUp, "This workspace no longer exists")
            library.refresh()
            return
        }
        if (location == activeRoot) {
            if (openTerminal) terminal.openIn(location)
            if (announce) {
                stateHolder.emit(
                    if (openTerminal) ICodeEffect.WorkspaceTerminalOpened(location)
                    else ICodeEffect.WorkspaceOpened(location),
                )
            }
            return
        }
        val dirtyCount = stateHolder.current.openDocuments.count { it.dirty }
        if (dirtyCount > 0 && !dirtyChoiceResolved) {
            stateHolder.emit(ICodeEffect.ConfirmWorkspaceSwitch(location, openTerminal, dirtyCount))
            return
        }
        runCatching {
            val root = source.describeRoot(location)
            val children = source.listChildren(location)
            val gitPicture = gitWorkspace.read(root.location)
            val closing = closingDocuments()
            languages.stopAll()
            activeRoot = root.location
            tree.reset(root.location, children)
            if (persist) library.remember(root.location)
            val workspaces = library.workspaces()
            stateHolder.update { snapshot -> snapshot.withGit(gitPicture).copy(
                workspaces = workspaces,
                files = tree.rows(root.location, gitPicture.state),
                localGitAuthorName = null,
                localGitAuthorEmail = null,
                gitIdentityLoaded = false,
                activeWorkspace = root.name,
                activePath = root.displayPath,
                activeWorkspaceLocation = root.location,
                activeDocument = null,
                openDocuments = emptyList(),
                recentDocuments = library.recentDocuments(root.location),
                fileIndex = emptyList(),
                fileIndexTruncated = false,
                contentSearch = ContentSearchState.Empty,
                externalChanges = emptyList(),
            ) }
            leftBehind(closing)
            if (openTerminal) terminal.openIn(root.location)
            if (announce) {
                stateHolder.emit(
                    if (openTerminal) ICodeEffect.WorkspaceTerminalOpened(location)
                    else ICodeEffect.WorkspaceOpened(location),
                )
            }
        }.onFailure { error ->
            library.refresh()
            stateHolder.report("workspace", "Unable to open the workspace", error)
        }
    }

    /**
     * Closes the open workspace, before it is deleted or renamed. Its tabs close — one with unsaved
     * edits was refused before this — its services stop, and iCode shows no workspace.
     */
    suspend fun close() {
        if (activeRoot == null) return
        val closing = closingDocuments()
        languages.stopAll()
        activeRoot = null
        tree.clear()
        val empty = ICodeStateHolder.emptySnapshot()
        stateHolder.update { snapshot -> snapshot.copy(
            files = empty.files,
            gitChanges = empty.gitChanges,
            gitRepository = null,
            gitRepositories = emptyList(),
            gitFailure = null,
            activeWorkspace = empty.activeWorkspace,
            activePath = empty.activePath,
            activeWorkspaceLocation = null,
            activeBranch = empty.activeBranch,
            activeDocument = null,
            openDocuments = emptyList(),
            recentDocuments = emptyList(),
            fileIndex = emptyList(),
            fileIndexTruncated = false,
            contentSearch = ContentSearchState.Empty,
            externalChanges = emptyList(),
        ) }
        leftBehind(closing)
    }

    /** Opens [location] with [tabs] as a restored session does: quietly, a file gone from disk skipped. */
    suspend fun openWithTabs(location: String, tabs: List<String>, active: String?) {
        open(location, persist = true, announce = false)
        if (activeRoot == location) reopenTabs(tabs, active)
    }

    suspend fun resolveSwitch(intent: ICodeIntent.ResolveWorkspaceSwitch) {
        if (intent.saveChanges) {
            val dirty = stateHolder.current.openDocuments.filter { it.dirty }
            for (document in dirty) if (!documents.save(document.location)) return
        }
        open(intent.path, persist = true, openTerminal = intent.openTerminal, dirtyChoiceResolved = true)
    }

    fun requestRefresh(rebuildIndex: Boolean) {
        if (rebuildIndex) indexStale = true
        refreshRequests.tryEmit(Unit)
    }

    /** The watcher runs only while iCode is seen; out of sight, the drafts waiting to be written are written. */
    fun screenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) return watcher.watch(watchedFolders(stateHolder.current, source.workspacesRoot))
        watcher.stop()
        recovery.flush()
    }

    fun stopWatching() = watcher.stop()

    /**
     * Opens a draft's file for the recovery question, switching to its
     * workspace first when it belongs to another one the user has opened.
     */
    suspend fun openRecoverableDraft(location: String) {
        val root = activeRoot
        if (root == null || !location.isAtOrUnder(root)) {
            val home = library.workspaceHolding(location) ?: run {
                stateHolder.notify(NoticeTone.HeadsUp, "${File(location).name} is outside iCode's workspaces: import its folder, or discard the draft")
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
        val change = stateHolder.current.externalChanges.firstOrNull { it.location == location } ?: return
        val document = stateHolder.current.openDocuments.firstOrNull { it.location == location } ?: return
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
            stateHolder.mutations.withLock { refreshActive() }
        }
    }

    /** The folders shown and those of open files follow the state, while iCode is seen. */
    private suspend fun followWatchedFolders() {
        stateHolder.snapshot.map { watchedFolders(it, source.workspacesRoot) }.distinctUntilChanged().collect { folders ->
            if (screenVisible) watcher.watch(folders)
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
            stateHolder.mutations.withLock {
                if (activeRoot != null || !source.isWorkspace(session.workspaceLocation)) return@withLock
                open(session.workspaceLocation, persist = false, announce = false)
                if (activeRoot != session.workspaceLocation) return@withLock
                reopenTabs(session.documents, session.activeDocument)
            }
        }
        stateHolder.snapshot
            .map { state ->
                state.activeWorkspaceLocation?.let { root ->
                    StoredSession(root, state.openDocuments.map { it.location }, state.activeDocument?.location)
                }
            }
            .distinctUntilChanged()
            .collect { session -> library.saveSession(session) }
    }

    private suspend fun reopenTabs(tabs: List<String>, active: String?) {
        tabs.filter { File(it).isFile }.forEach { documents.open(it, announce = false) }
        active?.let(documents::select)
    }

    /** What a workspace being left keeps as drafts: unsaved edits, and drafts not yet answered. */
    private fun closingDocuments() = stateHolder.current.openDocuments
        .filter { it.dirty || !recovery.isUnresolved(it.location) }
        .map { it.location }

    private suspend fun leftBehind(closing: List<String>) {
        documents.clear()
        recovery.workspaceClosing(closing)
        search.workspaceClosing()
        gitWorkspace.workspaceClosing()
    }

    private suspend fun refreshActive() {
        // A workspace made or removed from the terminal appears or goes, whatever is open.
        library.refresh()
        val root = activeRoot ?: return
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
            stateHolder.report("workspace", "Unable to refresh workspace", error)
        }
    }

    private companion object {
        /** Lets requests that arrive together (resume and a panel change) become one refresh. */
        const val REFRESH_SETTLE_MILLIS = 300L
    }
}
