package dev.icode.mobile.data.workspace

import android.content.Context
import com.termux.terminal.TerminalSession
import dev.icode.mobile.data.assistant.AssistantCoordinator
import dev.icode.mobile.data.assistant.tools.AssistantToolbox
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.git.GitAuthCoordinator
import dev.icode.mobile.data.git.GitAuthenticator
import dev.icode.mobile.data.git.GitConflictCoordinator
import dev.icode.mobile.data.git.GitCredentialStore
import dev.icode.mobile.data.git.GitRepositoryClient
import dev.icode.mobile.data.git.GitTransferCoordinator
import dev.icode.mobile.data.git.GitTransferLedger
import dev.icode.mobile.data.git.GitWorkspaceCoordinator
import dev.icode.mobile.data.language.LanguageServiceCoordinator
import dev.icode.mobile.data.notices.NoticeBoard
import dev.icode.mobile.data.run.RunCoordinator
import dev.icode.mobile.data.runtime.RuntimeToolsCoordinator
import dev.icode.mobile.data.runtime.RuntimeCoordinator
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.RuntimeForegroundService
import dev.icode.mobile.data.runtime.RuntimeLayout
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
import dev.icode.mobile.domain.GitAuthWorkIntent
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.NoticeIntent
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.ICodeGateway
import dev.icode.mobile.domain.GpgIntent
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.ICodeSnapshot
import dev.icode.mobile.domain.StageLineChange
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

