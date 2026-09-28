package dev.icode.mobile.data.workspace

import android.content.Context
import android.os.Environment
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.git.GitHeadReader
import dev.icode.mobile.data.git.GitAuthenticator
import dev.icode.mobile.data.git.GitConflictParser
import dev.icode.mobile.data.git.GitCredentialStore
import dev.icode.mobile.data.git.GitProgressParser
import dev.icode.mobile.data.git.GitOperationOutcome
import dev.icode.mobile.data.git.GitRepositoryClient
import dev.icode.mobile.data.git.GitWorkspaceSnapshot
import dev.icode.mobile.data.language.LanguageServiceProcessManager
import dev.icode.mobile.data.language.LanguageServiceStore
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.search.RipgrepService
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.runtime.RuntimeCoordinator
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.TerminalSessionManager
import com.termux.terminal.TerminalSession
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.SystemPermission
import java.io.IOException
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

class AndroidWorkbenchGateway(context: Context) : WorkbenchGateway {
    private val workbench = WorkbenchStateHolder()
    private val appContext = context.applicationContext
    private val source = DirectFileWorkspaceSource()
    private val store: WorkspaceStore = SharedPreferencesWorkspaceStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeLayout = RuntimeLayout(appContext)
    private val gitCli = CliGitService(runtimeLayout)
    private val git = GitRepositoryClient(gitCli)
    private val credentialStore = GitCredentialStore(appContext)
    private val authenticator = GitAuthenticator(runtimeLayout, gitCli, credentialStore)
    /** Shared with every screen this process shows; see [close]. */
    private val terminals = TerminalSessionManager.get(appContext)
    private val languageStore = LanguageServiceStore(appContext)
    private val notices = NoticeStore.get(appContext)
    private val loadedLanguageConfigs = runCatching { languageStore.load() }
    private var languageConfigs = loadedLanguageConfigs.getOrDefault(emptyList())
    private val languageProcesses = LanguageServiceProcessManager(runtimeLayout, scope) { id, status, pid, message ->
        workbench.update { state -> state.copy(languageServices = state.languageServices.map { service ->
            if (service.id == id) service.copy(
                running = status == LanguageServiceStatus.Running,
                status = status,
                processId = pid,
                statusMessage = message,
            ) else service
        }) }
    }
    private val terminalPreferences = AndroidWorkspacePreferencesRepository(appContext)
    private val runtime: RuntimeCoordinator = RuntimeCoordinator(
        appContext,
        workbench,
        scope,
        runtimeLayout,
        AndroidSystemAccess(appContext),
        sessionsRunning = { terminals.hasRunningSessions() || languageProcesses.hasActiveProcesses() },
        onStorageAccessChanged = { library.refreshRecents() },
    )
    private var activeRootLocation: String? = null
    private var gitRefreshJob: Job? = null

    private val library: WorkspaceLibraryCoordinator = WorkspaceLibraryCoordinator(
        appContext,
        workbench,
        scope,
        source,
        store,
        runtimeLayout,
        tasksRunning = {
            terminals.hasRunningSessions() || languageProcesses.hasActiveProcesses() || runtime.installing ||
                workbench.current.gitOperation is GitOperationState.Running
        },
    )
    private val folders: FolderBrowserCoordinator = FolderBrowserCoordinator(workbench, source) { location -> openWorkspace(location, persist = true) }

    private val tree = WorkspaceTree(workbench, source)
    private val search = SearchCoordinator(workbench, scope, RipgrepService(runtimeLayout, scope)) { activeRootLocation }
    private val recovery: DocumentRecoveryCoordinator = DocumentRecoveryCoordinator(
        workbench,
        scope,
        DocumentDraftStore(File(appContext.filesDir, "document-drafts")),
    ) { location -> documents.sourceOf(location) }
    private val documents: DocumentSessionCoordinator = DocumentSessionCoordinator(
        workbench,
        source,
        recovery,
        library,
        activeRoot = { activeRootLocation },
        onSaved = { scheduleGitRefresh() },
    )
    private val items = WorkspaceItemsCoordinator(
        workbench,
        source,
        tree,
        documents,
        library,
        activeRoot = { activeRootLocation },
        refreshWorkspaceState = { root -> refreshWorkspaceState(root) },
        reindex = { root -> search.workspaceChanged(root) },
    )

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Quick Open's index is rebuilt only when entries may have come or gone, not after every save. */
    @Volatile private var indexStale = false
    @Volatile private var screenVisible = false
    private val watcher = WorkspaceWatcher(scope) { structure -> requestRefresh(rebuildIndex = structure) }

    override val snapshot: StateFlow<WorkbenchSnapshot> = workbench.snapshot
    override val effects: SharedFlow<WorkbenchEffect> = workbench.effects

    init {
        workbench.update { it.copy(
            languageServices = languageConfigs.map(::languageServiceView),
            languageServicesLoadError = loadedLanguageConfigs.exceptionOrNull()?.let { workbench.failureText("language", "Unable to load saved services", it) },
        ) }
        loadedLanguageConfigs.exceptionOrNull()?.let { error ->
            scope.launch { workbench.report("language", "Unable to load language services", error) }
        }
        scope.launch { library.refreshRecents() }
        scope.launch { recovery.loadRecoverable() }
        scope.launch { observeTerminals() }
        scope.launch { refreshOnRequest() }
        scope.launch { refreshAfterCommands() }
        scope.launch { followWatchedFolders() }
        scope.launch { restoreThenKeepSession() }
        scope.launch { observeNotices() }
        scope.launch { reportTerminalEndings() }
        scope.launch {
            terminalPreferences.values(null).collect { preferences ->
                if (preferences.loaded && preferences.loadError == null) {
                    workbench.onMain { terminals.applySettings(preferences.terminal) }
                }
            }
        }
        scope.launch { runtime.reportState() }
    }

