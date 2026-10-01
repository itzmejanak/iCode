package dev.icode.mobile.data.workspace

import android.content.Context
import com.termux.terminal.TerminalSession
import dev.icode.mobile.data.assistant.AssistantCoordinator
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.git.ConflictFiles
import dev.icode.mobile.data.git.GitAuthCoordinator
import dev.icode.mobile.data.git.GitAuthenticator
import dev.icode.mobile.data.git.GitConflictCoordinator
import dev.icode.mobile.data.git.GitCredentialStore
import dev.icode.mobile.data.git.GitRepositoryClient
import dev.icode.mobile.data.git.GitTransferCoordinator
import dev.icode.mobile.data.git.GitTransferLedger
import dev.icode.mobile.data.git.GitWorkspaceCoordinator
import dev.icode.mobile.data.language.LanguageServiceCoordinator
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.runtime.RuntimeComponentsCoordinator
import dev.icode.mobile.data.runtime.RuntimeCoordinator
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.data.runtime.TerminalCoordinator
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.search.RipgrepService
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.tasks.BackgroundWork
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.BackgroundWorkRules
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** The workbench's backend: one per process ([WorkbenchProcess]), so its work outlives every screen. */
class AndroidWorkbenchGateway internal constructor(context: Context) : WorkbenchGateway {
    private val workbench = WorkbenchStateHolder()
    private val appContext = context.applicationContext
    private val store: WorkspaceStore = SharedPreferencesWorkspaceStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeLayout = RuntimeLayout(appContext)
    private val execution = RuntimeExecution(runtimeLayout, scope) { list -> workbench.update { it.copy(processes = list) } }
    private val source = WorkspaceFileSource(runtimeLayout.workspaces)
    private val settings = AndroidWorkspacePreferencesRepository(appContext)
    /** The runtime's components, one apt run at a time: Git, the SSH client and search install through it. */
    private val components = RuntimeComponentsCoordinator(workbench, scope, RuntimePackageInstaller(runtimeLayout, scope), runtimeLayout.componentsOffer)
    private val gitCli = CliGitService(runtimeLayout, components.installer)
    private val git = GitRepositoryClient(gitCli)
    private val authenticator = GitAuthenticator(runtimeLayout, gitCli, components.installer, GitCredentialStore(appContext))
    private val terminals = TerminalSessionManager.get(appContext)
    private val terminal: TerminalCoordinator = TerminalCoordinator(
        workbench,
        terminals,
        runtimeInstalled = { runtimeLayout.isInstalled },
        activeRoot = { session.activeRoot },
        openWorkspaceTerminal = { location -> session.open(location, persist = true, openTerminal = true) },
        workspaceChanged = { session.requestRefresh(rebuildIndex = true) },
    )
    private val notices = NoticeStore.get(appContext)
    /** Something runs in the runtime that rebuilding it, or clearing the cache PRoot works in, would break. */
    private val runtimeBusy = { terminals.hasRunningSessions() || languages.hasActiveProcesses() || execution.processes.anyRunning() }
    private val languages: LanguageServiceCoordinator = LanguageServiceCoordinator(appContext, workbench, scope, runtimeLayout, { session.activeRoot }) { execution.environment.current().variables }
    private val runtime: RuntimeCoordinator = RuntimeCoordinator(
        appContext, workbench, scope, runtimeLayout, AndroidSystemAccess(appContext),
        sessionsRunning = runtimeBusy,
    )
    private val library: WorkspaceLibraryCoordinator = WorkspaceLibraryCoordinator(
        appContext, workbench, scope, source, store, runtimeLayout,
        branchOf = git::branchOf,
        tasksRunning = { runtimeBusy() || runtime.installing || workbench.current.gitOperation is GitOperationState.Running },
    )
    private val tree = WorkspaceTree(workbench, source)
    private val gitWorkspace: GitWorkspaceCoordinator = GitWorkspaceCoordinator(
        workbench,
        scope,
        git,
        tree,
        activeRoot = { session.activeRoot },
        workspaces = library::workspaces,
        recentDocuments = library::recentDocuments,
        onInstalled = { gitAuth.refresh() },
    )
    private val transferLedger = GitTransferLedger(appContext)
    /** Read before any transfer can start, or it would report the one just started. */
    private val interruptedTransfer = transferLedger.takeInterrupted()
    private val gitConflicts: GitConflictCoordinator = GitConflictCoordinator(
        workbench,
        object : ConflictFiles {
            override fun read(location: String) = source.readDocument(location)
            override fun write(document: WorkspaceDocument, text: String) = source.writeDocument(document, text)
            override fun hasUnsavedEdits(location: String) =
                workbench.current.openDocuments.any { it.location == location && it.dirty }
            override suspend fun written(location: String) = documents.reconcileWithDisk()
        },
        activeRoot = { session.activeRoot },
        act = gitWorkspace::act,
        stage = { workspace, path -> git.markResolved(workspace, path) },
        refresh = { gitWorkspace.refreshActive() },
    )
    private val gitTransfers: GitTransferCoordinator = GitTransferCoordinator(
        workbench,
        scope,
        git,
        runtimeLayout.workspaces,
        activeRoot = { session.activeRoot },
        withCredentials = { block -> authenticator.withHttpsCredentials(block) },
        // A transfer runs outside the lock; what it changes afterwards takes it.
        refresh = { workspace -> workbench.mutations.withLock { gitWorkspace.refresh(workspace) } },
        openClone = { location -> workbench.mutations.withLock { lifecycle.reveal(location) } },
        commit = { message -> workbench.mutations.withLock { gitWorkspace.commit(message, quiet = true) } },
        ledger = transferLedger,
    )
    private val gitAuth: GitAuthCoordinator = GitAuthCoordinator(
        workbench,
        git,
        authenticator,
        activeRoot = { session.activeRoot },
        actInRepository = gitWorkspace::act,
        refreshRepository = { root -> gitWorkspace.refresh(root) },
    )
    /** Long operations: their own jobs, off the mutation lock (docs/long-operations-plan.md). */
    private val tasks = TaskRunner(workbench, scope)
    /** One ripgrep, for Search and the assistant: one install, one cooldown after a failed one. */
    private val ripgrep = RipgrepService(runtimeLayout, components.installer)
    private val search: SearchCoordinator = SearchCoordinator(workbench, scope, ripgrep) { session.activeRoot }
    private val recovery: DocumentRecoveryCoordinator =
        DocumentRecoveryCoordinator(workbench, scope, DocumentDraftStore(File(appContext.filesDir, "document-drafts"))) { location -> documents.sourceOf(location) }
    private val documents: DocumentSessionCoordinator = DocumentSessionCoordinator(
        workbench, source, recovery, library, activeRoot = { session.activeRoot }, onSaved = { gitWorkspace.scheduleRefresh() },
    )
    private val session: WorkspaceSessionCoordinator = WorkspaceSessionCoordinator(
        workbench, scope, source, tree, library, documents, recovery, gitWorkspace, search, terminal, languages,
    )
    /** What may change now, asked the same way by the lifecycle and the Explorer's items. */
    private val usage = WorkspaceUsage(workbench, tasks, terminal::runsIn, execution.processes::runsIn, documents::blockingEdits) { session.activeRoot }
    private val lifecycle = WorkspaceLifecycleCoordinator(
        workbench, tasks, source, library, session, usage, documents::open,
        moveSettings = { from, to -> settings.moveWorkspace(from, to).also { assistant.workspaceMoved(from, to); execution.processes.workspaceMoved(from, to) } },
        forgetSettings = { location -> settings.forgetWorkspace(location).also { assistant.workspaceForgotten(location); execution.processes.workspaceForgotten(location) } },
    )
    private val items = WorkspaceItemsCoordinator(
        workbench, tasks, source, tree, documents, library, usage, lifecycle,
        activeRoot = { session.activeRoot },
        refreshWorkspaceState = { root -> gitWorkspace.refresh(root) },
        reindex = { root -> search.workspaceChanged(root) },
    )
    private val importer = WorkspaceImportCoordinator(
        workbench, tasks, runtimeLayout.workspaces, git,
        PhoneStorage.of(),
        reserveSpace = runtimeLayout::reserveSpace,
        freeBytes = runtimeLayout::allocatableBytes,
        open = lifecycle::reveal,
    )
    /** iCode's assistant (docs/assistant-koog-plan.md): its turns are its own jobs, its tools iCode's owners. */
    private val assistant = AssistantCoordinator.create(
        appContext, workbench, scope,
        AssistantToolbox(source, ripgrep, gitCli, execution, runtimeLayout::isInstalled, { documents.blockingEdits(it) != null }) { session.requestRefresh(rebuildIndex = true) },
        activeRoot = { session.activeRoot },
        source = source,
    )