/** The iCode app's backend: one per process ([ICodeProcess]), so its work outlives every screen. */
class AndroidICodeGateway internal constructor(context: Context) : ICodeGateway {
    private val stateHolder = ICodeStateHolder()
    private val appContext = context.applicationContext
    private val store: WorkspaceStore = SharedPreferencesWorkspaceStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runtimeLayout = RuntimeLayout(appContext)
    private val execution = RuntimeExecution(runtimeLayout, scope, stateHolder)
    private val source = WorkspaceFileSource(runtimeLayout.workspaces)
    private val settings = AndroidWorkspacePreferencesRepository(appContext)
    /** The runtime's tools, one apt run at a time: Git, the SSH client and search install through it. */
    private val runtimeTools = RuntimeToolsCoordinator(stateHolder, scope, runtimeLayout, appContext)
    private val gitCli = CliGitService(runtimeLayout, runtimeTools.installer)
    private val git = GitRepositoryClient(gitCli)
    private val authenticator = GitAuthenticator(runtimeLayout, gitCli, runtimeTools.installer, GitCredentialStore(appContext))
    private val terminals = TerminalSessionManager.get(appContext)
    private val terminal: TerminalCoordinator = TerminalCoordinator(
        stateHolder,
        terminals,
        runtimeInstalled = { runtimeLayout.isInstalled },
        activeRoot = { session.activeRoot },
        openWorkspaceTerminal = { location -> session.open(location, persist = true, openTerminal = true) },
        workspaceChanged = { session.requestRefresh(rebuildIndex = true) },
    )
    private val notices = NoticeBoard(appContext, stateHolder, terminals.screenVisible)
    /** Something runs in the runtime that rebuilding it, or clearing the cache PRoot works in, would break. */
    private val runtimeBusy = { terminals.hasRunningSessions() || languages.hasActiveProcesses() || execution.processes.anyRunning() }
    private val languages: LanguageServiceCoordinator = LanguageServiceCoordinator(appContext, stateHolder, scope, runtimeLayout, { session.activeRoot }) { execution.environment.current().variables }
    private val runtime: RuntimeCoordinator = RuntimeCoordinator(
        appContext, stateHolder, scope, runtimeLayout, AndroidSystemAccess(appContext),
        sessionsRunning = runtimeBusy,
    )
    /** Phone storage, the file explorer's Shared place (docs/storage-explorer-plan.md). */
    private val phoneStorage = PhoneStorage.of()
    private val library: WorkspaceLibraryCoordinator = WorkspaceLibraryCoordinator(
        appContext, stateHolder, scope, source, store, runtimeLayout, phoneStorage,
        branchOf = git::branchOf,
        tasksRunning = { runtimeBusy() || runtime.installing || stateHolder.current.gitOperation is GitOperationState.Running },
    )
    private val tree = WorkspaceTree(stateHolder, source)
    private val gitWorkspace: GitWorkspaceCoordinator = GitWorkspaceCoordinator(
        stateHolder,
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
        stateHolder,
        WorkspaceConflictFiles(stateHolder, source) { documents.reconcileWithDisk() },
        activeRoot = { session.activeRoot },
        act = gitWorkspace::act,
        stage = { workspace, path -> git.markResolved(workspace, path) },
        refresh = { gitWorkspace.refreshActive() },
    )
    private val gitTransfers: GitTransferCoordinator = GitTransferCoordinator(
        stateHolder,
        scope,
        git,
        runtimeLayout.workspaces,
        activeRoot = { session.activeRoot },
        withCredentials = { block -> authenticator.withHttpsCredentials(block) },
        // A transfer runs outside the lock; what it changes afterwards takes it.
        refresh = { workspace -> stateHolder.mutations.withLock { gitWorkspace.refresh(workspace) } },
        openClone = { location -> stateHolder.mutations.withLock { lifecycle.reveal(location) } },
        commit = { message -> stateHolder.mutations.withLock { gitWorkspace.commit(message, quiet = true) } },
        ledger = transferLedger,
    )
    private val gitAuth: GitAuthCoordinator = GitAuthCoordinator(
        stateHolder,
        git,
        authenticator,
        activeRoot = { session.activeRoot },
        actInRepository = gitWorkspace::act,
        refreshRepository = { root -> gitWorkspace.refresh(root) },
    )
    /** Long operations: their own jobs, off the mutation lock (docs/long-operations-plan.md). */
    private val tasks = TaskRunner(stateHolder, scope)
    /** One ripgrep, for Search and the assistant: one install, one cooldown after a failed one. */
    private val ripgrep = RipgrepService(runtimeLayout, runtimeTools.installer)
    private val search: SearchCoordinator = SearchCoordinator(stateHolder, scope, ripgrep) { session.activeRoot }
    private val recovery: DocumentRecoveryCoordinator =
        DocumentRecoveryCoordinator(stateHolder, scope, DocumentDraftStore(File(appContext.filesDir, "document-drafts"))) { location -> documents.sourceOf(location) }
    private val documents: DocumentSessionCoordinator = DocumentSessionCoordinator(
        stateHolder, source, recovery, library, activeRoot = { session.activeRoot }, onSaved = { gitWorkspace.scheduleRefresh() },
    )
    private val session: WorkspaceSessionCoordinator = WorkspaceSessionCoordinator(
        stateHolder, scope, source, tree, library, documents, recovery, gitWorkspace, search, terminal, languages,
    )
    /** What may change now, asked the same way by the lifecycle and the Explorer's items. */
    private val usage = WorkspaceUsage(stateHolder, tasks, terminal::runsIn, execution.processes::runsIn, documents::blockingEdits) { session.activeRoot }
    private val lifecycle = WorkspaceLifecycleCoordinator(
        stateHolder, tasks, source, library, session, usage, documents::open,
        moveSettings = { from, to -> settings.moveWorkspace(from, to).also { assistant.workspaceMoved(from, to); execution.processes.workspaceMoved(from, to); runs.workspaceMoved(from, to) } },
        forgetSettings = { location -> settings.forgetWorkspace(location).also { assistant.workspaceForgotten(location); execution.processes.workspaceForgotten(location); runs.workspaceForgotten(location) } },
    )
    private val items = WorkspaceItemsCoordinator(
        stateHolder, tasks, source, tree, documents, library, usage, lifecycle, phoneStorage,
        StorageTransfers(
            stateHolder, tasks, runtimeLayout.workspaces, git, phoneStorage,
            reserveSpace = runtimeLayout::reserveSpace,
            freeBytes = runtimeLayout::allocatableBytes,
        ),
        activeRoot = { session.activeRoot },
        refreshWorkspaceState = { root -> gitWorkspace.refresh(root) },
        reindex = { root -> search.workspaceChanged(root) },
    )
    /** The Terminal panel's Run tab (docs/run-tab-plan.md): Android apps and saved commands, for the tab and the assistant. */
    private val runs = RunCoordinator(stateHolder, scope, runtimeLayout, execution, appContext)
    /** iCode's assistant (docs/assistant-koog-plan.md): its turns are its own jobs, its tools iCode's owners. */
    private val assistant = AssistantCoordinator.create(
        appContext, stateHolder, scope,
        AssistantToolbox(source, ripgrep, gitCli, execution, runs, runtimeLayout::isInstalled, { documents.blockingEdits(it) != null }) { session.requestRefresh(rebuildIndex = true) },
        activeRoot = { session.activeRoot },
        source = source, git = gitCli,
    )