    /** The live session behind a terminal tab, for the view that draws it. Main thread. */
    fun terminalSession(id: String): TerminalSession? = terminals.session(id)

    /** Reports screen changes of a terminal session until the handle is closed. Main thread. */
    fun observeTerminal(id: String, onScreenChanged: () -> Unit): AutoCloseable = terminals.observe(id, onScreenChanged)

    /**
     * A tab closes when its shell exits. A clean exit needs no comment; any
     * other code is said once, so a shell that failed on its own is not silent.
     */
    private suspend fun reportTerminalEndings() {
        terminals.endings.collect { ending ->
            if (ending.exitCode != 0) {
                workbench.message("${ending.title} exited with code ${ending.exitCode}")
            }
        }
    }

    /** The Notifications page; first, once per process, what the previous process's end took with it. */
    private suspend fun observeNotices() {
        terminals.ledger.takePrevious()?.let(notices::add)
        notices.notices.collect { list ->
            workbench.update { it.copy(notices = list) }
        }
    }

    private suspend fun observeTerminals() {
        terminals.snapshot.collect { terminal ->
            workbench.update { it.copy(terminal = terminal) }
        }
    }

    override fun dispatch(intent: WorkbenchIntent) {
        if (intent is WorkbenchIntent.SelectLanguageService) {
            workbench.update { state ->
                state.copy(selectedLanguageServiceId = intent.id.takeIf { id ->
                    state.languageServices.any { service -> service.id == id }
                })
            }
            return
        }
        if (intent == WorkbenchIntent.Refresh) {
            requestRefresh(rebuildIndex = true)
            return
        }
        if (intent is WorkbenchIntent.ScreenVisible) {
            screenVisible = intent.visible
            if (intent.visible) watcher.watch(watchedFolders(workbench.current)) else watcher.stop()
            return
        }
        if (intent == WorkbenchIntent.RefreshStorage) {
            library.refreshStorage()
            return
        }
        // Reading the runtime's own files changes nothing, so it does not wait
        // on the mutation lock behind whatever the workspace is doing.
        if (intent == WorkbenchIntent.RefreshRuntimeDetails) {
            runtime.refreshDetails()
            return
        }
        if (intent is WorkbenchIntent.UpdateDocumentText) {
            documents.updateText(intent)
            return
        }
        // Folder reads and expansion state belong to WorkspaceTree; they do
        // not wait behind an unrelated Git refresh on the mutation lock.
        if (intent is WorkbenchIntent.ToggleFolder) {
            val root = activeRootLocation ?: return
            scope.launch { tree.toggle(root, intent.path) }
            return
        }
        // Search owns cancellable jobs. It must never wait behind a workspace
        // mutation, and a new query must be able to cancel the old process.
        if (intent == WorkbenchIntent.PrepareWorkspaceSearch) {
            activeRootLocation?.let(search::prepareIndex)
            return
        }
        if (intent is WorkbenchIntent.RunContentSearch) {
            search.runContentSearch(intent.query, intent.matchCase, intent.wholeWord, intent.regex)
            return
        }
        if (intent == WorkbenchIntent.ClearContentSearch) {
            search.clear()
            return
        }
        scope.launch {
            workbench.mutations.withLock {
                when (intent) {
                    is WorkbenchIntent.RequestSystemPermission ->
                        workbench.emit(WorkbenchEffect.RequestSystemPermission(intent.permission))
                    is WorkbenchIntent.SystemPermissionAnswered -> systemPermissionAnswered(intent.permission)
                    is WorkbenchIntent.DeclineSystemPermission -> runtime.declinePermission(intent.permission)
                    is WorkbenchIntent.OpenSystemSettings ->
                        workbench.emit(WorkbenchEffect.OpenSystemSettings(intent.permission))
                    WorkbenchIntent.RefreshSystemAccess -> runtime.refreshSystemAccess()
                    WorkbenchIntent.MarkNoticesRead -> notices.markAllRead()
                    is WorkbenchIntent.DeleteNotice -> notices.delete(intent.id)
                    WorkbenchIntent.ClearNotices -> notices.clear()
                    is WorkbenchIntent.StartFolderBrowser -> folders.start(intent.purpose)
                    is WorkbenchIntent.ShowWorkspaceLocation -> folders.showLocation(intent.path)
                    is WorkbenchIntent.BrowseFolder -> folders.browse(intent.path)
                    WorkbenchIntent.BrowseParentFolder -> folders.browseParent()
                    WorkbenchIntent.ConfirmFolderSelection -> folders.confirmSelection()
                    is WorkbenchIntent.RequestCreateFolder -> folders.requestCreateFolder(intent.name)
                    is WorkbenchIntent.OpenWorkspace -> openWorkspace(intent.path, persist = true)
                    is WorkbenchIntent.OpenWorkspaceTerminal -> openWorkspace(intent.path, persist = true, openTerminal = true)
                    is WorkbenchIntent.ResolveWorkspaceSwitch -> resolveWorkspaceSwitch(intent)
                    is WorkbenchIntent.RemoveRecentWorkspace -> library.removeRecent(intent.path)
                    is WorkbenchIntent.OpenFile ->
                        documents.open(intent.path, intent.lineNumber, intent.column)
                    is WorkbenchIntent.CreateFile -> items.createFile(intent)
                    is WorkbenchIntent.CreateWorkspaceFolder -> items.createFolder(intent)
                    is WorkbenchIntent.RenameWorkspaceItem -> items.rename(intent)
                    is WorkbenchIntent.MoveWorkspaceItem -> items.move(intent)
                    is WorkbenchIntent.CopyWorkspaceItem -> items.copy(intent)
                    is WorkbenchIntent.DeleteWorkspaceItem -> items.delete(intent)
                    is WorkbenchIntent.SaveDocument -> documents.save(
                        intent.location,
                        intent.automatic,
                        intent.expectedText,
                        intent.expectedWorkspace,
                    )
                    is WorkbenchIntent.RestoreDraft -> recovery.restore(intent.location)
                    is WorkbenchIntent.OpenRecoverableDraft -> openRecoverableDraft(intent.location)
                    is WorkbenchIntent.DiscardDraft -> recovery.keepDiskVersion(intent.location)
                    is WorkbenchIntent.ConfirmRecoveredOverwrite -> documents.save(
                        intent.location,
                        allowRecoveredOverwrite = true,
                    )
                    is WorkbenchIntent.ReloadFromDisk -> documents.reloadFromDisk(intent.location)
                    is WorkbenchIntent.OverwriteDisk -> documents.overwriteDisk(intent.location)
                    is WorkbenchIntent.CompareWithDisk -> compareWithDisk(intent.location)
                    is WorkbenchIntent.SelectDocument -> documents.select(intent.location)
                    is WorkbenchIntent.ReorderDocument -> documents.reorder(intent.movingLocation, intent.targetLocation)
                    is WorkbenchIntent.CloseDocument -> documents.close(intent.location, intent.saveChanges)
                    is WorkbenchIntent.CloseOtherDocuments -> documents.closeOthers(intent.keepLocation, intent.saveChanges)
                    WorkbenchIntent.CloseSavedDocuments -> documents.closeSaved()
                    WorkbenchIntent.InitializeGit -> initializeGit()
                    is WorkbenchIntent.StageFile -> stageFile(intent.path)
                    is WorkbenchIntent.UnstageFile -> unstageFile(intent.path)
                    WorkbenchIntent.StageAllFiles -> stageAllFiles()
                    WorkbenchIntent.UnstageAllFiles -> unstageAllFiles()
                    is WorkbenchIntent.CommitStaged -> commitStaged(intent.message)
                    is WorkbenchIntent.ConfigureGitIdentity -> configureGitIdentity(intent.name, intent.email)
                    is WorkbenchIntent.ConfigureGlobalGitIdentity -> configureGlobalGitIdentity(intent.name, intent.email)
                    WorkbenchIntent.ClearRepositoryGitIdentity -> clearRepositoryGitIdentity()
                    is WorkbenchIntent.SwitchBranch -> switchBranch(intent.branch)
                    is WorkbenchIntent.CreateBranch -> createBranch(intent.branch)
                    is WorkbenchIntent.DeleteBranch -> deleteBranch(intent.branch)
                    WorkbenchIntent.ProvisionGit -> provisionGit()
                    WorkbenchIntent.RefreshGit -> activeRootLocation?.let { refreshWorkspaceState(it) }
                    is WorkbenchIntent.OpenDiff -> openDiff(intent.path, intent.staged)
                    WorkbenchIntent.CloseDiff -> workbench.update { it.copy(activeDiff = null) }
                    is WorkbenchIntent.RequestDiscardChanges -> workbench.update { state ->
                        state.copy(pendingDiscard = state.gitChanges.firstOrNull { it.path == intent.path })
                    }
                    WorkbenchIntent.CancelDiscardChanges -> workbench.update { it.copy(pendingDiscard = null) }
                    is WorkbenchIntent.DiscardChanges -> discardChanges(intent.path, intent.status)
                    WorkbenchIntent.AbortInProgress -> abortInProgress()
                    WorkbenchIntent.FetchRemote -> runRemote("Fetching") { workspace, onStderr ->
                        git.fetch(workspace, onStderr)
                    }
                    WorkbenchIntent.PullRemote -> runRemote("Pulling") { workspace, onStderr ->
                        git.pull(workspace, onStderr)
                    }
                    WorkbenchIntent.PushRemote -> pushRemote()
                    is WorkbenchIntent.OpenConflict -> openConflict(intent.path)
                    WorkbenchIntent.CloseConflict -> workbench.update { it.copy(activeConflict = null) }
                    is WorkbenchIntent.ResolveConflict -> resolveConflict(intent.path, intent.choice)
                    is WorkbenchIntent.MarkConflictResolved -> runGitAction(
                        operation = { git.markResolved(it, intent.path) },
                    )
                    is WorkbenchIntent.UpdateCloneForm -> workbench.update {
                it.copy(cloneUrl = intent.url, cloneBranch = intent.branch)
            }
            is WorkbenchIntent.CloneRepository -> cloneRepository(intent)
                    is WorkbenchIntent.SelectGitProvider -> workbench.update {
                it.copy(gitAuth = it.gitAuth.copy(provider = intent.provider))
            }
            is WorkbenchIntent.SelectGitAuthMethod -> workbench.update {
                        it.copy(gitAuth = it.gitAuth.copy(method = intent.method))
                    }
                    is WorkbenchIntent.SaveGitToken -> saveGitToken(intent)
                    WorkbenchIntent.ClearGitToken -> {
                        authenticator.clearHttpsCredentials()
                        refreshGitAuth()
                    }
                    WorkbenchIntent.GenerateSshKey -> generateSshKey()
                    is WorkbenchIntent.TestGitConnection -> testGitConnection(intent.url)
                    WorkbenchIntent.RefreshGitAuth -> refreshGitAuth()
                    is WorkbenchIntent.SaveLanguageService -> saveLanguageService(intent)
                    is WorkbenchIntent.RemoveLanguageService -> removeLanguageService(intent.id)
                    is WorkbenchIntent.StartLanguageService -> startLanguageService(intent.id)
                    is WorkbenchIntent.StopLanguageService -> languageProcesses.stop(intent.id)
                    is WorkbenchIntent.RestartLanguageService -> restartLanguageService(intent.id)
                    WorkbenchIntent.InstallRuntime -> runtime.install(RuntimeInstallReason.Initial)
                    WorkbenchIntent.ClearAppCache -> library.clearCache()
                    WorkbenchIntent.RemoveStaleRecents -> library.removeStale()
                    // Repair keeps the home directory, reset does not; both go
                    // through the one installer rather than a second path.
                    WorkbenchIntent.RepairRuntime -> {
                        if (runtime.install(RuntimeInstallReason.Repair)) {
                            workbench.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        }
                    }
                    WorkbenchIntent.ResetRuntime -> {
                        if (runtime.install(RuntimeInstallReason.Reset)) {
                            workbench.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        }
                    }
                    WorkbenchIntent.OpenTerminalSession -> openTerminalSession()
                    is WorkbenchIntent.SelectTerminalSession -> workbench.onMain { terminals.select(intent.id) }
                    is WorkbenchIntent.ShowTerminalSession -> showTerminalSession(intent.id)
                    is WorkbenchIntent.ReorderTerminalSession -> workbench.onMain { terminals.reorder(intent.movingId, intent.targetId) }
                    is WorkbenchIntent.CloseTerminalSession -> workbench.onMain { terminals.close(intent.id) }
                    is WorkbenchIntent.OpenExternalUrl -> workbench.emit(WorkbenchEffect.OpenExternalUrl(intent.url))
                }
            }
        }
    }

