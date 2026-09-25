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
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeInstaller
import dev.icode.mobile.data.runtime.RuntimeInspector
import dev.icode.mobile.data.runtime.RuntimeHomeBackup
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeWork
import dev.icode.mobile.data.runtime.TerminalSessionManager
import com.termux.terminal.TerminalSession
import dev.icode.mobile.domain.GitAuthTestResult
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.ContentSearchState
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitTreeStatus
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import dev.icode.mobile.domain.WorkspaceSummary
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.LanguageService
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServiceRules
import dev.icode.mobile.domain.LanguageServiceStatus
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.StorageDetails
import dev.icode.mobile.domain.SystemAccess
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.reorderDocuments
import dev.icode.mobile.domain.selectedAfterClose
import dev.icode.mobile.domain.WorkspaceNameRules
import java.util.concurrent.TimeUnit
import java.util.regex.PatternSyntaxException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AndroidWorkbenchGateway(context: Context) : WorkbenchGateway {
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
    private val systemAccessSource = AndroidSystemAccess(appContext)
    private val notices = NoticeStore.get(appContext)
    private val loadedLanguageConfigs = runCatching { languageStore.load() }
    private var languageConfigs = loadedLanguageConfigs.getOrDefault(emptyList())
    private val languageProcesses = LanguageServiceProcessManager(runtimeLayout, scope) { id, status, pid, message ->
        mutableSnapshot.update { state -> state.copy(languageServices = state.languageServices.map { service ->
            if (service.id == id) service.copy(
                running = status == LanguageServiceStatus.Running,
                status = status,
                processId = pid,
                statusMessage = message,
            ) else service
        }) }
    }
    private val terminalPreferences = AndroidWorkspacePreferencesRepository(appContext)
    private val runtimeInspector = RuntimeInspector(
        runtimeLayout,
        RuntimeInstaller(appContext, runtimeLayout).prootVersion(),
    )
    private val mutationMutex = Mutex()
    private val draftMutex = Mutex()
    private val draftStore = DocumentDraftStore(File(appContext.filesDir, "document-drafts"))
    private val lastDraftText = mutableMapOf<String, String>()
    private val recoveredExternalChanges = mutableSetOf<String>()
    private val unresolvedRecoveryLocations = mutableSetOf<String>()
    private val mutableSnapshot = MutableStateFlow(emptySnapshot())
    private val mutableEffects = MutableSharedFlow<WorkbenchEffect>(extraBufferCapacity = 1)
    private val childrenByParent = mutableMapOf<String, List<DocumentNode>>()
    private val expandedLocations = mutableSetOf<String>()
    private var activeRootLocation: String? = null
    private var pendingBrowserPurpose: FolderBrowserPurpose? = null
    private val documentSources = ConcurrentHashMap<String, WorkspaceDocument>()
    private val savedDocumentTexts = ConcurrentHashMap<String, String>()
    @Volatile private var indexedWorkspace: String? = null
    private var gitRefreshJob: Job? = null
    private var storageRefreshJob: Job? = null
    private var runtimeInstallJob: Job? = null

    override val snapshot: StateFlow<WorkbenchSnapshot> = mutableSnapshot.asStateFlow()
    override val effects: SharedFlow<WorkbenchEffect> = mutableEffects.asSharedFlow()

    init {
        mutableSnapshot.update { it.copy(
            languageServices = languageConfigs.map(::languageServiceView),
            languageServicesLoadError = loadedLanguageConfigs.exceptionOrNull()?.userMessage("Unable to load saved services"),
        ) }
        loadedLanguageConfigs.exceptionOrNull()?.let { error ->
            scope.launch { mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to load language services"))) }
        }
        scope.launch { refreshRecents() }
        scope.launch { observeTerminals() }
        scope.launch { observeNotices() }
        scope.launch { reportTerminalEndings() }
        scope.launch {
            terminalPreferences.values(null).collect { preferences ->
                if (preferences.loaded && preferences.loadError == null) {
                    onMain { terminals.applySettings(preferences.terminal) }
                }
            }
        }
        scope.launch { reportRuntimeState() }
    }

    /** The terminal session manager is main-thread confined. */
    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main.immediate) { block() }

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
                mutableEffects.emit(WorkbenchEffect.Message("${ending.title} exited with code ${ending.exitCode}"))
            }
        }
    }

    /** The Notifications page; first, once per process, what the previous process's end took with it. */
    private suspend fun observeNotices() {
        terminals.ledger.takePrevious()?.let(notices::add)
        notices.notices.collect { list ->
            mutableSnapshot.update { it.copy(notices = list) }
        }
    }

    private suspend fun observeTerminals() {
        terminals.snapshot.collect { terminal ->
            mutableSnapshot.update { it.copy(terminal = terminal) }
        }
    }

    /** Publishes what is actually on disk; never claims readiness it has not checked. */
    private fun reportRuntimeState() {
        val preservedHome = RuntimeHomeBackup(runtimeLayout.rootfs, runtimeLayout.runtimeRoot).hasPreservedHome
        val state = when {
            !runtimeLayout.binariesPresent ->
                RuntimeState.Failed("The runtime binaries are missing from this build", recoverable = false)
            runtimeLayout.isInstalled -> RuntimeState.Ready(installedRelease())
            preservedHome -> RuntimeState.NotInstalled("A runtime repair was interrupted; your home is preserved for retry")
            else -> RuntimeState.NotInstalled("The Linux runtime is not installed yet")
        }
        mutableSnapshot.update {
            it.copy(
                runtime = state,
                runtimeHealthy = state is RuntimeState.Ready,
                runtimeInstallReason = if (preservedHome && state !is RuntimeState.Ready)
                    RuntimeInstallReason.Repair else RuntimeInstallReason.Initial,
                systemAccess = systemAccessSource.read(),
            )
        }
    }

    private fun installedRelease(): String =
        runCatching {
            org.json.JSONObject(runtimeLayout.installMarker.readText()).getString("base")
        }.getOrDefault("Linux runtime")

    private fun refreshRuntimeDetails() {
        android.util.Log.d("iCodeRuntime", "refreshRuntimeDetails dispatched")
        scope.launch(Dispatchers.IO) {
            val details = runCatching { runtimeInspector.inspect() }
                .onFailure { android.util.Log.e("iCodeRuntime", "inspect failed", it) }
                .getOrNull() ?: return@launch
            android.util.Log.d("iCodeRuntime", "inspected: ${'$'}details")
            mutableSnapshot.update { it.copy(runtimeDetails = details) }
        }
    }

    /** One state-driven installer for first install, repair and reset. */
    private suspend fun installRuntime(reason: RuntimeInstallReason): Boolean {
        if (runtimeInstallJob?.isActive == true || mutableSnapshot.value.runtime is RuntimeState.Downloading ||
            mutableSnapshot.value.runtime is RuntimeState.Installing) {
            mutableEffects.emit(WorkbenchEffect.Message("Runtime installation is already running"))
            return false
        }
        if (reason != RuntimeInstallReason.Initial &&
            (terminals.hasRunningSessions() || languageProcesses.hasActiveProcesses())) {
            mutableEffects.emit(WorkbenchEffect.Message("Close running terminal and language service sessions before rebuilding the runtime"))
            return false
        }
        val label = when (reason) {
            RuntimeInstallReason.Initial -> "Preparing runtime installation"
            RuntimeInstallReason.Repair -> "Preparing runtime repair"
            RuntimeInstallReason.Reset -> "Preparing runtime reset"
        }
        mutableSnapshot.update { it.copy(
            runtime = RuntimeState.Installing(label),
            runtimeHealthy = false,
            runtimeInstallReason = reason,
        ) }
        runtimeInstallJob = scope.launch {
            try {
                // Android kills a backgrounded app mid-install otherwise.
                RuntimeWork.installStarted()
                RuntimeForegroundService.ensureRunning(appContext)
                RuntimeInstaller(appContext, runtimeLayout).install(reason).collect { state ->
                    mutableSnapshot.update { it.copy(runtime = state, runtimeHealthy = state is RuntimeState.Ready) }
                    if (state is RuntimeState.Failed) {
                        mutableEffects.emit(WorkbenchEffect.Message(state.reason))
                    }
                    if (state is RuntimeState.Ready) refreshRuntimeDetails()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val message = error.userMessage("Runtime installation failed")
                mutableSnapshot.update { it.copy(runtime = RuntimeState.Failed(message), runtimeHealthy = false) }
                mutableEffects.emit(WorkbenchEffect.Message(message))
            } finally {
                RuntimeWork.installEnded()
            }
        }
        return true
    }

    override fun dispatch(intent: WorkbenchIntent) {
        if (intent is WorkbenchIntent.SelectLanguageService) {
            mutableSnapshot.update { state ->
                state.copy(selectedLanguageServiceId = intent.id.takeIf { id ->
                    state.languageServices.any { service -> service.id == id }
                })
            }
            return
        }
        if (intent == WorkbenchIntent.RefreshStorage) {
            refreshStorage()
            return
        }
        // Reading the runtime's own files changes nothing, so it does not wait
        // on the mutation lock behind whatever the workspace is doing.
        if (intent == WorkbenchIntent.RefreshRuntimeDetails) {
            refreshRuntimeDetails()
            return
        }
        if (intent is WorkbenchIntent.UpdateDocumentText) {
            updateDocumentText(intent)
            return
        }
        scope.launch {
            mutationMutex.withLock {
                when (intent) {
                    is WorkbenchIntent.RequestSystemPermission ->
                        mutableEffects.emit(WorkbenchEffect.RequestSystemPermission(intent.permission))
                    is WorkbenchIntent.SystemPermissionAnswered -> systemPermissionAnswered(intent.permission)
                    is WorkbenchIntent.DeclineSystemPermission -> {
                        systemAccessSource.markOffered(intent.permission)
                        refreshSystemAccess()
                    }
                    is WorkbenchIntent.OpenSystemSettings ->
                        mutableEffects.emit(WorkbenchEffect.OpenSystemSettings(intent.permission))
                    WorkbenchIntent.RefreshSystemAccess -> refreshSystemAccess()
                    WorkbenchIntent.MarkNoticesRead -> notices.markAllRead()
                    is WorkbenchIntent.DeleteNotice -> notices.delete(intent.id)
                    WorkbenchIntent.ClearNotices -> notices.clear()
                    is WorkbenchIntent.StartFolderBrowser -> startFolderBrowser(intent.purpose)
                    is WorkbenchIntent.ShowWorkspaceLocation -> showWorkspaceLocation(intent.path)
                    is WorkbenchIntent.BrowseFolder -> browseFolder(intent.path)
                    WorkbenchIntent.BrowseParentFolder -> browseParentFolder()
                    WorkbenchIntent.ConfirmFolderSelection -> confirmFolderSelection()
                    is WorkbenchIntent.RequestCreateFolder -> requestCreateFolder(intent.name)
                    is WorkbenchIntent.OpenWorkspace -> openWorkspace(intent.path, persist = true)
                    is WorkbenchIntent.OpenWorkspaceTerminal -> openWorkspace(intent.path, persist = true, openTerminal = true)
                    is WorkbenchIntent.ResolveWorkspaceSwitch -> resolveWorkspaceSwitch(intent)
                    is WorkbenchIntent.RemoveRecentWorkspace -> removeRecentWorkspace(intent.path)
                    is WorkbenchIntent.OpenFile -> {
                        openDocument(intent.path, intent.lineNumber, intent.column)
                        ensureFileIndex(activeRootLocation ?: intent.path)
                    }
                    is WorkbenchIntent.CreateFile -> createFile(intent)
                    is WorkbenchIntent.CreateWorkspaceFolder -> createWorkspaceFolder(intent)
                    is WorkbenchIntent.SaveDocument -> saveDocument(
                        intent.location,
                        intent.automatic,
                        intent.expectedText,
                        intent.expectedWorkspace,
                    )
                    is WorkbenchIntent.RestoreDraft -> restoreDraft(intent.location)
                    is WorkbenchIntent.DiscardDraft -> {
                        if (mutableSnapshot.value.openDocuments.firstOrNull { it.location == intent.location }?.dirty == true) {
                            mutableEffects.emit(WorkbenchEffect.Message("This document has new edits; close or save them before discarding recovery"))
                        } else {
                            discardDraft(intent.location)
                            val sourceReadOnly = documentSources[intent.location]?.readOnly ?: false
                            mutableSnapshot.update { state ->
                                val unlocked = state.openDocuments.firstOrNull { it.location == intent.location }
                                    ?.copy(readOnly = sourceReadOnly) ?: return@update state
                                state.copy(
                                    openDocuments = state.openDocuments.map { if (it.location == intent.location) unlocked else it },
                                    activeDocument = if (state.activeDocument?.location == intent.location) unlocked else state.activeDocument,
                                )
                            }
                        }
                    }
                    is WorkbenchIntent.ConfirmRecoveredOverwrite -> saveDocument(
                        intent.location,
                        allowRecoveredOverwrite = true,
                    )
                    is WorkbenchIntent.SelectDocument -> selectDocument(intent.location)
                    is WorkbenchIntent.ReorderDocument -> reorderDocument(intent.movingLocation, intent.targetLocation)
                    is WorkbenchIntent.CloseDocument -> closeDocument(intent.location, intent.saveChanges)
                    is WorkbenchIntent.CloseOtherDocuments -> closeOtherDocuments(intent.keepLocation, intent.saveChanges)
                    WorkbenchIntent.CloseSavedDocuments -> closeSavedDocuments()
                    WorkbenchIntent.InitializeGit -> initializeGit()
                    is WorkbenchIntent.ToggleFolder -> toggleFolder(intent.path)
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
                    WorkbenchIntent.CloseDiff -> mutableSnapshot.update { it.copy(activeDiff = null) }
                    is WorkbenchIntent.RequestDiscardChanges -> mutableSnapshot.update { state ->
                        state.copy(pendingDiscard = state.gitChanges.firstOrNull { it.path == intent.path })
                    }
                    WorkbenchIntent.CancelDiscardChanges -> mutableSnapshot.update { it.copy(pendingDiscard = null) }
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
                    WorkbenchIntent.CloseConflict -> mutableSnapshot.update { it.copy(activeConflict = null) }
                    is WorkbenchIntent.ResolveConflict -> resolveConflict(intent.path, intent.choice)
                    is WorkbenchIntent.MarkConflictResolved -> runGitAction(
                        operation = { git.markResolved(it, intent.path) },
                    )
                    is WorkbenchIntent.UpdateCloneForm -> mutableSnapshot.update {
                it.copy(cloneUrl = intent.url, cloneBranch = intent.branch)
            }
            is WorkbenchIntent.CloneRepository -> cloneRepository(intent)
                    is WorkbenchIntent.SelectGitProvider -> mutableSnapshot.update {
                it.copy(gitAuth = it.gitAuth.copy(provider = intent.provider))
            }
            is WorkbenchIntent.SelectGitAuthMethod -> mutableSnapshot.update {
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
                    is WorkbenchIntent.SelectLanguageService -> Unit
                    is WorkbenchIntent.SaveLanguageService -> saveLanguageService(intent)
                    is WorkbenchIntent.RemoveLanguageService -> removeLanguageService(intent.id)
                    is WorkbenchIntent.StartLanguageService -> startLanguageService(intent.id)
                    is WorkbenchIntent.StopLanguageService -> languageProcesses.stop(intent.id)
                    is WorkbenchIntent.RestartLanguageService -> restartLanguageService(intent.id)
                    is WorkbenchIntent.RunContentSearch -> runContentSearch(intent.query, intent.matchCase, intent.wholeWord, intent.regex)
                    WorkbenchIntent.ClearContentSearch -> clearContentSearch()
                    WorkbenchIntent.Refresh -> refreshActiveWorkspace()
                    WorkbenchIntent.InstallRuntime -> installRuntime(RuntimeInstallReason.Initial)
                    WorkbenchIntent.ClearAppCache -> clearAppCache()
                    WorkbenchIntent.RemoveStaleRecents -> removeStaleRecents()
                    WorkbenchIntent.RefreshStorage -> Unit
                    // Repair keeps the home directory, reset does not; both go
                    // through the one installer rather than a second path.
                    WorkbenchIntent.RepairRuntime -> {
                        if (installRuntime(RuntimeInstallReason.Repair)) {
                            mutableEffects.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        }
                    }
                    WorkbenchIntent.ResetRuntime -> {
                        if (installRuntime(RuntimeInstallReason.Reset)) {
                            mutableEffects.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        }
                    }
                    WorkbenchIntent.OpenTerminalSession -> openTerminalSession()
                    is WorkbenchIntent.SelectTerminalSession -> onMain { terminals.select(intent.id) }
                    is WorkbenchIntent.ShowTerminalSession -> showTerminalSession(intent.id)
                    is WorkbenchIntent.ReorderTerminalSession -> onMain { terminals.reorder(intent.movingId, intent.targetId) }
                    is WorkbenchIntent.CloseTerminalSession -> onMain { terminals.close(intent.id) }
                    // Handled before the lock; see the top of dispatch.
                    is WorkbenchIntent.OpenExternalUrl -> mutableEffects.emit(WorkbenchEffect.OpenExternalUrl(intent.url))
                    is WorkbenchIntent.UpdateDocumentText,
                    is WorkbenchIntent.SetLanguageServiceRunning,
                    -> Unit
                }
            }
        }
    }

    private fun refreshSystemAccess(): SystemAccess {
        val access = systemAccessSource.read()
        val storageChanged = mutableSnapshot.value.systemAccess?.storage != access.storage
        mutableSnapshot.update { it.copy(systemAccess = access) }
        // Whether a recent workspace can be opened follows storage access.
        if (storageChanged) refreshRecents()
        return access
    }

    /**
     * Android's prompt closed. Whatever the answer, onboarding has now offered
     * the permission; a folder browser that was waiting for storage opens.
     */
    private suspend fun systemPermissionAnswered(permission: SystemPermission) {
        systemAccessSource.markOffered(permission)
        val granted = refreshSystemAccess().granted(permission)
        if (permission == SystemPermission.Storage) {
            val purpose = pendingBrowserPurpose
            pendingBrowserPurpose = null
            if (granted && purpose != null) return startFolderBrowser(purpose)
        }
        mutableEffects.emit(WorkbenchEffect.SystemPermissionResolved(permission, granted))
    }

    private suspend fun startFolderBrowser(purpose: FolderBrowserPurpose) {
        if (!Environment.isExternalStorageManager()) {
            pendingBrowserPurpose = purpose
            mutableEffects.emit(WorkbenchEffect.RequestSystemPermission(SystemPermission.Storage))
            return
        }
        runCatching { source.initialBrowser(purpose) }
            .onSuccess { browser ->
                mutableSnapshot.update { snapshot -> snapshot.copy(folderBrowser = browser) }
                mutableEffects.emit(WorkbenchEffect.FolderBrowserOpened)
            }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read phone storage")))
            }
    }

    private suspend fun showWorkspaceLocation(location: String) {
        if (!Environment.isExternalStorageManager()) {
            mutableEffects.emit(WorkbenchEffect.Message("All files access is needed to browse this folder"))
            return
        }
        runCatching { source.browse(location, FolderBrowserPurpose.OpenWorkspace) }
            .onSuccess { browser ->
                mutableSnapshot.update { it.copy(folderBrowser = browser) }
                mutableEffects.emit(WorkbenchEffect.FolderBrowserOpened)
            }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to locate folder")))
            }
    }

    private suspend fun removeRecentWorkspace(location: String) {
        runCatching { store.remove(location) }
            .onSuccess { mutableSnapshot.update { state ->
                state.copy(workspaces = state.workspaces.filterNot { it.location == location })
            } }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to remove recent workspace")))
            }
    }

    private suspend fun browseFolder(path: String) {
        val browser = mutableSnapshot.value.folderBrowser ?: return
        runCatching { source.browse(path, browser.purpose) }
            .onSuccess { mutableSnapshot.update { snapshot -> snapshot.copy(folderBrowser = it) } }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read directory")))
            }
    }

    private suspend fun browseParentFolder() {
        val browser = mutableSnapshot.value.folderBrowser ?: return
        runCatching { source.parent(browser.currentPath, browser.purpose) }
            .onSuccess { mutableSnapshot.update { snapshot -> snapshot.copy(folderBrowser = it) } }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read parent directory")))
            }
    }

    private suspend fun confirmFolderSelection() {
        val browser = mutableSnapshot.value.folderBrowser ?: return
        when (browser.purpose) {
            FolderBrowserPurpose.OpenWorkspace -> openWorkspace(browser.currentPath, persist = true)
            FolderBrowserPurpose.CreateFolderDestination -> {
                mutableSnapshot.update { snapshot -> snapshot.copy(
                    createFolderDestination = browser.currentPath,
                    createFolderDestinationDisplay = browser.displayPath,
                ) }
                mutableEffects.emit(WorkbenchEffect.FolderDestinationSelected(browser.currentPath))
            }
        }
    }

    /**
     * Selects the session, then opens its workspace on the Terminal panel. The
     * workspace path reuses the running session the selection now names, and
     * goes through the same dirty-document confirmation as any switch.
     */
    private suspend fun showTerminalSession(id: String) {
        val workspace = onMain {
            if (terminals.snapshot.value.sessions.none { it.id == id && it.isRunning }) return@onMain null
            terminals.select(id)
            terminals.workspaceOf(id) ?: ""
        }
        when (workspace) {
            null -> mutableEffects.emit(WorkbenchEffect.Message("That terminal session has ended"))
            "" -> mutableEffects.emit(WorkbenchEffect.WorkspaceTerminalOpened(activeRootLocation.orEmpty()))
            else -> openWorkspace(workspace, persist = true, openTerminal = true)
        }
    }

    private suspend fun openTerminalSession() {
        if (!runtimeLayout.isInstalled) {
            mutableEffects.emit(WorkbenchEffect.Message("Install the Linux runtime before opening a terminal"))
            return
        }
        if (terminals.open(activeRootLocation) == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Unable to start a terminal session"))
        }
    }

    /**
     * The screen is going away. Terminal sessions are the user's and outlive
     * it: they end only when the user ends them, and the next screen adopts
     * them. RuntimeForegroundService keeps them alive meanwhile, and stops by
     * itself when none runs.
     */
    override fun close() {
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
        if (mutableSnapshot.value.languageServicesLoadError != null) {
            mutableEffects.emit(WorkbenchEffect.Message("Saved services could not be read; configuration was not overwritten"))
            return
        }
        val name = intent.name.trim()
        val command = intent.command.trim()
        val options = intent.initializationOptions.trim().ifBlank { "{}" }
        val error = LanguageServiceRules.validate(name, command, intent.patterns, options)
            ?: runCatching { LanguageServiceRules.commandArguments(command); org.json.JSONObject(options) }
                .exceptionOrNull()?.let { "Check command quoting and enter a JSON object for initialization options" }
        if (error != null) {
            mutableEffects.emit(WorkbenchEffect.Message(error))
            return
        }
        val existingId = intent.id
        if (existingId != null && languageConfigs.none { it.id == existingId }) {
            mutableEffects.emit(WorkbenchEffect.Message("This language service no longer exists"))
            return
        }
        if (existingId != null && languageProcesses.isActive(existingId)) {
            mutableEffects.emit(WorkbenchEffect.Message("Stop this server before editing its configuration"))
            return
        }
        if (existingId == null && languageConfigs.size >= LanguageServiceRules.MAX_SERVICES) {
            mutableEffects.emit(WorkbenchEffect.Message("Maximum ${LanguageServiceRules.MAX_SERVICES} language services"))
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
            mutableSnapshot.update { state -> state.copy(
                languageServices = updated.map(::languageServiceView),
                selectedLanguageServiceId = id,
            ) }
        } catch (failure: Exception) {
            mutableEffects.emit(WorkbenchEffect.Message(failure.userMessage("Unable to save language service")))
        }
    }

    private suspend fun removeLanguageService(id: String) {
        if (mutableSnapshot.value.languageServicesLoadError != null) return
        if (languageConfigs.none { it.id == id }) return
        val updated = languageConfigs.filterNot { it.id == id }
        try {
            languageStore.save(updated)
            languageConfigs = updated
            languageProcesses.stop(id)
            mutableSnapshot.update { state -> state.copy(
                languageServices = state.languageServices.filterNot { it.id == id },
                selectedLanguageServiceId = null,
            ) }
        } catch (failure: Exception) {
            mutableEffects.emit(WorkbenchEffect.Message(failure.userMessage("Unable to remove language service")))
        }
    }

    private suspend fun startLanguageService(id: String) {
        val config = languageConfigs.firstOrNull { it.id == id } ?: return
        val workspace = activeRootLocation
        if (workspace == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Open a workspace before starting a language service"))
            return
        }
        languageProcesses.start(config, workspace)?.let { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error))
        }
    }

    private fun restartLanguageService(id: String) {
        languageProcesses.stop(id)
        scope.launch {
            repeat(30) {
                if (!languageProcesses.isActive(id)) {
                    mutationMutex.withLock { startLanguageService(id) }
                    return@launch
                }
                delay(100)
            }
            mutableEffects.emit(WorkbenchEffect.Message("Server did not stop; retry after it exits"))
        }
    }

    private suspend fun requestCreateFolder(rawName: String) {
        val name = rawName.trim()
        val error = WorkspaceNameRules.validationError(name)
        if (error != null) {
            mutableEffects.emit(WorkbenchEffect.Message(error))
        } else if (mutableSnapshot.value.createFolderDestination == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Choose a destination first"))
        } else {
            createFolder(name)
        }
    }

    private suspend fun createFolder(name: String) {
        val parentLocation = mutableSnapshot.value.createFolderDestination ?: return
        runCatching {
            val location = source.createDirectory(parentLocation, name)
            openWorkspace(location, persist = true)
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to create folder")))
        }
    }

    private suspend fun openWorkspace(
        location: String,
        persist: Boolean,
        openTerminal: Boolean = false,
        dirtyChoiceResolved: Boolean = false,
    ) {
        if (!Environment.isExternalStorageManager()) {
            mutableEffects.emit(WorkbenchEffect.Message("All files access is needed to open this folder"))
            return
        }
        if (!source.isAvailable(location)) {
            val reason = if (!File(location).exists()) "This folder no longer exists" else "This folder is not accessible"
            mutableEffects.emit(WorkbenchEffect.Message(reason))
            return
        }
        if (location == activeRootLocation) {
            if (openTerminal && runtimeLayout.isInstalled) {
                runCatching { terminals.open(location, reuseExisting = true) }
                    .onSuccess { mutableSnapshot.update { it.copy(terminal = terminals.snapshot.value) } }
                    .onFailure { error ->
                        mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to start terminal")))
                    }
            }
            mutableEffects.emit(
                if (openTerminal) WorkbenchEffect.WorkspaceTerminalOpened(location)
                else WorkbenchEffect.WorkspaceOpened(location),
            )
            return
        }
        val dirtyCount = mutableSnapshot.value.openDocuments.count { it.dirty }
        if (dirtyCount > 0 && !dirtyChoiceResolved) {
            mutableEffects.emit(WorkbenchEffect.ConfirmWorkspaceSwitch(location, openTerminal, dirtyCount))
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
            val closingDocuments = mutableSnapshot.value.openDocuments
                .filter { it.dirty || it.location !in unresolvedRecoveryLocations }
                .map { it.location }
            languageProcesses.stopAll()
            activeRootLocation = root.location
            childrenByParent.clear()
            childrenByParent[root.location] = children
            expandedLocations.clear()
            if (persist) {
                store.touch(
                    StoredWorkspace(
                        name = root.name,
                        location = root.location,
                        displayPath = root.displayPath,
                        lastOpenedAt = System.currentTimeMillis(),
                    ),
                )
            }
            val recents = recentSummaries()
            mutableSnapshot.update { snapshot -> snapshot.copy(
                workspaces = recents,
                files = decorateFiles(projectVisibleFiles(root.location, childrenByParent, expandedLocations), gitState),
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
                recentDocuments = recentDocumentSummaries(root.location),
                fileIndex = emptyList(),
                fileIndexTruncated = false,
                contentSearch = ContentSearchState.Empty,
            ) }
            documentSources.clear()
            savedDocumentTexts.clear()
            recoveredExternalChanges.clear()
            unresolvedRecoveryLocations.clear()
            draftMutex.withLock {
                closingDocuments.forEach { oldLocation ->
                    draftStore.delete(oldLocation)
                    lastDraftText.remove(oldLocation)
                }
            }
            indexedWorkspace = null
            if (openTerminal && runtimeLayout.isInstalled) {
                runCatching { terminals.open(root.location, reuseExisting = true) }
                    .onSuccess { mutableSnapshot.update { it.copy(terminal = terminals.snapshot.value) } }
                    .onFailure { error ->
                        mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to start terminal")))
                    }
            }
            mutableEffects.emit(
                if (openTerminal) WorkbenchEffect.WorkspaceTerminalOpened(location)
                else WorkbenchEffect.WorkspaceOpened(location),
            )
            ensureFileIndex(root.location)
        }.onFailure { error ->
            refreshRecents()
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to open folder")))
        }
    }

    private suspend fun resolveWorkspaceSwitch(intent: WorkbenchIntent.ResolveWorkspaceSwitch) {
        if (intent.saveChanges) {
            val dirty = mutableSnapshot.value.openDocuments.filter { it.dirty }
            for (document in dirty) if (!saveDocument(document.location)) return
        }
        openWorkspace(intent.path, persist = true, openTerminal = intent.openTerminal, dirtyChoiceResolved = true)
    }

    private suspend fun initializeGit() {
        val workspace = activeRootLocation
        if (workspace == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Open a workspace before initializing Git"))
            return
        }
        runCatching {
            git.initialize(workspace)
            refreshWorkspaceState(workspace)
            mutableEffects.emit(WorkbenchEffect.GitInitialized)
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to initialize Git")))
        }
    }

    private suspend fun stageFile(path: String) = runGitAction(operation = { workspace -> git.stage(workspace, path) })

    private suspend fun unstageFile(path: String) = runGitAction(operation = { workspace -> git.unstage(workspace, path) })

    private suspend fun stageAllFiles() = runGitAction(operation = { workspace -> git.stageAll(workspace) })

    private suspend fun unstageAllFiles() = runGitAction(operation = { workspace -> git.unstageAll(workspace) })

    private suspend fun commitStaged(message: String) = runGitAction(
        operation = { workspace -> git.commit(workspace, message) },
        onSuccess = { mutableEffects.emit(WorkbenchEffect.GitCommitted) },
    )

    private suspend fun configureGitIdentity(name: String, email: String) = runGitAction(
        operation = { workspace -> git.configureIdentity(workspace, name, email) },
        onSuccess = { refreshGitAuth() },
    )

    private suspend fun configureGlobalGitIdentity(name: String, email: String) {
        if (name.isBlank() || !email.matches(Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))) {
            mutableEffects.emit(WorkbenchEffect.Message("Enter a name and valid Git author email"))
            return
        }
        if (!git.isAvailable()) {
            mutableEffects.emit(WorkbenchEffect.Message("Install Git in the runtime first"))
            return
        }
        runCatching {
            listOf(
                listOf("config", "--global", "user.name", name.trim()),
                listOf("config", "--global", "user.email", email.trim()),
            ).forEach { arguments ->
                val result = gitCli.run(null, arguments)
                if (!result.succeeded) throw java.io.IOException(result.errorMessage)
            }
            activeRootLocation?.let { refreshWorkspaceState(it) }
            refreshGitAuth()
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Could not save Git identity")))
        }
    }

    private suspend fun clearRepositoryGitIdentity() {
        val workspace = activeRootLocation ?: return
        runCatching {
            listOf("user.name", "user.email").forEach { key ->
                val current = gitCli.run(workspace, listOf("config", "--local", "--get", key))
                if (current.succeeded) {
                    val result = gitCli.run(workspace, listOf("config", "--local", "--unset-all", key))
                    if (!result.succeeded) throw java.io.IOException(result.errorMessage)
                }
            }
            refreshWorkspaceState(workspace)
            refreshGitAuth()
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Could not reset repository identity")))
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
            mutableSnapshot.update { it.copy(gitAvailable = true) }
            return
        }
        mutableSnapshot.update { it.copy(gitOperation = GitOperationState.Running("Installing Git")) }
        val installed = runCatching { CliGitService(runtimeLayout).provision() }.getOrNull()
        val available = git.isAvailable()
        mutableSnapshot.update {
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
            .onSuccess { diff -> mutableSnapshot.update { it.copy(activeDiff = diff) } }
            .onFailure { error ->
                mutableSnapshot.update { it.copy(activeDiff = null) }
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read the diff")))
            }
    }

    /**
     * Git reports change paths relative to the repository root, which is not
     * the opened folder when a subdirectory of a repository is opened.
     */
    private fun conflictRoot(): String? =
        mutableSnapshot.value.gitRepository?.workTreePath ?: activeRootLocation

    /** Loads the conflicted file straight from the worktree, markers and all. */
    private suspend fun openConflict(path: String) {
        val file = File(conflictRoot() ?: return, path)
        runCatching { file.readText() }
            .onSuccess { text ->
                val parsed = GitConflictParser.parse(path, text)
                mutableSnapshot.update { it.copy(activeConflict = parsed) }
            }
            .onFailure { error ->
                mutableSnapshot.update { it.copy(activeConflict = null) }
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read the conflicted file")))
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
    private suspend fun resolveConflict(path: String, choice: dev.icode.mobile.domain.GitConflictChoice) {
        val root = conflictRoot() ?: return
        val conflict = mutableSnapshot.value.activeConflict
            ?: run {
                openConflict(path)
                mutableSnapshot.value.activeConflict
            }
            ?: return
        val resolved = GitConflictParser.resolve(conflict, choice)
        runCatching { File(root, path).writeText(resolved) }
            .onSuccess {
                openConflict(path)
                activeRootLocation?.let { refreshWorkspaceState(it) }
            }
            .onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to write the resolved file")))
            }
    }

    private suspend fun discardChanges(path: String, status: String) {
        runGitAction(operation = { workspace -> git.discard(workspace, path, status) })
        mutableSnapshot.update { it.copy(pendingDiscard = null) }
    }

    private suspend fun abortInProgress() {
        val inProgress = mutableSnapshot.value.gitRepository?.inProgress ?: return
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
            mutableEffects.emit(WorkbenchEffect.Message("Open a workspace first"))
            return
        }
        mutableSnapshot.update { it.copy(gitOperation = GitOperationState.Running(label)) }
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
                mutableSnapshot.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                refreshWorkspaceState(workspace)
                // The progress surface closes itself, so the outcome is
                // reported here or it is never reported at all.
                mutableEffects.emit(WorkbenchEffect.Message(completionMessage(label, transcript)))
            }
            .onFailure { error ->
                mutableSnapshot.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            error.userMessage("$label failed"),
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
        mutableSnapshot.update { state ->
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
        val repository = mutableSnapshot.value.gitRepository
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
        mutableSnapshot.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        var transcript = emptyList<String>()
        runCatching {
            git.clone(intent.url, intent.destination, intent.directoryName) { stderr ->
                transcript = GitProgressParser.outputLines(stderr)
                publishProgress(label, stderr, transcript)
            }
        }
            .onSuccess {
                mutableSnapshot.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                openWorkspace(File(intent.destination, intent.directoryName).path, persist = true)
            }
            .onFailure { error ->
                mutableSnapshot.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            error.userMessage("Clone failed"),
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
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to save the token")))
        }
        refreshGitAuth()
    }

    private suspend fun generateSshKey() {
        mutableSnapshot.update { it.copy(gitAuth = it.gitAuth.copy(busy = true)) }
        val available = authenticator.ensureSshAvailable()
        val outcome = if (available) {
            authenticator.generateSshKey(credentialStore.describeHttps() ?: "icode")
        } else {
            GitOperationOutcome(false, "The OpenSSH client could not be installed")
        }
        if (!outcome.succeeded) mutableEffects.emit(WorkbenchEffect.Message(outcome.message))
        refreshGitAuth()
    }

    private suspend fun testGitConnection(url: String) {
        mutableSnapshot.update { it.copy(gitAuth = it.gitAuth.copy(busy = true, lastTest = null)) }
        val outcome = authenticator.testConnection(url)
        mutableSnapshot.update {
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
        val workspace = activeRootLocation?.takeIf { mutableSnapshot.value.gitRepository != null }
        val localName = if (gitAvailable && workspace != null) config(workspace, "--local", "user.name") else null
        val localEmail = if (gitAvailable && workspace != null) config(workspace, "--local", "user.email") else null
        mutableSnapshot.update {
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
            mutableEffects.emit(WorkbenchEffect.Message("Open a workspace first"))
            return
        }
        runCatching {
            operation(workspace)
            refreshWorkspaceState(workspace)
            onSuccess()
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Git operation failed")))
        }
    }

    private suspend fun runContentSearch(query: String, matchCase: Boolean, wholeWord: Boolean, regex: Boolean) {
        val root = activeRootLocation
        if (root == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Open a workspace before searching"))
            return
        }
        if (query.isBlank()) {
            mutableSnapshot.update { snapshot -> snapshot.copy(contentSearch = ContentSearchState.Empty) }
            return
        }
        try {
            val outcome = WorkspaceIndexer.search(root, query.trim(), matchCase, wholeWord, regex)
            mutableSnapshot.update { snapshot -> snapshot.copy(
                contentSearch = ContentSearchState(
                    query = query.trim(),
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    regex = regex,
                    results = outcome.results,
                    searchedFiles = outcome.searchedFiles,
                    truncated = outcome.truncated,
                ),
            ) }
        } catch (error: PatternSyntaxException) {
            mutableSnapshot.update { snapshot -> snapshot.copy(
                contentSearch = ContentSearchState(
                    query = query.trim(),
                    matchCase = matchCase,
                    wholeWord = wholeWord,
                    regex = regex,
                    results = emptyList(),
                    searchedFiles = 0,
                    truncated = false,
                    error = "Invalid regular expression: ${error.description ?: "check the pattern"}",
                ),
            ) }
        }
    }

    private fun clearContentSearch() {
        mutableSnapshot.update { snapshot -> snapshot.copy(contentSearch = ContentSearchState.Empty) }
    }

    private suspend fun ensureFileIndex(root: String): Pair<List<dev.icode.mobile.domain.IndexedFile>, Boolean> {
        if (indexedWorkspace == root && mutableSnapshot.value.fileIndex.isNotEmpty()) {
            return mutableSnapshot.value.fileIndex to mutableSnapshot.value.fileIndexTruncated
        }
        val (index, truncated) = WorkspaceIndexer.indexFiles(root)
        indexedWorkspace = root
        mutableSnapshot.update { snapshot -> snapshot.copy(fileIndex = index, fileIndexTruncated = truncated) }
        return index to truncated
    }

    private suspend fun openDocument(location: String, lineNumber: Int? = null, column: Int? = null) {
        val existing = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location }
        if (existing != null) {
            selectDocument(location)
            recordRecentDocument(location)
            mutableEffects.emit(WorkbenchEffect.DocumentOpened(location, lineNumber, column))
            return
        }
        val root = activeRootLocation
        if (root == null || !runCatching {
                File(location).canonicalFile.toPath().startsWith(File(root).canonicalFile.toPath())
            }.getOrDefault(false)) {
            mutableEffects.emit(WorkbenchEffect.Message("The selected file is outside the active workspace"))
            return
        }
        runCatching {
            val document = source.readDocument(location)
            documentSources[document.location] = document
            savedDocumentTexts[document.location] = document.text
            val opened = OpenDocument(
                name = document.name,
                location = document.location,
                text = document.text,
                encoding = document.encoding,
                lineEnding = document.lineEnding,
                dirty = false,
                readOnly = document.readOnly,
            )
            mutableSnapshot.update { snapshot -> snapshot.copy(
                openDocuments = snapshot.openDocuments + opened,
                activeDocument = opened,
            ) }
            recordRecentDocument(document.location)
            mutableEffects.emit(WorkbenchEffect.DocumentOpened(location, lineNumber, column))
            draftMutex.withLock { draftStore.read(document.location) }?.let { draft ->
                if (draft.text == document.text) {
                    discardDraft(document.location)
                } else {
                    unresolvedRecoveryLocations += document.location
                    mutableSnapshot.update { state ->
                        val locked = state.openDocuments.firstOrNull { it.location == document.location }
                            ?.copy(readOnly = true) ?: return@update state
                        state.copy(
                            openDocuments = state.openDocuments.map { if (it.location == document.location) locked else it },
                            activeDocument = if (state.activeDocument?.location == document.location) locked else state.activeDocument,
                        )
                    }
                    mutableEffects.emit(WorkbenchEffect.ConfirmDraftRecovery(
                        location = document.location,
                        name = document.name,
                        sourceChanged = draft.sourceFingerprint != document.fingerprint,
                    ))
                }
            }
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to open document")))
        }
    }

    private suspend fun createFile(intent: WorkbenchIntent.CreateFile) {
        val root = activeRootLocation ?: run {
            reportWorkspaceItemFailure(intent.requestId, "Open a workspace before creating a file")
            return
        }
        if (intent.expectedWorkspace != null && intent.expectedWorkspace != root) {
            reportWorkspaceItemFailure(intent.requestId, "The active workspace changed. Choose the destination again")
            return
        }
        val name = intent.name.trim()
        WorkspaceNameRules.fileValidationError(name)?.let { error ->
            reportWorkspaceItemFailure(intent.requestId, error)
            return
        }
        val parent = intent.parentLocation ?: root
        if (!isWorkspaceCreationParent(root, parent)) {
            reportWorkspaceItemFailure(intent.requestId, "Choose a folder inside the active workspace")
            return
        }
        runCatching { source.createFile(parent, name) }.onSuccess { location ->
            refreshCreatedWorkspaceItem(root, parent, "File", updateIndex = true)
            if (intent.requestId != null) mutableEffects.emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
            openDocument(location)
        }.onFailure { error -> reportWorkspaceItemFailure(intent.requestId, error.userMessage("Unable to create file")) }
    }

    private suspend fun createWorkspaceFolder(intent: WorkbenchIntent.CreateWorkspaceFolder) {
        val root = activeRootLocation
        if (root == null || root != intent.expectedWorkspace) {
            reportWorkspaceItemFailure(intent.requestId, "The active workspace changed. Choose the destination again")
            return
        }
        val name = intent.name.trim()
        WorkspaceNameRules.workspaceFolderValidationError(name)?.let { error ->
            reportWorkspaceItemFailure(intent.requestId, error)
            return
        }
        if (!isWorkspaceCreationParent(root, intent.parentLocation)) {
            reportWorkspaceItemFailure(intent.requestId, "Choose a folder inside the active workspace")
            return
        }
        runCatching { source.createDirectory(intent.parentLocation, name) }.onSuccess { location ->
            refreshCreatedWorkspaceItem(root, intent.parentLocation, "Folder", updateIndex = false)
            mutableEffects.emit(WorkbenchEffect.WorkspaceItemCreated(intent.requestId, location))
        }.onFailure { error -> reportWorkspaceItemFailure(intent.requestId, error.userMessage("Unable to create folder")) }
    }

    private fun isWorkspaceCreationParent(root: String, parent: String): Boolean = runCatching {
        val rootPath = File(root).canonicalFile.toPath()
        val parentPath = File(parent).canonicalFile.toPath()
        parentPath.startsWith(rootPath) && !rootPath.relativize(parentPath).any { it.toString() == ".git" }
    }.getOrDefault(false)

    private suspend fun refreshCreatedWorkspaceItem(root: String, parent: String, kind: String, updateIndex: Boolean) {
        runCatching {
            childrenByParent[parent] = source.listChildren(parent)
            if (parent != root) expandedLocations += parent
            refreshWorkspaceState(root)
            if (updateIndex) {
                indexedWorkspace = null
                ensureFileIndex(root)
            }
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("$kind created, but the workspace could not refresh")))
        }
    }

    private suspend fun reportWorkspaceItemFailure(requestId: String?, message: String) {
        if (requestId == null) mutableEffects.emit(WorkbenchEffect.Message(message))
        else mutableEffects.emit(WorkbenchEffect.WorkspaceItemCreationFailed(requestId, message))
    }

    private suspend fun recordRecentDocument(location: String) {
        val root = activeRootLocation ?: return
        runCatching {
            store.touchRecentDocument(
                StoredRecentDocument(File(location).name, File(location).canonicalPath, root, System.currentTimeMillis()),
            )
            mutableSnapshot.update { it.copy(recentDocuments = recentDocumentSummaries(root)) }
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("File opened, but recent files could not be saved")))
        }
    }

    private fun recentDocumentSummaries(root: String): List<RecentDocument> = store.loadRecentDocuments(root)
        .mapNotNull { item ->
            val relative = runCatching { File(root).canonicalFile.toPath().relativize(File(item.location).canonicalFile.toPath()) }.getOrNull()
                ?: return@mapNotNull null
            if (relative.isAbsolute || relative.any { it.toString() == ".." || it.toString() == ".git" }) return@mapNotNull null
            RecentDocument(
                name = item.name,
                location = item.location,
                path = relative.toString(),
                lastOpened = relativeTime(item.lastOpenedAt),
                available = File(item.location).isFile,
            )
        }

    private fun updateDocumentText(intent: WorkbenchIntent.UpdateDocumentText) {
        mutableSnapshot.update { state ->
            val document = state.openDocuments.firstOrNull { it.location == intent.location }
                ?: return@update state
            if (document.readOnly) return@update state
            val changed = document.copy(
                text = intent.text,
                dirty = intent.text != savedDocumentTexts[intent.location],
            )
            state.copy(
                openDocuments = state.openDocuments.map { if (it.location == intent.location) changed else it },
                activeDocument = if (state.activeDocument?.location == intent.location) changed else state.activeDocument,
            )
        }
        queueDraftSync(intent.location)
    }

    private fun queueDraftSync(location: String) {
        scope.launch {
            runCatching {
                draftMutex.withLock {
                    val document = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location }
                        ?: return@withLock
                    val sourceDocument = documentSources[location] ?: return@withLock
                    if (document.dirty) {
                        if (lastDraftText[location] != document.text) {
                            draftStore.write(location, document.name, document.text, sourceDocument.fingerprint)
                            lastDraftText[location] = document.text
                        }
                    } else {
                        draftStore.delete(location)
                        lastDraftText.remove(location)
                    }
                }
            }.onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to keep a recovery draft")))
            }
        }
    }

    private suspend fun restoreDraft(location: String) {
        val draft = draftMutex.withLock { draftStore.read(location) } ?: return
        val document = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location } ?: return
        if (document.dirty) {
            mutableEffects.emit(WorkbenchEffect.Message("This document has newer edits. Save or discard them before restoring a draft"))
            return
        }
        val sourceDocument = documentSources[location] ?: return
        if (draft.text == document.text) {
            discardDraft(location)
            return
        }
        if (draft.sourceFingerprint != sourceDocument.fingerprint) recoveredExternalChanges += location
        unresolvedRecoveryLocations.remove(location)
        mutableSnapshot.update { state ->
            val restored = state.openDocuments.firstOrNull { it.location == location }
                ?.copy(text = draft.text, dirty = true, readOnly = sourceDocument.readOnly) ?: return@update state
            state.copy(
                openDocuments = state.openDocuments.map { if (it.location == location) restored else it },
                activeDocument = if (state.activeDocument?.location == location) restored else state.activeDocument,
            )
        }
        queueDraftSync(location)
    }

    private suspend fun discardDraft(location: String) {
        draftMutex.withLock {
            draftStore.delete(location)
            lastDraftText.remove(location)
        }
        unresolvedRecoveryLocations.remove(location)
    }

    private suspend fun saveDocument(
        location: String,
        automatic: Boolean = false,
        expectedText: String? = null,
        expectedWorkspace: String? = null,
        allowRecoveredOverwrite: Boolean = false,
    ): Boolean {
        val sourceDocument = documentSources[location]
        val openDocument = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location }
        if (automatic && (expectedWorkspace != activeRootLocation ||
                !mutableSnapshot.value.isCurrentAutoSaveRequest(location, expectedWorkspace, expectedText))) return false
        if (sourceDocument == null || openDocument == null) {
            mutableEffects.emit(WorkbenchEffect.Message("The document is no longer open"))
            return false
        }
        if (!openDocument.dirty && location in unresolvedRecoveryLocations) {
            draftMutex.withLock { draftStore.read(location) }?.let { draft ->
                mutableEffects.emit(WorkbenchEffect.ConfirmDraftRecovery(
                    location,
                    openDocument.name,
                    draft.sourceFingerprint != sourceDocument.fingerprint,
                ))
                return false
            }
            unresolvedRecoveryLocations.remove(location)
        }
        if (location in recoveredExternalChanges && !allowRecoveredOverwrite) {
            if (!automatic) mutableEffects.emit(WorkbenchEffect.ConfirmRecoveredOverwrite(location, openDocument.name))
            return false
        }
        val textBeingSaved = openDocument.text
        val saved = runCatching {
            documentSources[location] = source.writeDocument(sourceDocument, textBeingSaved)
            recoveredExternalChanges.remove(location)
            savedDocumentTexts[location] = textBeingSaved
            mutableSnapshot.update { state ->
                val current = state.openDocuments.firstOrNull { it.location == location } ?: return@update state
                val saved = current.copy(dirty = current.text != textBeingSaved)
                state.copy(
                    openDocuments = state.openDocuments.map { if (it.location == location) saved else it },
                    activeDocument = if (state.activeDocument?.location == location) saved else state.activeDocument,
                )
            }
            // Saving changes the worktree, so Source Control and the Explorer
            // decorations are stale the moment the write lands.
            scheduleGitRefresh()
            true
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to save document")))
        }.getOrDefault(false)
        if (!saved) return false
        runCatching {
            draftMutex.withLock {
                val latest = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location }
                if (latest?.dirty == true) {
                    val refreshedSource = documentSources[location]
                    if (refreshedSource != null) {
                        draftStore.write(location, latest.name, latest.text, refreshedSource.fingerprint)
                        lastDraftText[location] = latest.text
                    }
                } else {
                    draftStore.delete(location)
                    lastDraftText.remove(location)
                }
            }
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("File saved, but recovery cache could not update")))
        }
        mutableEffects.emit(WorkbenchEffect.DocumentSaved(location, automatic))
        return true
    }

    private fun selectDocument(location: String) {
        mutableSnapshot.update { state ->
            val selected = state.openDocuments.firstOrNull { it.location == location }
            if (selected == null) state else state.copy(activeDocument = selected)
        }
    }

    private fun reorderDocument(movingLocation: String, targetLocation: String) {
        mutableSnapshot.update { state ->
            state.copy(openDocuments = reorderDocuments(state.openDocuments, movingLocation, targetLocation))
        }
    }

    private suspend fun closeDocument(location: String, saveChanges: Boolean?) {
        val document = mutableSnapshot.value.openDocuments.firstOrNull { it.location == location } ?: return
        if (document.dirty && saveChanges == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Choose Save, Discard or Cancel before closing ${document.name}"))
            return
        }
        if (document.dirty && saveChanges == true && !saveDocument(location)) return
        mutableSnapshot.update { state ->
            val nextLocation = selectedAfterClose(state.openDocuments, state.activeDocument?.location, location)
            val remaining = state.openDocuments.filterNot { it.location == location }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == nextLocation })
        }
        documentSources.remove(location)
        savedDocumentTexts.remove(location)
        recoveredExternalChanges.remove(location)
        if (location in unresolvedRecoveryLocations && !document.dirty) {
            unresolvedRecoveryLocations.remove(location)
        } else discardDraft(location)
    }

    private suspend fun closeOtherDocuments(keepLocation: String, saveChanges: Boolean?) {
        val others = mutableSnapshot.value.openDocuments.filterNot { it.location == keepLocation }
        if (others.any { it.dirty } && saveChanges == null) {
            mutableEffects.emit(WorkbenchEffect.Message("Choose Save, Discard or Cancel before closing dirty tabs"))
            return
        }
        if (saveChanges == true) {
            for (document in others.filter { it.dirty }) if (!saveDocument(document.location)) return
        }
        others.forEach { document ->
            documentSources.remove(document.location)
            savedDocumentTexts.remove(document.location)
            recoveredExternalChanges.remove(document.location)
        }
        draftMutex.withLock {
            others.forEach { document ->
                if (document.location !in unresolvedRecoveryLocations || document.dirty) {
                    draftStore.delete(document.location)
                    lastDraftText.remove(document.location)
                }
            }
        }
        others.forEach { unresolvedRecoveryLocations.remove(it.location) }
        mutableSnapshot.update { state ->
            val kept = state.openDocuments.firstOrNull { it.location == keepLocation }
            state.copy(openDocuments = listOfNotNull(kept), activeDocument = kept)
        }
    }

    private suspend fun closeSavedDocuments() {
        val removed = mutableSnapshot.value.openDocuments.filterNot { it.dirty }
        removed.forEach { document ->
            documentSources.remove(document.location)
            savedDocumentTexts.remove(document.location)
            recoveredExternalChanges.remove(document.location)
        }
        draftMutex.withLock {
            removed.forEach { document ->
                if (document.location !in unresolvedRecoveryLocations) {
                    draftStore.delete(document.location)
                    lastDraftText.remove(document.location)
                }
            }
        }
        removed.forEach { unresolvedRecoveryLocations.remove(it.location) }
        mutableSnapshot.update { state ->
            val remaining = state.openDocuments.filter { it.dirty }
            state.copy(openDocuments = remaining, activeDocument = remaining.firstOrNull { it.location == state.activeDocument?.location } ?: remaining.firstOrNull())
        }
    }

    private suspend fun toggleFolder(location: String) {
        val root = activeRootLocation ?: return
        if (!expandedLocations.remove(location)) {
            runCatching {
                if (location !in childrenByParent) childrenByParent[location] = source.listChildren(location)
                expandedLocations += location
            }.onFailure { error ->
                mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to read folder")))
            }
        }
        mutableSnapshot.update { snapshot -> snapshot.copy(
            files = decorateFiles(
                projectVisibleFiles(root, childrenByParent, expandedLocations),
                GitWorkspaceSnapshot(mutableSnapshot.value.gitRepository, mutableSnapshot.value.gitChanges),
            ),
        ) }
    }

    private suspend fun refreshActiveWorkspace() {
        val root = activeRootLocation
        if (root == null) {
            refreshRecents()
            return
        }
        runCatching {
            childrenByParent[root] = source.listChildren(root)
            expandedLocations.toList().forEach { location ->
                childrenByParent[location] = source.listChildren(location)
            }
            refreshWorkspaceState(root)
            ensureFileIndex(root)
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to refresh workspace")))
        }
    }

    private fun refreshRecents() {
        // Computed before the update so the atomic section stays short and no
        // concurrent writer's fields are lost.
        val recents = recentSummaries()
        val storage = runCatching(source::storageSummary).getOrNull()
        mutableSnapshot.update { it.copy(workspaces = recents, storage = storage) }
    }

    private fun refreshStorage() {
        storageRefreshJob?.cancel()
        storageRefreshJob = scope.launch(Dispatchers.IO) {
            val device = runCatching(source::storageSummary).getOrNull()
            val workspaces = store.load()
            val details = device?.let { summary -> StorageDetails(
                device = summary,
                runtime = StorageInspector.measure(runtimeLayout.runtimeRoot, maxEntries = 400_000),
                cache = StorageInspector.measure(appContext.cacheDir, maxEntries = 50_000),
                workspaceSizes = workspaces.associate { item ->
                    item.location to StorageInspector.measure(File(item.location), maxEntries = 25_000)
                },
            ) }
            currentCoroutineContext().ensureActive()
            mutableSnapshot.update { it.copy(storage = device, storageDetails = details) }
        }
    }

    private suspend fun clearAppCache() {
        if (terminals.hasRunningSessions() || languageProcesses.hasActiveProcesses() || runtimeInstallJob?.isActive == true ||
            mutableSnapshot.value.gitOperation is GitOperationState.Running) {
            mutableEffects.emit(WorkbenchEffect.Message("Finish runtime, Git, terminal and language service tasks before clearing cache"))
            return
        }
        runCatching {
            StorageInspector.clearRuntimeCache(appContext.cacheDir, runtimeLayout.prootTemp)
        }.onSuccess { removed ->
            mutableEffects.emit(WorkbenchEffect.Message(
                if (removed == 0) "Runtime cache is already clear" else "Removed $removed cached runtime items",
            ))
            refreshStorage()
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to clear runtime cache")))
        }
    }

    private suspend fun removeStaleRecents() {
        if (!Environment.isExternalStorageManager() || Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) {
            mutableEffects.emit(WorkbenchEffect.Message("Storage access is needed before checking recent workspace paths"))
            return
        }
        runCatching {
            val missing = store.load().filter { !File(it.location).exists() }
            missing.forEach { store.remove(it.location) }
            refreshRecents()
            refreshStorage()
            missing.size
        }.onSuccess { removed ->
            mutableEffects.emit(WorkbenchEffect.Message(
                if (removed == 0) "No stale recent workspaces found"
                else "Removed $removed stale recent shortcuts; no workspace files were deleted",
            ))
        }.onFailure { error ->
            mutableEffects.emit(WorkbenchEffect.Message(error.userMessage("Unable to remove stale recents")))
        }
    }

    private fun recentSummaries(): List<WorkspaceSummary> {
        return store.load().map { workspace ->
            // Reading .git/HEAD keeps the launcher instant; starting a Git
            // process for every recent workspace would cost a PRoot launch each.
            val branch = if (Environment.isExternalStorageManager() && source.isAvailable(workspace.location)) {
                runCatching { GitHeadReader.branchOf(workspace.location) }.getOrNull()
            } else {
                null
            }
            WorkspaceSummary(
                name = workspace.name,
                path = workspace.displayPath,
                branch = branch,
                lastOpened = relativeTime(workspace.lastOpenedAt),
                available = Environment.isExternalStorageManager() && source.isAvailable(workspace.location),
                location = workspace.location,
            )
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
            mutationMutex.withLock { refreshWorkspaceState(root) }
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
        val recents = recentSummaries()
        val files = decorateFiles(projectVisibleFiles(root, childrenByParent, expandedLocations), gitState)
        mutableSnapshot.update {
            it.copy(
                workspaces = recents,
                files = files,
                gitChanges = gitState.changes,
                gitRepository = gitState.repository,
                gitAvailable = available,
                activeBranch = gitState.repository?.branch
                    ?: GitHeadReader.branchOf(root).orEmpty(),
                recentDocuments = recentDocumentSummaries(root),
            )
        }
    }

    private fun decorateFiles(files: List<dev.icode.mobile.domain.FileEntry>, gitState: GitWorkspaceSnapshot) =
        gitState.repository?.let { repository ->
            val statuses = gitState.changes.associate { change ->
                File(repository.workTreePath, change.path).canonicalPath to change.status
            }
            GitTreeStatus.decorate(files, statuses)
        } ?: files

    private fun relativeTime(timestamp: Long): String {
        val elapsed = (System.currentTimeMillis() - timestamp).coerceAtLeast(0)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsed)
        val days = TimeUnit.MILLISECONDS.toDays(elapsed)
        return when {
            minutes < 1 -> "now"
            hours < 1 -> "${minutes}m ago"
            days < 1 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }

    private fun Throwable.userMessage(fallback: String): String =
        message?.takeIf { it.isNotBlank() } ?: fallback

    private companion object {
        /** Long enough to absorb a burst of saves, short enough to feel immediate. */
        const val GIT_REFRESH_DEBOUNCE_MILLIS = 350L

        fun emptySnapshot() = WorkbenchSnapshot(
            workspaces = emptyList(),
            files = emptyList(),
            gitChanges = emptyList(),
            diagnostics = emptyList(),
            languageServices = emptyList(),
            activeWorkspace = "No workspace",
            activePath = "Open a folder to begin",
            activeBranch = "",
            runtimeHealthy = true,
        )
    }
}