    override val snapshot: StateFlow<ICodeSnapshot> = stateHolder.snapshot
    override val effects: SharedFlow<ICodeEffect> = stateHolder.effects

    init {
        notices.start(scope) { listOfNotNull(terminals.ledger.takePrevious(), interruptedTransfer) }
        languages.publish()
        scope.launch {
            WorkspaceStaging(runtimeLayout.workspaces.root).sweep()
            library.refresh()
            session.start()
        }
        scope.launch { recovery.loadRecoverable() }
        terminal.follow(scope, settings.values(null).filter { it.loaded && it.loadError == null }.map { it.terminal })
        gitWorkspace.lineChanges.follow(settings.values(null).filter { it.loaded && it.loadError == null }.map { it.editor.changeMarkers })
        scope.launch { runtime.reportState() }
        runtimeTools.start()
        runs.start()
        BackgroundWork.follow(appContext, scope, stateHolder.snapshot.map(BackgroundWorkRules::of), ::dispatch)
        library.followAccess()
        assistant.start()
    }

    /** The live session behind a terminal tab, for the view that draws it. Main thread. */
    fun terminalSession(id: String): TerminalSession? = terminals.session(id)

    /** Reports screen changes of a terminal session until the handle is closed. Main thread. */
    fun observeTerminal(id: String, onScreenChanged: () -> Unit): AutoCloseable = terminals.observe(id, onScreenChanged)