    /**
     * Android's prompt closed. Whatever the answer, onboarding has now offered
     * the permission; a folder browser that was waiting for storage opens.
     */
    private suspend fun systemPermissionAnswered(permission: SystemPermission) {
        val granted = runtime.permissionAnswered(permission)
        if (permission == SystemPermission.Storage && folders.storageAnswered(granted)) return
        workbench.emit(WorkbenchEffect.SystemPermissionResolved(permission, granted))
    }

    /**
     * Selects the session, then opens its workspace on the Terminal panel. The
     * workspace path reuses the running session the selection now names, and
     * goes through the same dirty-document confirmation as any switch.
     */
    private suspend fun showTerminalSession(id: String) {
        val workspace = workbench.onMain {
            if (terminals.snapshot.value.sessions.none { it.id == id && it.isRunning }) return@onMain null
            terminals.select(id)
            terminals.workspaceOf(id) ?: ""
        }
        when (workspace) {
            null -> workbench.message("That terminal session has ended")
            "" -> workbench.emit(WorkbenchEffect.WorkspaceTerminalOpened(activeRootLocation.orEmpty()))
            else -> openWorkspace(workspace, persist = true, openTerminal = true)
        }
    }

    private suspend fun openTerminalSession() {
        if (!runtimeLayout.isInstalled) {
            workbench.message("Install the Linux runtime before opening a terminal")
            return
        }
        if (terminals.open(activeRootLocation) == null) {
            workbench.message("Unable to start a terminal session")
        }
    }

