package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceTree
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspaceSummary
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/** What Git says about one workspace. */
internal data class GitPicture(
    val available: Boolean,
    val state: GitWorkspaceSnapshot,
    /** Git's branch, else `.git/HEAD`'s, so a repository names its branch before Git is installed. */
    val branch: String,
)

/**
 * The open workspace's repository (S5): what Git reports about it and the
 * local changes made to it — staging, commits, branches, diffs, discards and
 * conflicts. Network transfers and credentials have their own owners.
 */
internal class GitWorkspaceCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val scope: CoroutineScope,
    private val git: GitWorkspaceOperations,
    private val tree: WorkspaceTree,
    private val activeRoot: () -> String?,
    /** The launcher shows each recent workspace's branch, so recents are read again with Git. */
    private val recentWorkspaces: () -> List<WorkspaceSummary>,
    private val recentDocuments: (root: String) -> List<RecentDocument>,
    /** Git was just installed; the credentials surface depends on it. */
    private val onInstalled: suspend () -> Unit,
) {
    private var refreshJob: Job? = null

    suspend fun read(root: String): GitPicture {
        val available = git.isAvailable()
        // Without git the inspect script cannot run, but .git on disk still
        // tells us this is a repository - so the UI can offer to install Git
        // instead of wrongly offering to initialise a repo that already exists.
        val state = if (available) git.inspect(root) else GitWorkspaceSnapshot(repository = null, changes = emptyList())
        return GitPicture(available, state, state.repository?.branch ?: GitHeadReader.branchOf(root).orEmpty())
    }

    /**
     * Tree decoration, Git state and recents, after the worktree changed. Git
     * takes a second or more; if another workspace opened meanwhile, this one's
     * answer is dropped rather than painted over it.
     */
    suspend fun refresh(root: String) {
        val picture = read(root)
        val recents = recentWorkspaces()
        val files = tree.rows(root, picture.state)
        workbench.update {
            if (it.activeWorkspaceLocation != root) return@update it
            it.copy(
                workspaces = recents,
                files = files,
                gitChanges = picture.state.changes,
                gitRepository = picture.state.repository,
                gitAvailable = picture.available,
                activeBranch = picture.branch,
                recentDocuments = recentDocuments(root),
            )
        }
    }

    /** The workspace is closing: a refresh still waiting for it has nothing left to show. */
    fun workspaceClosing() {
        refreshJob?.cancel()
        refreshJob = null
    }

    suspend fun refreshActive() {
        activeRoot()?.let { refresh(it) }
    }

    /**
     * Refreshes Git state shortly after the worktree changes.
     *
     * Each refresh starts PRoot, which costs about a second, so saves are
     * coalesced: a new request replaces the one still waiting rather than
     * queueing another process behind it.
     */
    fun scheduleRefresh() {
        val root = activeRoot() ?: return
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(REFRESH_DEBOUNCE_MILLIS)
            workbench.mutations.withLock { refresh(root) }
        }
    }

    suspend fun initialize() {
        val workspace = activeRoot()
        if (workspace == null) {
            workbench.message("Open a workspace before initializing Git")
            return
        }
        runCatching {
            git.initialize(workspace)
            refresh(workspace)
            workbench.emit(WorkbenchEffect.GitInitialized)
        }.onFailure { error ->
            workbench.report("git", "Unable to initialize Git", error)
        }
    }

    /** Git is absent from a fresh runtime; this installs it and reports honestly. */
    suspend fun provision() {
        if (git.isAvailable()) {
            workbench.update { it.copy(gitAvailable = true) }
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running("Installing Git")) }
        val installed = runCatching { git.provision() }.getOrNull()
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
        if (available) onInstalled()
        if (available) activeRoot()?.let { refresh(it) }
    }

    suspend fun stage(path: String) = act(operation = { workspace -> git.stage(workspace, path) })

    suspend fun unstage(path: String) = act(operation = { workspace -> git.unstage(workspace, path) })

    suspend fun stageAll() = act(operation = { workspace -> git.stageAll(workspace) })

    suspend fun unstageAll() = act(operation = { workspace -> git.unstageAll(workspace) })

    suspend fun commit(message: String) = act(
        operation = { workspace -> git.commit(workspace, message) },
        onSuccess = { workbench.emit(WorkbenchEffect.GitCommitted) },
    )

    suspend fun switchBranch(branch: String) = act(operation = { workspace -> git.switchBranch(workspace, branch) })

    suspend fun createBranch(branch: String) = act(operation = { workspace -> git.createBranch(workspace, branch) })

    suspend fun deleteBranch(branch: String) = act(operation = { workspace -> git.deleteBranch(workspace, branch) })

    suspend fun openDiff(path: String, staged: Boolean) {
        val workspace = activeRoot() ?: return
        runCatching { git.diff(workspace, path, staged) }
            .onSuccess { diff -> workbench.update { if (it.activeWorkspaceLocation != workspace) it else it.copy(activeDiff = diff) } }
            .onFailure { error ->
                workbench.update { it.copy(activeDiff = null) }
                workbench.report("git", "Unable to read the diff", error)
            }
    }

    fun closeDiff() = workbench.update { it.copy(activeDiff = null) }

    fun requestDiscard(path: String) = workbench.update { state ->
        state.copy(pendingDiscard = state.gitChanges.firstOrNull { it.path == path })
    }

    fun cancelDiscard() = workbench.update { it.copy(pendingDiscard = null) }

    suspend fun discard(path: String, status: String) {
        act(operation = { workspace -> git.discard(workspace, path, status) })
        workbench.update { it.copy(pendingDiscard = null) }
    }

    suspend fun abort() {
        val inProgress = workbench.current.gitRepository?.inProgress ?: return
        act(operation = { workspace -> git.abortInProgress(workspace, inProgress) })
    }

    /**
     * Git reports change paths relative to the repository root, which is not
     * the opened folder when a subdirectory of a repository is opened.
     */
    private fun conflictRoot(): String? =
        workbench.current.gitRepository?.workTreePath ?: activeRoot()

    /** Loads the conflicted file straight from the worktree, markers and all. */
    suspend fun openConflict(path: String) {
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

    fun closeConflict() = workbench.update { it.copy(activeConflict = null) }

    /**
     * Writes the resolved text back and refreshes.
     *
     * The file is rebuilt from the parsed sections rather than through
     * `checkout --ours/--theirs`, because "accept both" has no Git equivalent
     * and a single path keeps all three choices consistent with what the user
     * is looking at. Marking resolved stays a separate explicit step.
     */
    suspend fun resolveConflict(path: String, choice: GitConflictChoice) {
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
                refreshActive()
            }
            .onFailure { error ->
                workbench.report("git", "Unable to write the resolved file", error)
            }
    }

    suspend fun markResolved(path: String) = act(operation = { git.markResolved(it, path) })

    /** Runs one local Git change in the open workspace, then shows what Git now says. */
    suspend fun act(
        operation: suspend (String) -> Unit,
        onSuccess: suspend () -> Unit = {},
    ) {
        val workspace = activeRoot()
        if (workspace == null) {
            workbench.message("Open a workspace first")
            return
        }
        runCatching {
            operation(workspace)
            refresh(workspace)
            onSuccess()
        }.onFailure { error ->
            workbench.report("git", "Git operation failed", error)
        }
    }

    private companion object {
        /** Long enough to absorb a burst of saves, short enough to feel immediate. */
        const val REFRESH_DEBOUNCE_MILLIS = 350L
    }
}