    override fun dispatch(intent: ICodeIntent) {
        when (intent) {
            // What must not wait on the mutation lock. apt, ssh-keygen, gpg and the network do not change the workspace.
            is ICodeIntent.SelectLanguageService -> languages.select(intent.id)
            ICodeIntent.ProvisionGit -> scope.launch { gitWorkspace.provision() }
            is GitAuthWorkIntent -> scope.launch { gitAuth.handle(intent) }
            // A task's dialog must answer while the task runs.
            is ICodeIntent.TaskIntent -> tasks.handle(intent)
            is ICodeIntent.RuntimeToolsIntent -> runtimeTools.handle(intent)
            ICodeIntent.Refresh -> session.requestRefresh(rebuildIndex = true)
            is ICodeIntent.ScreenVisible -> session.screenVisible(intent.visible)
            is NoticeIntent -> scope.launch { notices.handle(intent) }
            is ICodeIntent.StorageIntent -> library.read(intent)
            // Reading the runtime's own files changes nothing, so it does not wait
            // on the mutation lock behind whatever the workspace is doing.
            ICodeIntent.RefreshRuntimeDetails -> runtime.refreshDetails()
            is ICodeIntent.UpdateDocumentText -> documents.updateText(intent)
            // Folder reads and expansion state belong to WorkspaceTree; they do
            // not wait behind an unrelated Git refresh on the mutation lock.
            is ICodeIntent.ToggleFolder -> session.activeRoot?.let { root -> scope.launch { tree.toggle(root, intent.path) } }
            // Search owns cancellable jobs. It must never wait behind a workspace
            // mutation, and a new query must be able to cancel the old process.
            ICodeIntent.PrepareWorkspaceSearch -> session.activeRoot?.let(search::prepareIndex)
            is ICodeIntent.RunContentSearch -> search.runContentSearch(intent.query, intent.matchCase, intent.wholeWord, intent.regex)
            ICodeIntent.ClearContentSearch -> search.clear()
            // A transfer is its own job: nothing waits behind a slow remote.
            is ICodeIntent.TransferIntent -> gitTransfers.handle(intent)
            is ICodeIntent.AssistantIntent -> assistant.handle(intent)
            is ICodeIntent.ProcessIntent -> execution.processes.handle(intent)
            is ICodeIntent.RunIntent -> runs.handle(intent)
            is GpgIntent -> execution.gpg.handle(intent)
            else -> scope.launch {
                stateHolder.mutations.withLock {
                    when (intent) {
                        is ICodeIntent.RequestSystemPermission -> stateHolder.emit(ICodeEffect.RequestSystemPermission(intent.permission))
                        // Android's prompt closed: whatever the answer, the permission has been offered.
                        is ICodeIntent.SystemPermissionAnswered ->
                            stateHolder.emit(ICodeEffect.SystemPermissionResolved(intent.permission, runtime.permissionAnswered(intent.permission)))
                        is ICodeIntent.DeclineSystemPermission -> runtime.declinePermission(intent.permission)
                        is ICodeIntent.OpenSystemSettings -> stateHolder.emit(ICodeEffect.OpenSystemSettings(intent.permission))
                        ICodeIntent.RefreshSystemAccess -> runtime.refreshSystemAccess()
                        is ICodeIntent.CreateWorkspace -> lifecycle.create(intent)
                        is ICodeIntent.RenameWorkspace -> lifecycle.rename(intent)
                        is ICodeIntent.DeleteWorkspace -> lifecycle.delete(intent.location)
                        is ICodeIntent.OpenWorkspace -> session.open(intent.path, persist = true)
                        is ICodeIntent.OpenWorkspaceTerminal -> session.open(intent.path, persist = true, openTerminal = true)
                        is ICodeIntent.ResolveWorkspaceSwitch -> session.resolveSwitch(intent)
                        is ICodeIntent.OpenFile -> lifecycle.reveal(intent.path, intent.lineNumber, intent.column)
                        is ICodeIntent.CreateFile -> items.createFile(intent)
                        is ICodeIntent.CreateWorkspaceFolder -> items.createFolder(intent)
                        is ICodeIntent.RenameWorkspaceItem -> items.rename(intent)
                        is ICodeIntent.ClipboardIntent -> items.clipboard(intent)
                        is ICodeIntent.DeleteWorkspaceItem -> items.delete(intent)
                        is ICodeIntent.SaveDocument -> documents.save(intent.location, intent.automatic, intent.expectedText, intent.expectedWorkspace)
                        is ICodeIntent.RestoreDraft -> recovery.restore(intent.location)
                        is ICodeIntent.OpenRecoverableDraft -> session.openRecoverableDraft(intent.location)
                        is ICodeIntent.DiscardDraft -> recovery.keepDiskVersion(intent.location)
                        is ICodeIntent.ConfirmRecoveredOverwrite -> documents.save(intent.location, allowRecoveredOverwrite = true)
                        is ICodeIntent.ReloadFromDisk -> documents.reloadFromDisk(intent.location)
                        is ICodeIntent.OverwriteDisk -> documents.overwriteDisk(intent.location)
                        is ICodeIntent.CompareWithDisk -> session.compareWithDisk(intent.location)
                        is ICodeIntent.SelectDocument -> documents.select(intent.location)
                        is ICodeIntent.ReorderDocument -> documents.reorder(intent.movingLocation, intent.targetLocation)
                        is ICodeIntent.CloseDocument -> documents.close(intent.location, intent.saveChanges)
                        is ICodeIntent.CloseOtherDocuments -> documents.closeOthers(intent.keepLocation, intent.saveChanges)
                        ICodeIntent.CloseSavedDocuments -> documents.closeSaved()
                        ICodeIntent.InitializeGit -> gitWorkspace.initialize()
                        is ICodeIntent.StageFile -> gitWorkspace.stage(intent.path)
                        is ICodeIntent.UnstageFile -> gitWorkspace.unstage(intent.path)
                        ICodeIntent.StageAllFiles -> gitWorkspace.stageAll()
                        ICodeIntent.UnstageAllFiles -> gitWorkspace.unstageAll()
                        is ICodeIntent.CommitStaged -> gitWorkspace.commit(intent.message)
                        is ICodeIntent.ConfigureGitIdentity -> gitAuth.configureRepositoryIdentity(intent.name, intent.email)
                        is ICodeIntent.ConfigureGlobalGitIdentity -> gitAuth.configureGlobalIdentity(intent.name, intent.email)
                        ICodeIntent.ClearRepositoryGitIdentity -> gitAuth.clearRepositoryIdentity()
                        is ICodeIntent.SwitchBranch -> gitWorkspace.switchBranch(intent.branch)
                        is ICodeIntent.CreateBranch -> gitWorkspace.createBranch(intent.branch)
                        is ICodeIntent.DeleteBranch -> gitWorkspace.deleteBranch(intent.branch)
                        is ICodeIntent.CheckoutRemoteBranch -> gitWorkspace.checkoutRemote(intent.remoteBranch)
                        ICodeIntent.RefreshGit -> gitWorkspace.refreshActive()
                        is ICodeIntent.ChooseGitRepository -> gitWorkspace.choose(intent.location)
                        is ICodeIntent.OpenDiff -> gitWorkspace.openDiff(intent.path, intent.staged)
                        is StageLineChange -> gitWorkspace.stageLineChange(intent)
                        ICodeIntent.CloseDiff -> gitWorkspace.closeDiff()
                        is ICodeIntent.RequestDiscardChanges -> gitWorkspace.requestDiscard(intent.path)
                        ICodeIntent.CancelDiscardChanges -> gitWorkspace.cancelDiscard()
                        ICodeIntent.ConfirmDiscardChanges -> gitWorkspace.confirmDiscard()
                        ICodeIntent.AbortInProgress -> gitWorkspace.abort()
                        is ICodeIntent.AddRemote -> gitWorkspace.addRemote(intent.name, intent.url)
                        is ICodeIntent.OpenConflict -> gitConflicts.open(intent.path)
                        ICodeIntent.CloseConflict -> gitConflicts.close()
                        is ICodeIntent.ResolveConflict -> gitConflicts.resolve(intent.path, intent.sectionStart, intent.choice)
                        is ICodeIntent.MarkConflictResolved -> gitConflicts.markResolved(intent.path)
                        is ICodeIntent.UpdateCloneForm -> stateHolder.update { it.copy(cloneUrl = intent.url, cloneName = intent.name, cloneBranch = intent.branch) }
                        is ICodeIntent.SelectGitProvider -> gitAuth.selectProvider(intent.provider)
                        is ICodeIntent.SelectGitAuthMethod -> gitAuth.selectMethod(intent.method)
                        is ICodeIntent.SaveGitToken -> gitAuth.saveToken(intent.host, intent.username, intent.token)
                        ICodeIntent.ClearGitToken -> gitAuth.clearToken()
                        ICodeIntent.RefreshGitAuth -> gitAuth.refresh()
                        is ICodeIntent.SaveLanguageService -> languages.save(intent)
                        is ICodeIntent.RemoveLanguageService -> languages.remove(intent.id)
                        is ICodeIntent.StartLanguageService -> languages.start(intent.id)
                        is ICodeIntent.StopLanguageService -> languages.stop(intent.id)
                        is ICodeIntent.RestartLanguageService -> languages.restart(intent.id)
                        ICodeIntent.InstallRuntime -> runtime.install(RuntimeInstallReason.Initial)
                        ICodeIntent.ClearAppCache -> library.clearCache()
                        // Repair keeps the home directory, reset does not; both go
                        // through the one installer rather than a second path.
                        ICodeIntent.RepairRuntime -> if (runtime.install(RuntimeInstallReason.Repair)) stateHolder.emit(ICodeEffect.RuntimeRebuildStarted)
                        ICodeIntent.ResetRuntime -> if (runtime.install(RuntimeInstallReason.Reset)) stateHolder.emit(ICodeEffect.RuntimeRebuildStarted)
                        ICodeIntent.OpenTerminalSession -> terminal.open()
                        ICodeIntent.EnsureTerminalSession -> terminal.ensure()
                        is ICodeIntent.SelectTerminalSession -> terminal.select(intent.id)
                        is ICodeIntent.ShowTerminalSession -> terminal.show(intent.id)
                        is ICodeIntent.ShowProcessOutput -> execution.processes.info(intent.id)?.let { terminal.showOutput(it, execution.processes.logOf(it.id)) }
                        is ICodeIntent.ReorderTerminalSession -> terminal.reorder(intent.movingId, intent.targetId)
                        is ICodeIntent.CloseTerminalSession -> terminal.close(intent.id)
                        is ICodeIntent.OpenExternalUrl -> stateHolder.emit(ICodeEffect.OpenExternalUrl(intent.url))
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