    override val snapshot: StateFlow<WorkbenchSnapshot> = workbench.snapshot
    override val effects: SharedFlow<WorkbenchEffect> = workbench.effects

    init {
        languages.publish()
        scope.launch {
            WorkspaceStaging(runtimeLayout.workspaces.root).sweep()
            library.refresh()
            session.start()
        }
        scope.launch { recovery.loadRecoverable() }
        scope.launch { observeNotices() }
        terminal.follow(scope, settings.values(null).filter { it.loaded && it.loadError == null }.map { it.terminal })
        scope.launch { runtime.reportState() }
        components.start()
        BackgroundWork.follow(appContext, scope, workbench.snapshot.map(BackgroundWorkRules::of), ::dispatch)
        importer.followAccess(scope)
        assistant.start()
    }

    /** The live session behind a terminal tab, for the view that draws it. Main thread. */
    fun terminalSession(id: String): TerminalSession? = terminals.session(id)

    /** Reports screen changes of a terminal session until the handle is closed. Main thread. */
    fun observeTerminal(id: String, onScreenChanged: () -> Unit): AutoCloseable = terminals.observe(id, onScreenChanged)

    /** The Notifications page; first, once per process, what the previous process's end took with it. */
    private suspend fun observeNotices() {
        terminals.ledger.takePrevious()?.let(notices::add)
        interruptedTransfer?.let(notices::add)
        notices.notices.collect { list -> workbench.update { it.copy(notices = list) } }
    }