    /**
     * The screen is going away. Terminal sessions are the user's and outlive
     * it: they end only when the user ends them, and the next screen adopts
     * them. RuntimeForegroundService keeps them alive meanwhile, and stops by
     * itself when none runs.
     */
    override fun close() {
        watcher.stop()
        languageProcesses.stopAll()
        scope.cancel()
    }

    private fun languageServiceView(config: LanguageServiceConfiguration): LanguageService = LanguageService(
        initials = config.name.split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("").ifBlank { "LS" },
        name = config.name,
        patterns = config.patterns.ifBlank { "All files" },
        running = false,
        id = config.id,
        command = config.command,
        rootMarkers = config.rootMarkers,
        initializationOptions = config.initializationOptions,
    )

    private suspend fun saveLanguageService(intent: WorkbenchIntent.SaveLanguageService) {
        if (workbench.current.languageServicesLoadError != null) {
            workbench.message("Saved services could not be read; configuration was not overwritten")
            return
        }
        val name = intent.name.trim()
        val command = intent.command.trim()
        val options = intent.initializationOptions.trim().ifBlank { "{}" }
        val error = LanguageServiceRules.validate(name, command, intent.patterns, options)
            ?: runCatching { LanguageServiceRules.commandArguments(command); JSONObject(options) }
                .exceptionOrNull()?.let { "Check command quoting and enter a JSON object for initialization options" }
        if (error != null) {
            workbench.message(error)
            return
        }
        val existingId = intent.id
        if (existingId != null && languageConfigs.none { it.id == existingId }) {
            workbench.message("This language service no longer exists")
            return
        }
        if (existingId != null && languageProcesses.isActive(existingId)) {
            workbench.message("Stop this server before editing its configuration")
            return
        }
        if (existingId == null && languageConfigs.size >= LanguageServiceRules.MAX_SERVICES) {
            workbench.message("Maximum ${LanguageServiceRules.MAX_SERVICES} language services")
            return
        }
        val id = existingId ?: UUID.randomUUID().toString()
        val config = LanguageServiceConfiguration(
            id, name, command, intent.patterns.trim(), intent.rootMarkers.trim(), options,
        )
        val updated = if (existingId == null) languageConfigs + config else languageConfigs.map {
            if (it.id == existingId) config else it
        }
        try {
            languageStore.save(updated)
            languageConfigs = updated
            workbench.update { state -> state.copy(
                languageServices = updated.map(::languageServiceView),
                selectedLanguageServiceId = id,
            ) }
        } catch (failure: Exception) {
            workbench.report("language", "Unable to save language service", failure)
        }
    }

