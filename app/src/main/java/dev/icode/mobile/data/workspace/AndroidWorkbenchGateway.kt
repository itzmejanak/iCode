package dev.icode.mobile.data.workspace

import android.content.Context
import android.os.Environment
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.git.ConflictFiles
import dev.icode.mobile.data.git.GitAuthCoordinator
import dev.icode.mobile.data.git.GitConflictCoordinator
import dev.icode.mobile.data.git.GitAuthenticator
import dev.icode.mobile.data.git.GitCredentialStore
import dev.icode.mobile.data.git.GitRepositoryClient
import dev.icode.mobile.data.git.GitTransferCoordinator
import dev.icode.mobile.data.git.GitTransferLedger
import dev.icode.mobile.data.git.GitWorkspaceCoordinator
import dev.icode.mobile.data.language.LanguageServiceProcessManager
import dev.icode.mobile.data.language.LanguageServiceStore
import dev.icode.mobile.data.search.RipgrepService
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.runtime.RuntimeCoordinator
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.data.runtime.TerminalCoordinator
import dev.icode.mobile.data.runtime.TerminalSessionManager
import com.termux.terminal.TerminalSession
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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
    /** git, openssh-client and ripgrep install through it, one at a time. */
    private val packages = RuntimePackageInstaller(runtimeLayout, scope)
    private val gitCli = CliGitService(runtimeLayout, packages)
    private val git = GitRepositoryClient(gitCli)
    private val authenticator = GitAuthenticator(runtimeLayout, gitCli, packages, GitCredentialStore(appContext))
    /** Shared with every screen this process shows; see [close]. */
    private val terminals = TerminalSessionManager.get(appContext)
    private val terminal: TerminalCoordinator = TerminalCoordinator(
        workbench,
        terminals,
        runtimeInstalled = { runtimeLayout.isInstalled },
        activeRoot = { activeRootLocation },
        openWorkspaceTerminal = { location -> openWorkspace(location, persist = true, openTerminal = true) },
        workspaceChanged = { requestRefresh(rebuildIndex = true) },
    )
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
    private val gitWorkspace: GitWorkspaceCoordinator = GitWorkspaceCoordinator(
        workbench,
        scope,
        git,
        tree,
        activeRoot = { activeRootLocation },
        recentWorkspaces = library::recentWorkspaces,
        recentDocuments = library::recentDocuments,
        onInstalled = { gitAuth.refresh() },
    )
    private val transferLedger = GitTransferLedger(appContext)
    /** Read before any transfer can start, or it would report the one just started. */
    private val interruptedTransfer = transferLedger.takeInterrupted()
    private val gitConflicts = GitConflictCoordinator(
        workbench,
        object : ConflictFiles {
            override fun read(location: String) = source.readDocument(location)
            override fun write(document: WorkspaceDocument, text: String) = source.writeDocument(document, text)
            override fun hasUnsavedEdits(location: String) =
                workbench.current.openDocuments.any { it.location == location && it.dirty }
            override suspend fun written(location: String) = documents.reconcileWithDisk()
        },
        activeRoot = { activeRootLocation },
        act = gitWorkspace::act,
        stage = { workspace, path -> git.markResolved(workspace, path) },
        refresh = { gitWorkspace.refreshActive() },
    )
    private val gitTransfers = GitTransferCoordinator(
        workbench,
        scope,
        git,
        activeRoot = { activeRootLocation },
        withCredentials = { block -> authenticator.withHttpsCredentials(block) },
        // A transfer runs outside the lock; what it changes afterwards takes it.
        refresh = { workspace -> workbench.mutations.withLock { gitWorkspace.refresh(workspace) } },
        openClone = { location -> workbench.mutations.withLock { openWorkspace(location, persist = true) } },
        ledger = transferLedger,
    )
    private val gitAuth: GitAuthCoordinator = GitAuthCoordinator(
        workbench,
        git,
        authenticator,
        activeRoot = { activeRootLocation },
        actInRepository = gitWorkspace::act,
        refreshRepository = { root -> gitWorkspace.refresh(root) },
    )
    private val search = SearchCoordinator(workbench, scope, RipgrepService(runtimeLayout, packages)) { activeRootLocation }
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
        onSaved = { gitWorkspace.scheduleRefresh() },
    )
    private val items = WorkspaceItemsCoordinator(
        workbench,
        source,
        tree,
        documents,
        library,
        activeRoot = { activeRootLocation },
        refreshWorkspaceState = { root -> gitWorkspace.refresh(root) },
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
        scope.launch { refreshOnRequest() }
        scope.launch { followWatchedFolders() }
        scope.launch { restoreThenKeepSession() }
        scope.launch { observeNotices() }
        terminal.follow(
            scope,
            terminalPreferences.values(null).filter { it.loaded && it.loadError == null }.map { it.terminal },
        )
        scope.launch { runtime.reportState() }
    }

    /** The live session behind a terminal tab, for the view that draws it. Main thread. */
    fun terminalSession(id: String): TerminalSession? = terminals.session(id)

    /** Reports screen changes of a terminal session until the handle is closed. Main thread. */
    fun observeTerminal(id: String, onScreenChanged: () -> Unit): AutoCloseable = terminals.observe(id, onScreenChanged)

    /** The Notifications page; first, once per process, what the previous process's end took with it. */
    private suspend fun observeNotices() {
        terminals.ledger.takePrevious()?.let(notices::add)
        interruptedTransfer?.let(notices::add)
        notices.notices.collect { list ->
            workbench.update { it.copy(notices = list) }
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
        // A transfer is its own job: nothing waits behind a slow remote.
        if (intent == WorkbenchIntent.FetchRemote) {
            gitTransfers.fetch()
            return
        }
        if (intent == WorkbenchIntent.PullRemote) {
            gitTransfers.pull()
            return
        }
        if (intent == WorkbenchIntent.PushRemote) {
            gitTransfers.push()
            return
        }
        if (intent == WorkbenchIntent.CancelGitTransfer) {
            gitTransfers.cancel()
            return
        }
        if (intent is WorkbenchIntent.CloneRepository) {
            gitTransfers.clone(intent.url, intent.destination, intent.directoryName, intent.branch)
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
                    WorkbenchIntent.InitializeGit -> gitWorkspace.initialize()
                    is WorkbenchIntent.StageFile -> gitWorkspace.stage(intent.path)
                    is WorkbenchIntent.UnstageFile -> gitWorkspace.unstage(intent.path)
                    WorkbenchIntent.StageAllFiles -> gitWorkspace.stageAll()
                    WorkbenchIntent.UnstageAllFiles -> gitWorkspace.unstageAll()
                    is WorkbenchIntent.CommitStaged -> gitWorkspace.commit(intent.message)
                    is WorkbenchIntent.ConfigureGitIdentity -> gitAuth.configureRepositoryIdentity(intent.name, intent.email)
                    is WorkbenchIntent.ConfigureGlobalGitIdentity -> gitAuth.configureGlobalIdentity(intent.name, intent.email)
                    WorkbenchIntent.ClearRepositoryGitIdentity -> gitAuth.clearRepositoryIdentity()
                    is WorkbenchIntent.SwitchBranch -> gitWorkspace.switchBranch(intent.branch)
                    is WorkbenchIntent.CreateBranch -> gitWorkspace.createBranch(intent.branch)
                    is WorkbenchIntent.DeleteBranch -> gitWorkspace.deleteBranch(intent.branch)
                    is WorkbenchIntent.CheckoutRemoteBranch -> gitWorkspace.checkoutRemote(intent.remoteBranch)
                    WorkbenchIntent.ProvisionGit -> gitWorkspace.provision()
                    WorkbenchIntent.RefreshGit -> gitWorkspace.refreshActive()
                    is WorkbenchIntent.OpenDiff -> gitWorkspace.openDiff(intent.path, intent.staged)
                    WorkbenchIntent.CloseDiff -> gitWorkspace.closeDiff()
                    is WorkbenchIntent.RequestDiscardChanges -> gitWorkspace.requestDiscard(intent.path)
                    WorkbenchIntent.CancelDiscardChanges -> gitWorkspace.cancelDiscard()
                    WorkbenchIntent.ConfirmDiscardChanges -> gitWorkspace.confirmDiscard()
                    WorkbenchIntent.AbortInProgress -> gitWorkspace.abort()
                    is WorkbenchIntent.AddRemote -> gitWorkspace.addRemote(intent.name, intent.url)
                    is WorkbenchIntent.OpenConflict -> gitConflicts.open(intent.path)
                    WorkbenchIntent.CloseConflict -> gitConflicts.close()
                    is WorkbenchIntent.ResolveConflict -> gitConflicts.resolve(intent.path, intent.sectionStart, intent.choice)
                    is WorkbenchIntent.MarkConflictResolved -> gitConflicts.markResolved(intent.path)
                    is WorkbenchIntent.UpdateCloneForm -> workbench.update {
                it.copy(cloneUrl = intent.url, cloneBranch = intent.branch)
            }
                    is WorkbenchIntent.SelectGitProvider -> gitAuth.selectProvider(intent.provider)
                    is WorkbenchIntent.SelectGitAuthMethod -> gitAuth.selectMethod(intent.method)
                    is WorkbenchIntent.SaveGitToken -> gitAuth.saveToken(intent.host, intent.username, intent.token)
                    WorkbenchIntent.ClearGitToken -> gitAuth.clearToken()
                    WorkbenchIntent.GenerateSshKey -> gitAuth.generateSshKey()
                    is WorkbenchIntent.TestGitConnection -> gitAuth.testConnection(intent.url)
                    WorkbenchIntent.RefreshGitAuth -> gitAuth.refresh()
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
                    WorkbenchIntent.OpenTerminalSession -> terminal.open()
                    is WorkbenchIntent.SelectTerminalSession -> terminal.select(intent.id)
                    is WorkbenchIntent.ShowTerminalSession -> terminal.show(intent.id)
                    is WorkbenchIntent.ReorderTerminalSession -> terminal.reorder(intent.movingId, intent.targetId)
                    is WorkbenchIntent.CloseTerminalSession -> terminal.close(intent.id)
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
            val children = source.listChildren(location)
            val gitPicture = gitWorkspace.read(root.location)
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

    private suspend fun resolveWorkspaceSwitch(intent: WorkbenchIntent.ResolveWorkspaceSwitch) {
        if (intent.saveChanges) {
            val dirty = workbench.current.openDocuments.filter { it.dirty }
            for (document in dirty) if (!documents.save(document.location)) return
        }
        openWorkspace(intent.path, persist = true, openTerminal = intent.openTerminal, dirtyChoiceResolved = true)
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