    override fun dispatch(intent: WorkbenchIntent) {
        when (intent) {
            // What must not wait on the mutation lock. apt, ssh-keygen and the network do not change the workspace.
            is WorkbenchIntent.SelectLanguageService -> languages.select(intent.id)
            WorkbenchIntent.ProvisionGit -> scope.launch { gitWorkspace.provision() }
            WorkbenchIntent.GenerateSshKey -> scope.launch { gitAuth.generateSshKey() }
            is WorkbenchIntent.TestGitConnection -> scope.launch { gitAuth.testConnection(intent.url) }
            // A task's dialog must answer while the task runs.
            is WorkbenchIntent.TaskIntent -> tasks.handle(intent)
            is WorkbenchIntent.ComponentIntent -> components.handle(intent)
            WorkbenchIntent.Refresh -> session.requestRefresh(rebuildIndex = true)
            is WorkbenchIntent.ScreenVisible -> session.screenVisible(intent.visible)
            is WorkbenchIntent.StorageIntent -> library.read(intent)
            // Reading the runtime's own files changes nothing, so it does not wait
            // on the mutation lock behind whatever the workspace is doing.
            WorkbenchIntent.RefreshRuntimeDetails -> runtime.refreshDetails()
            is WorkbenchIntent.UpdateDocumentText -> documents.updateText(intent)
            // Folder reads and expansion state belong to WorkspaceTree; they do
            // not wait behind an unrelated Git refresh on the mutation lock.
            is WorkbenchIntent.ToggleFolder -> session.activeRoot?.let { root -> scope.launch { tree.toggle(root, intent.path) } }
            // Search owns cancellable jobs. It must never wait behind a workspace
            // mutation, and a new query must be able to cancel the old process.
            WorkbenchIntent.PrepareWorkspaceSearch -> session.activeRoot?.let(search::prepareIndex)
            is WorkbenchIntent.RunContentSearch -> search.runContentSearch(intent.query, intent.matchCase, intent.wholeWord, intent.regex)
            WorkbenchIntent.ClearContentSearch -> search.clear()
            // A transfer is its own job: nothing waits behind a slow remote.
            is WorkbenchIntent.TransferIntent -> gitTransfers.handle(intent)
            is WorkbenchIntent.AssistantIntent -> assistant.handle(intent)
            is WorkbenchIntent.ProcessIntent -> execution.processes.handle(intent)
            else -> scope.launch {
                workbench.mutations.withLock {
                    when (intent) {
                        is WorkbenchIntent.RequestSystemPermission ->
                            workbench.emit(WorkbenchEffect.RequestSystemPermission(intent.permission))
                        // Android's prompt closed: whatever the answer, the permission has been offered.
                        is WorkbenchIntent.SystemPermissionAnswered ->
                            workbench.emit(WorkbenchEffect.SystemPermissionResolved(intent.permission, runtime.permissionAnswered(intent.permission)))
                        is WorkbenchIntent.DeclineSystemPermission -> runtime.declinePermission(intent.permission)
                        is WorkbenchIntent.OpenSystemSettings ->
                            workbench.emit(WorkbenchEffect.OpenSystemSettings(intent.permission))
                        WorkbenchIntent.RefreshSystemAccess -> runtime.refreshSystemAccess()
                        WorkbenchIntent.MarkNoticesRead -> notices.markAllRead()
                        is WorkbenchIntent.DeleteNotice -> notices.delete(intent.id)
                        WorkbenchIntent.ClearNotices -> notices.clear()
                        is WorkbenchIntent.CreateWorkspace -> lifecycle.create(intent)
                        is WorkbenchIntent.RenameWorkspace -> lifecycle.rename(intent)
                        is WorkbenchIntent.DeleteWorkspace -> lifecycle.delete(intent.location)
                        is WorkbenchIntent.OpenImportBrowser -> importer.openBrowser(intent.path)
                        is WorkbenchIntent.BrowseImportFolder -> importer.browse(intent.path)
                        is WorkbenchIntent.ChooseImportFolder -> importer.choose(intent.path)
                        is WorkbenchIntent.ImportWorkspace -> importer.import(intent.name, intent.into)
                        is WorkbenchIntent.OpenWorkspace -> session.open(intent.path, persist = true)
                        is WorkbenchIntent.OpenWorkspaceTerminal -> session.open(intent.path, persist = true, openTerminal = true)
                        is WorkbenchIntent.ResolveWorkspaceSwitch -> session.resolveSwitch(intent)
                        is WorkbenchIntent.OpenFile -> lifecycle.reveal(intent.path, intent.lineNumber, intent.column)
                        is WorkbenchIntent.CreateFile -> items.createFile(intent)
                        is WorkbenchIntent.CreateWorkspaceFolder -> items.createFolder(intent)
                        is WorkbenchIntent.RenameWorkspaceItem -> items.rename(intent)
                        is WorkbenchIntent.ClipboardIntent -> items.clipboard(intent)
                        is WorkbenchIntent.DeleteWorkspaceItem -> items.delete(intent)
                        is WorkbenchIntent.SaveDocument ->
                            documents.save(intent.location, intent.automatic, intent.expectedText, intent.expectedWorkspace)
                        is WorkbenchIntent.RestoreDraft -> recovery.restore(intent.location)
                        is WorkbenchIntent.OpenRecoverableDraft -> session.openRecoverableDraft(intent.location)
                        is WorkbenchIntent.DiscardDraft -> recovery.keepDiskVersion(intent.location)
                        is WorkbenchIntent.ConfirmRecoveredOverwrite -> documents.save(intent.location, allowRecoveredOverwrite = true)
                        is WorkbenchIntent.ReloadFromDisk -> documents.reloadFromDisk(intent.location)
                        is WorkbenchIntent.OverwriteDisk -> documents.overwriteDisk(intent.location)
                        is WorkbenchIntent.CompareWithDisk -> session.compareWithDisk(intent.location)
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
                        WorkbenchIntent.RefreshGit -> gitWorkspace.refreshActive()
                        is WorkbenchIntent.ChooseGitRepository -> gitWorkspace.choose(intent.location)
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
                        is WorkbenchIntent.UpdateCloneForm ->
                            workbench.update { it.copy(cloneUrl = intent.url, cloneName = intent.name, cloneBranch = intent.branch) }
                        is WorkbenchIntent.SelectGitProvider -> gitAuth.selectProvider(intent.provider)
                        is WorkbenchIntent.SelectGitAuthMethod -> gitAuth.selectMethod(intent.method)
                        is WorkbenchIntent.SaveGitToken -> gitAuth.saveToken(intent.host, intent.username, intent.token)
                        WorkbenchIntent.ClearGitToken -> gitAuth.clearToken()
                        WorkbenchIntent.RefreshGitAuth -> gitAuth.refresh()
                        is WorkbenchIntent.SaveLanguageService -> languages.save(intent)
                        is WorkbenchIntent.RemoveLanguageService -> languages.remove(intent.id)
                        is WorkbenchIntent.StartLanguageService -> languages.start(intent.id)
                        is WorkbenchIntent.StopLanguageService -> languages.stop(intent.id)
                        is WorkbenchIntent.RestartLanguageService -> languages.restart(intent.id)
                        WorkbenchIntent.InstallRuntime -> runtime.install(RuntimeInstallReason.Initial)
                        WorkbenchIntent.ClearAppCache -> library.clearCache()
                        // Repair keeps the home directory, reset does not; both go
                        // through the one installer rather than a second path.
                        WorkbenchIntent.RepairRuntime ->
                            if (runtime.install(RuntimeInstallReason.Repair)) workbench.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        WorkbenchIntent.ResetRuntime ->
                            if (runtime.install(RuntimeInstallReason.Reset)) workbench.emit(WorkbenchEffect.RuntimeRebuildStarted)
                        WorkbenchIntent.OpenTerminalSession -> terminal.open()
                        WorkbenchIntent.EnsureTerminalSession -> terminal.ensure()
                        is WorkbenchIntent.SelectTerminalSession -> terminal.select(intent.id)
                        is WorkbenchIntent.ShowTerminalSession -> terminal.show(intent.id)
                        is WorkbenchIntent.ReorderTerminalSession -> terminal.reorder(intent.movingId, intent.targetId)
                        is WorkbenchIntent.CloseTerminalSession -> terminal.close(intent.id)
                        is WorkbenchIntent.OpenExternalUrl -> workbench.emit(WorkbenchEffect.OpenExternalUrl(intent.url))
                    }
                }
            }
        }
    }

    /**
     * No screen is left: what serves one stops — the folder watcher and language servers. Work
     * goes on (terminal sessions, tasks, Git transfers, installs), kept alive by
     * RuntimeForegroundService, and the next screen finds it as it is.
     */
    internal fun screensGone() {
        session.stopWatching()
        languages.stopAll()
    }
}