    private suspend fun removeLanguageService(id: String) {
        if (workbench.current.languageServicesLoadError != null) return
        if (languageConfigs.none { it.id == id }) return
        val updated = languageConfigs.filterNot { it.id == id }
        try {
            languageStore.save(updated)
            languageConfigs = updated
            languageProcesses.stop(id)
            workbench.update { state -> state.copy(
                languageServices = state.languageServices.filterNot { it.id == id },
                selectedLanguageServiceId = null,
            ) }
        } catch (failure: Exception) {
            workbench.report("language", "Unable to remove language service", failure)
        }
    }

    private suspend fun startLanguageService(id: String) {
        val config = languageConfigs.firstOrNull { it.id == id } ?: return
        val workspace = activeRootLocation
        if (workspace == null) {
            workbench.message("Open a workspace before starting a language service")
            return
        }
        languageProcesses.start(config, workspace)?.let { error ->
            workbench.message(error)
        }
    }

    private fun restartLanguageService(id: String) {
        languageProcesses.stop(id)
        scope.launch {
            repeat(30) {
                if (!languageProcesses.isActive(id)) {
                    workbench.mutations.withLock { startLanguageService(id) }
                    return@launch
                }
                delay(100)
            }
            workbench.message("Server did not stop; retry after it exits")
        }
    }

