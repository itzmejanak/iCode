package dev.icode.mobile.data.workspace

import android.content.Context
import androidx.core.net.toUri
import com.termux.terminal.TerminalSession
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
import dev.icode.mobile.data.runtime.RuntimeCoordinator
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.data.runtime.TerminalCoordinator
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.search.RipgrepService
import dev.icode.mobile.data.search.SearchCoordinator
import dev.icode.mobile.data.settings.AndroidWorkspacePreferencesRepository
import dev.icode.mobile.data.storage.DocumentTreeImport
import dev.icode.mobile.data.storage.WorkspacesProvider
import dev.icode.mobile.data.system.AndroidSystemAccess
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkbenchGateway
import dev.icode.mobile.domain.WorkbenchIntent
import dev.icode.mobile.domain.WorkbenchSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

class AndroidWorkbenchGateway(context: Context) : WorkbenchGateway {
    private val workbench = WorkbenchStateHolder()
    private val appContext = context.applicationContext
    private val store: WorkspaceStore = SharedPreferencesWorkspaceStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeLayout = RuntimeLayout(appContext)
    private val source = WorkspaceFileSource(runtimeLayout.workspaces)
    private val settings = AndroidWorkspacePreferencesRepository(appContext)
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
        activeRoot = { session.activeRoot },
        openWorkspaceTerminal = { location -> session.open(location, persist = true, openTerminal = true) },
        workspaceChanged = { session.requestRefresh(rebuildIndex = true) },
    )
    private val notices = NoticeStore.get(appContext)
    private val languages: LanguageServiceCoordinator = LanguageServiceCoordinator(appContext, workbench, scope, runtimeLayout, activeRoot = { session.activeRoot })
    private val runtime: RuntimeCoordinator = RuntimeCoordinator(
        appContext, workbench, scope, runtimeLayout, AndroidSystemAccess(appContext),
        sessionsRunning = { terminals.hasRunningSessions() || languages.hasActiveProcesses() },
    )
    private val library: WorkspaceLibraryCoordinator = WorkspaceLibraryCoordinator(
        appContext, workbench, scope, source, store, runtimeLayout,
        branchOf = git::branchOf,
        tasksRunning = {
            terminals.hasRunningSessions() || languages.hasActiveProcesses() || runtime.installing ||
                workbench.current.gitOperation is GitOperationState.Running
        },
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
        openClone = { location -> workbench.mutations.withLock { session.open(location, persist = true) } },
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
    private val search: SearchCoordinator = SearchCoordinator(workbench, scope, RipgrepService(runtimeLayout, packages)) { session.activeRoot }
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
        activeRoot = { session.activeRoot },
        onSaved = { gitWorkspace.scheduleRefresh() },
    )
    private val items: WorkspaceItemsCoordinator = WorkspaceItemsCoordinator(
        workbench,
        tasks,
        source,
        tree,
        documents,
        library,
        activeRoot = { session.activeRoot },
        refreshWorkspaceState = { root -> gitWorkspace.refresh(root) },
        reindex = { root -> search.workspaceChanged(root) },
    )

    private val session: WorkspaceSessionCoordinator = WorkspaceSessionCoordinator(
        workbench, scope, source, tree, library, documents, recovery, gitWorkspace, search, terminal, languages,
    )
    private val lifecycle = WorkspaceLifecycleCoordinator(
        workbench, tasks, source, library, session, terminal,
        moveSettings = settings::moveWorkspace,
        forgetSettings = settings::forgetWorkspace,
    )
    private val importer = WorkspaceImportCoordinator(
        workbench, tasks, runtimeLayout.workspaces, git,
        openTree = { uri -> DocumentTreeImport(appContext.contentResolver, uri.toUri()) },
        isOwnTree = { uri -> uri.toUri().authority == WorkspacesProvider.authorityOf(appContext) },
        reserveSpace = runtimeLayout::reserveSpace,
        freeBytes = runtimeLayout::allocatableBytes,
        open = { location -> session.open(location, persist = true) },
    )

    override val snapshot: StateFlow<WorkbenchSnapshot> = workbench.snapshot
    override val effects: SharedFlow<WorkbenchEffect> = workbench.effects

    init {
        languages.publish()
        scope.launch {
            // The install is brought to this layout before anything reads a workspace.
            WorkspaceStorageMigration(appContext, runtimeLayout.workspaces, runtimeLayout.rootfs, store, notices, settings::forgetWorkspace).run()
            library.refresh()
            session.start()
        }
        scope.launch { recovery.loadRecoverable() }
        scope.launch { observeNotices() }
        terminal.follow(scope, settings.values(null).filter { it.loaded && it.loadError == null }.map { it.terminal })
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
        when (intent) {
            // What must not wait on the mutation lock.
            is WorkbenchIntent.SelectLanguageService -> languages.select(intent.id)
            // A task's dialog must answer while the task runs.
            is WorkbenchIntent.TaskIntent -> tasks.handle(intent)
            WorkbenchIntent.Refresh -> session.requestRefresh(rebuildIndex = true)
            is WorkbenchIntent.ScreenVisible -> session.screenVisible(intent.visible)
            WorkbenchIntent.RefreshStorage -> library.refreshStorage()
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
            WorkbenchIntent.FetchRemote -> gitTransfers.fetch()
            WorkbenchIntent.PullRemote -> gitTransfers.pull()
            WorkbenchIntent.PushRemote -> gitTransfers.push()
            WorkbenchIntent.CancelGitTransfer -> gitTransfers.cancel()
            is WorkbenchIntent.CloneRepository -> gitTransfers.clone(intent.url, intent.directoryName, intent.branch)
            else -> scope.launch {
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
                        is WorkbenchIntent.CreateWorkspace -> lifecycle.create(intent.name)
                        is WorkbenchIntent.RenameWorkspace -> lifecycle.rename(intent)
                        is WorkbenchIntent.DeleteWorkspace -> lifecycle.delete(intent.location)
                        is WorkbenchIntent.ChooseImportSource -> importer.choose(intent.uri)
                        is WorkbenchIntent.ImportWorkspace -> importer.import(intent.name)
                        is WorkbenchIntent.ShowWorkspaceInFiles ->
                            WorkspacesProvider.showInFiles(appContext, intent.location)?.let { workbench.emit(it) }
                        is WorkbenchIntent.OpenWorkspace -> session.open(intent.path, persist = true)
                        is WorkbenchIntent.OpenWorkspaceTerminal -> session.open(intent.path, persist = true, openTerminal = true)
                        is WorkbenchIntent.ResolveWorkspaceSwitch -> session.resolveSwitch(intent)
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
                        is WorkbenchIntent.OpenRecoverableDraft -> session.openRecoverableDraft(intent.location)
                        is WorkbenchIntent.DiscardDraft -> recovery.keepDiskVersion(intent.location)
                        is WorkbenchIntent.ConfirmRecoveredOverwrite -> documents.save(
                            intent.location,
                            allowRecoveredOverwrite = true,
                        )
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
                        is WorkbenchIntent.UpdateCloneForm ->
                            workbench.update { it.copy(cloneUrl = intent.url, cloneName = intent.name, cloneBranch = intent.branch) }
                        is WorkbenchIntent.SelectGitProvider -> gitAuth.selectProvider(intent.provider)
                        is WorkbenchIntent.SelectGitAuthMethod -> gitAuth.selectMethod(intent.method)
                        is WorkbenchIntent.SaveGitToken -> gitAuth.saveToken(intent.host, intent.username, intent.token)
                        WorkbenchIntent.ClearGitToken -> gitAuth.clearToken()
                        WorkbenchIntent.GenerateSshKey -> gitAuth.generateSshKey()
                        is WorkbenchIntent.TestGitConnection -> gitAuth.testConnection(intent.url)
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

    /** Android's prompt closed. Whatever the answer, onboarding has now offered the permission. */
    private suspend fun systemPermissionAnswered(permission: SystemPermission) {
        workbench.emit(WorkbenchEffect.SystemPermissionResolved(permission, runtime.permissionAnswered(permission)))
    }

    /**
     * The screen is going away. Terminal sessions are the user's and outlive
     * it: they end only when the user ends them, and the next screen adopts
     * them. RuntimeForegroundService keeps them alive meanwhile, and stops by
     * itself when none runs.
     */
    override fun close() {
        session.stopWatching()
        languages.stopAll()
        scope.cancel()
    }
}