    private suspend fun openWorkspace(
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
        if (location == activeRootLocation) {
            if (openTerminal && runtimeLayout.isInstalled) {
                runCatching { terminals.open(location, reuseExisting = true) }
                    .onSuccess { workbench.update { it.copy(terminal = terminals.snapshot.value) } }
                    .onFailure { error ->
                        workbench.report("terminal", "Unable to start terminal", error)
                    }
            }
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
            val children = source.listChildren(location)
            val gitAvailable = git.isAvailable()
            val gitState = if (gitAvailable) {
                git.inspect(root.location)
            } else {
                GitWorkspaceSnapshot(repository = null, changes = emptyList())
            }
            val closingDocuments = workbench.current.openDocuments
                .filter { it.dirty || !recovery.isUnresolved(it.location) }
                .map { it.location }
            languageProcesses.stopAll()
            activeRootLocation = root.location
            tree.reset(root.location, children)
            if (persist) library.remember(root)
            val recents = library.recentWorkspaces()
            workbench.update { snapshot -> snapshot.copy(
                workspaces = recents,
                files = tree.rows(root.location, gitState),
                gitChanges = gitState.changes,
                gitRepository = gitState.repository,
                localGitAuthorName = null,
                localGitAuthorEmail = null,
                gitIdentityLoaded = false,
                activeWorkspace = root.name,
                activePath = root.displayPath,
                activeWorkspaceLocation = root.location,
                gitAvailable = gitAvailable,
                activeBranch = gitState.repository?.branch
                    ?: GitHeadReader.branchOf(root.location).orEmpty(),
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
            if (openTerminal && runtimeLayout.isInstalled) {
                runCatching { terminals.open(root.location, reuseExisting = true) }
                    .onSuccess { workbench.update { it.copy(terminal = terminals.snapshot.value) } }
                    .onFailure { error ->
                        workbench.report("terminal", "Unable to start terminal", error)
                    }
            }
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

    private suspend fun resolveWorkspaceSwitch(intent: WorkbenchIntent.ResolveWorkspaceSwitch) {
        if (intent.saveChanges) {
            val dirty = workbench.current.openDocuments.filter { it.dirty }
            for (document in dirty) if (!documents.save(document.location)) return
        }
        openWorkspace(intent.path, persist = true, openTerminal = intent.openTerminal, dirtyChoiceResolved = true)
    }

    private suspend fun initializeGit() {
        val workspace = activeRootLocation
        if (workspace == null) {
            workbench.message("Open a workspace before initializing Git")
            return
        }
        runCatching {
            git.initialize(workspace)
            refreshWorkspaceState(workspace)
            workbench.emit(WorkbenchEffect.GitInitialized)
        }.onFailure { error ->
            workbench.report("git", "Unable to initialize Git", error)
        }
    }

    private suspend fun stageFile(path: String) = runGitAction(operation = { workspace -> git.stage(workspace, path) })

    private suspend fun unstageFile(path: String) = runGitAction(operation = { workspace -> git.unstage(workspace, path) })

    private suspend fun stageAllFiles() = runGitAction(operation = { workspace -> git.stageAll(workspace) })

    private suspend fun unstageAllFiles() = runGitAction(operation = { workspace -> git.unstageAll(workspace) })

    private suspend fun commitStaged(message: String) = runGitAction(
        operation = { workspace -> git.commit(workspace, message) },
        onSuccess = { workbench.emit(WorkbenchEffect.GitCommitted) },
    )

    private suspend fun configureGitIdentity(name: String, email: String) = runGitAction(
        operation = { workspace -> git.configureIdentity(workspace, name, email) },
        onSuccess = { refreshGitAuth() },
    )

    private suspend fun configureGlobalGitIdentity(name: String, email: String) {
        if (name.isBlank() || !email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            workbench.message("Enter a name and valid Git author email")
            return
        }
        if (!git.isAvailable()) {
            workbench.message("Install Git in the runtime first")
            return
        }
        runCatching {
            listOf(
                listOf("config", "--global", "user.name", name.trim()),
                listOf("config", "--global", "user.email", email.trim()),
            ).forEach { arguments ->
                val result = gitCli.run(null, arguments)
                if (!result.succeeded) throw IOException(result.errorMessage)
            }
            activeRootLocation?.let { refreshWorkspaceState(it) }
            refreshGitAuth()
        }.onFailure { error ->
            workbench.report("git", "Could not save Git identity", error)
        }
    }

    private suspend fun clearRepositoryGitIdentity() {
        val workspace = activeRootLocation ?: return
        runCatching {
            listOf("user.name", "user.email").forEach { key ->
                val current = gitCli.run(workspace, listOf("config", "--local", "--get", key))
                if (current.succeeded) {
                    val result = gitCli.run(workspace, listOf("config", "--local", "--unset-all", key))
                    if (!result.succeeded) throw IOException(result.errorMessage)
                }
            }
            refreshWorkspaceState(workspace)
            refreshGitAuth()
        }.onFailure { error ->
            workbench.report("git", "Could not reset repository identity", error)
        }
    }

    private suspend fun switchBranch(branch: String) = runGitAction(
        operation = { workspace -> git.switchBranch(workspace, branch) },
    )

    private suspend fun createBranch(branch: String) = runGitAction(
        operation = { workspace -> git.createBranch(workspace, branch) },
    )

    /** Git is absent from a fresh runtime; this installs it and reports honestly. */
    private suspend fun provisionGit() {
        if (git.isAvailable()) {
            workbench.update { it.copy(gitAvailable = true) }
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running("Installing Git")) }
        val installed = runCatching { CliGitService(runtimeLayout).provision() }.getOrNull()
        val available = git.isAvailable()
        workbench.update {
            it.copy(
                gitAvailable = available,
                gitOperation = if (available) {
                    GitOperationState.Succeeded("Git installed")
                } else {
                    GitOperationState.Failed("Installing Git", (installed as? GitOutcome.Failure)?.message ?: "Git could not be installed")
                },
            )
        }
        if (available) refreshGitAuth()
        if (available) activeRootLocation?.let { refreshWorkspaceState(it) }
    }

    private suspend fun openDiff(path: String, staged: Boolean) {
        val workspace = activeRootLocation ?: return
        runCatching { git.diff(workspace, path, staged) }
            .onSuccess { diff -> workbench.update { it.copy(activeDiff = diff) } }
            .onFailure { error ->
                workbench.update { it.copy(activeDiff = null) }
                workbench.report("git", "Unable to read the diff", error)
            }
    }

    /**
     * Git reports change paths relative to the repository root, which is not
     * the opened folder when a subdirectory of a repository is opened.
     */
    private fun conflictRoot(): String? =
        workbench.current.gitRepository?.workTreePath ?: activeRootLocation

    /** Loads the conflicted file straight from the worktree, markers and all. */
    private suspend fun openConflict(path: String) {
        val file = File(conflictRoot() ?: return, path)
        runCatching { file.readText() }
            .onSuccess { text ->
                val parsed = GitConflictParser.parse(path, text)
                workbench.update { it.copy(activeConflict = parsed) }
            }
            .onFailure { error ->
                workbench.update { it.copy(activeConflict = null) }
                workbench.report("git", "Unable to read the conflicted file", error)
            }
    }

    /**
     * Writes the resolved text back and refreshes.
     *
     * The file is rebuilt from the parsed sections rather than through
     * `checkout --ours/--theirs`, because "accept both" has no Git equivalent
     * and a single path keeps all three choices consistent with what the user
     * is looking at. Marking resolved stays a separate explicit step.
     */
    private suspend fun resolveConflict(path: String, choice: GitConflictChoice) {
        val root = conflictRoot() ?: return
        val conflict = workbench.current.activeConflict
            ?: run {
                openConflict(path)
                workbench.current.activeConflict
            }
            ?: return
        val resolved = GitConflictParser.resolve(conflict, choice)
        runCatching { File(root, path).writeText(resolved) }
            .onSuccess {
                openConflict(path)
                activeRootLocation?.let { refreshWorkspaceState(it) }
            }
            .onFailure { error ->
                workbench.report("git", "Unable to write the resolved file", error)
            }
    }

    private suspend fun discardChanges(path: String, status: String) {
        runGitAction(operation = { workspace -> git.discard(workspace, path, status) })
        workbench.update { it.copy(pendingDiscard = null) }
    }

    private suspend fun abortInProgress() {
        val inProgress = workbench.current.gitRepository?.inProgress ?: return
        runGitAction(operation = { workspace -> git.abortInProgress(workspace, inProgress) })
    }

    private suspend fun deleteBranch(branch: String) = runGitAction(
        operation = { workspace -> git.deleteBranch(workspace, branch) },
    )

    /**
     * Runs a network operation, reporting only what Git actually returned.
     *
     * The UI never claims success while offline: failure carries Git's message.
     */
    private suspend fun runRemote(
        label: String,
        operation: suspend (String, (String) -> Unit) -> Unit,
    ) {
        val workspace = activeRootLocation
        if (workspace == null) {
            workbench.message("Open a workspace first")
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        // Git reads the credential helper itself; this just makes sure the
        // helper points at the token the user most recently saved.
        authenticator.applyHttpsCredentials()
        var transcript = emptyList<String>()
        runCatching {
            operation(workspace) { stderr ->
                transcript = GitProgressParser.outputLines(stderr)
                publishProgress(label, stderr, transcript)
            }
        }
            .onSuccess {
                workbench.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                refreshWorkspaceState(workspace)
                // The progress surface closes itself, so the outcome is
                // reported here or it is never reported at all.
                workbench.message(completionMessage(label, transcript))
            }
            .onFailure { error ->
                workbench.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            workbench.failureText("git", "$label failed", error),
                            transcript,
                        ),
                    )
                }
            }
    }

    /**
     * Publishes whatever Git has printed so far.
     *
     * Only applied while the operation is still running, so a late poll cannot
     * push a finished operation back into a running state.
     */
    private fun publishProgress(label: String, stderr: String, transcript: List<String>) {
        workbench.update { state ->
            val running = state.gitOperation as? GitOperationState.Running ?: return@update state
            if (running.label != label) return@update state
            state.copy(
                gitOperation = running.copy(
                    progress = GitProgressParser.latest(stderr) ?: running.progress,
                    output = transcript,
                ),
            )
        }
    }

    /** Prefers Git's own last word, such as "Already up to date." */
    private fun completionMessage(label: String, transcript: List<String>): String {
        val summary = transcript.lastOrNull { line ->
            line.startsWith("Already up to date") || line.startsWith("Everything up-to-date")
        }
        return summary ?: "$label complete"
    }

    private suspend fun pushRemote() {
        val repository = workbench.current.gitRepository
        val needsUpstream = repository?.upstream == null
        runRemote("Pushing") { workspace, onStderr ->
            git.push(
                workspace,
                setUpstream = needsUpstream,
                branch = repository?.branch.orEmpty(),
                onStderr = onStderr,
            )
        }
    }

    private suspend fun cloneRepository(intent: WorkbenchIntent.CloneRepository) {
        val label = "Cloning"
        // A new install has no Git until something needs it, and a clone is
        // often the first thing that does: install it as part of this clone.
        if (!git.isAvailable()) {
            val installing = listOf("Installing Git…")
            workbench.update { it.copy(gitOperation = GitOperationState.Running(label, output = installing)) }
            val installed = runCatching { gitCli.provision() }.getOrNull()
            if (!git.isAvailable()) {
                val reason = (installed as? GitOutcome.Failure)?.message ?: "Git could not be installed"
                BackendLog.w("git", "Git install before clone failed: $reason")
                workbench.update { it.copy(gitOperation = GitOperationState.Failed(label, reason, installing)) }
                return
            }
            workbench.update { it.copy(gitAvailable = true) }
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        var transcript = emptyList<String>()
        runCatching {
            git.clone(intent.url, intent.destination, intent.directoryName) { stderr ->
                transcript = GitProgressParser.outputLines(stderr)
                publishProgress(label, stderr, transcript)
            }
        }
            .onSuccess {
                workbench.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                openWorkspace(File(intent.destination, intent.directoryName).path, persist = true)
            }
            .onFailure { error ->
                workbench.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            workbench.failureText("git", "Clone failed", error),
                            transcript,
                        ),
                    )
                }
            }
    }

    private suspend fun saveGitToken(intent: WorkbenchIntent.SaveGitToken) {
        runCatching {
            credentialStore.saveHttps(
                GitCredentialStore.HttpsCredential(intent.host, intent.username, intent.token),
            )
            authenticator.applyHttpsCredentials()
        }.onFailure { error ->
            workbench.report("git", "Unable to save the token", error)
        }
        refreshGitAuth()
    }

    private suspend fun generateSshKey() {
        workbench.update { it.copy(gitAuth = it.gitAuth.copy(busy = true)) }
        val available = authenticator.ensureSshAvailable()
        val outcome = if (available) {
            authenticator.generateSshKey(credentialStore.describeHttps() ?: "icode")
        } else {
            GitOperationOutcome(false, "The OpenSSH client could not be installed")
        }
        if (!outcome.succeeded) workbench.message(outcome.message)
        refreshGitAuth()
    }

    private suspend fun testGitConnection(url: String) {
        workbench.update { it.copy(gitAuth = it.gitAuth.copy(busy = true, lastTest = null)) }
        val outcome = authenticator.testConnection(url)
        workbench.update {
            it.copy(
                gitAuth = it.gitAuth.copy(
                    busy = false,
                    lastTest = GitAuthTestResult(outcome.succeeded, outcome.message),
                ),
            )
        }
    }

    /** Recomputes what the auth surface may show — never the secret itself. */
    private suspend fun refreshGitAuth() {
        val account = credentialStore.describeHttps()
        val hasSshKey = authenticator.hasSshKey()
        val publicKey = authenticator.readSshPublicKey()
        suspend fun config(workspace: String?, scope: String, key: String): String? =
            gitCli.run(workspace, listOf("config", scope, "--get", key))
                .takeIf { it.succeeded }?.stdout?.trim()?.takeIf { it.isNotEmpty() }
        val gitAvailable = git.isAvailable()
        val globalName = if (gitAvailable) config(null, "--global", "user.name") else null
        val globalEmail = if (gitAvailable) config(null, "--global", "user.email") else null
        val workspace = activeRootLocation?.takeIf { workbench.current.gitRepository != null }
        val localName = if (gitAvailable && workspace != null) config(workspace, "--local", "user.name") else null
        val localEmail = if (gitAvailable && workspace != null) config(workspace, "--local", "user.email") else null
        workbench.update {
            it.copy(
                gitAvailable = gitAvailable,
                globalGitAuthorName = globalName,
                globalGitAuthorEmail = globalEmail,
                localGitAuthorName = localName,
                localGitAuthorEmail = localEmail,
                gitIdentityLoaded = true,
                gitAuth = it.gitAuth.copy(
                    httpsAccount = account,
                    hasSshKey = hasSshKey,
                    sshPublicKey = publicKey,
                    busy = false,
                ),
            )
        }
    }

    private suspend fun runGitAction(
        operation: suspend (String) -> Unit,
        onSuccess: suspend () -> Unit = {},
    ) {
        val workspace = activeRootLocation
        if (workspace == null) {
            workbench.message("Open a workspace first")
            return
        }
        runCatching {
            operation(workspace)
            refreshWorkspaceState(workspace)
            onSuccess()
        }.onFailure { error ->
            workbench.report("git", "Git operation failed", error)
        }
    }

    /**
     * Resume, leaving the terminal and a finished command often ask together;
     * one refresh answers every request made while the last one ran.
     */
    private suspend fun refreshOnRequest() {
        refreshRequests.conflate().collect {
            delay(REFRESH_SETTLE_MILLIS)
            workbench.mutations.withLock { refreshActiveWorkspace() }
        }
    }

    private fun requestRefresh(rebuildIndex: Boolean) {
        if (rebuildIndex) indexStale = true
        refreshRequests.tryEmit(Unit)
    }

    /** The folders shown and those of open files follow the state, while iCode is seen. */
    private suspend fun followWatchedFolders() {
        workbench.snapshot.map(::watchedFolders).distinctUntilChanged().collect { folders ->
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
            workbench.mutations.withLock {
                if (activeRootLocation != null || !Environment.isExternalStorageManager()) return@withLock
                if (!File(session.workspaceLocation).isDirectory) return@withLock
                openWorkspace(session.workspaceLocation, persist = false, announce = false)
                if (activeRootLocation != session.workspaceLocation) return@withLock
                session.documents.filter { File(it).isFile }.forEach { documents.open(it, announce = false) }
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

    /** A command that ran in the open workspace may have written to it. */
    private suspend fun refreshAfterCommands() {
        terminals.commandsFinished.collect { sessionId ->
            val workspace = workbench.onMain { terminals.workspaceOf(sessionId) }
            if (workspace != null && workspace == activeRootLocation) requestRefresh(rebuildIndex = true)
        }
    }

    /**
     * Opens a draft's file for the recovery question, switching to its
     * workspace first when it belongs to another one the user has opened.
     */
    private suspend fun openRecoverableDraft(location: String) {
        val root = activeRootLocation
        if (root == null || !location.isAtOrUnder(root)) {
            val home = library.workspaceHolding(location) ?: run {
                workbench.message("Open the folder that holds ${File(location).name} first; its draft is kept")
                return
            }
            openWorkspace(home, persist = true)
            // A switch that waits on unsaved edits, or failed, has said so already.
            if (activeRootLocation != home) return
        }
        documents.open(location)
    }

    /**
     * The disk version against the editor's text, as Git diffs two files, into
     * the diff view (US-EDIT-009 "Compare shows both relevant versions").
     */
    private suspend fun compareWithDisk(location: String) {
        val root = activeRootLocation ?: return
        val change = workbench.current.externalChanges.firstOrNull { it.location == location } ?: return
        val document = workbench.current.openDocuments.firstOrNull { it.location == location } ?: return
        val read = documents.sourceOf(location) ?: return
        if (!git.isAvailable()) {
            workbench.message("Comparing uses Git, which is not installed yet. Install it from Source Control")
            return
        }
        workbench.update { it.copy(activeDiff = null) }
        val editorBytes = DocumentCodec.encode(document.text, read.encoding, read.hasByteOrderMark)
        runCatching { gitCli.compareWithBytes(root, change.path, editorBytes) }
            .onSuccess { outcome ->
                when (outcome) {
                    is GitOutcome.Success -> workbench.update {
                        it.copy(activeDiff = outcome.value ?: GitFileDiff(path = change.path, hunks = emptyList()))
                    }
                    is GitOutcome.Failure -> workbench.message(outcome.message)
                }
            }
            .onFailure { error -> workbench.report("git", "Unable to compare the two versions", error) }
    }

    private suspend fun refreshActiveWorkspace() {
        val root = activeRootLocation
        if (root == null) {
            library.refreshRecents()
            return
        }
        runCatching {
            tree.reload(root)
            refreshWorkspaceState(root)
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

    /**
     * Refreshes Git state shortly after the worktree changes.
     *
     * Each refresh starts PRoot, which costs about a second, so saves are
     * coalesced: a new request replaces the one still waiting rather than
     * queueing another process behind it.
     */
    private fun scheduleGitRefresh() {
        val root = activeRootLocation ?: return
        gitRefreshJob?.cancel()
        gitRefreshJob = scope.launch {
            delay(GIT_REFRESH_DEBOUNCE_MILLIS)
            workbench.mutations.withLock { refreshWorkspaceState(root) }
        }
    }

    private suspend fun refreshWorkspaceState(root: String) {
        val available = git.isAvailable()
        // Without git the inspect script cannot run, but .git on disk still
        // tells us this is a repository - so the UI can offer to install Git
        // instead of wrongly offering to initialise a repo that already exists.
        val gitState = if (available) {
            git.inspect(root)
        } else {
            GitWorkspaceSnapshot(repository = null, changes = emptyList())
        }
        val recents = library.recentWorkspaces()
        val files = tree.rows(root, gitState)
        workbench.update {
            it.copy(
                workspaces = recents,
                files = files,
                gitChanges = gitState.changes,
                gitRepository = gitState.repository,
                gitAvailable = available,
                activeBranch = gitState.repository?.branch
                    ?: GitHeadReader.branchOf(root).orEmpty(),
                recentDocuments = library.recentDocuments(root),
            )
        }
    }

    private companion object {
        /** Long enough to absorb a burst of saves, short enough to feel immediate. */
        const val GIT_REFRESH_DEBOUNCE_MILLIS = 350L

        /** Lets requests that arrive together (resume and a panel change) become one refresh. */
        const val REFRESH_SETTLE_MILLIS = 300L
    }
}
