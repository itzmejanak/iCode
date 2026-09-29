package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.data.workspace.WorkspaceTree
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.RecentDocument
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspaceSummary
import java.io.File
import java.io.IOException
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
 * local changes made to it — staging, commits, branches, diffs and discards.
 * Conflicts, network transfers and credentials have their own owners.
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
        if (GitHeadReader.isRepository(workspace)) {
            workbench.message("This folder is already inside a Git repository")
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

    suspend fun checkoutRemote(remoteBranch: String) =
        act(operation = { workspace -> git.checkoutRemote(workspace, remoteBranch) })

    suspend fun addRemote(name: String, url: String) {
        val problem = GitRemoteRules.nameError(name) ?: GitRemoteRules.urlError(url)
        if (problem != null) {
            workbench.message(problem)
            return
        }
        act(operation = { workspace -> git.addRemote(workspace, name, url) })
    }

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

    /**
     * The disk version of [path] against the editor's unsaved text, [editorBytes]
     * as a save would write them, into the diff view (US-EDIT-009 "Compare shows
     * both relevant versions").
     */
    suspend fun compareWithEditor(path: String, editorBytes: ByteArray) {
        val workspace = activeRoot() ?: return
        if (!git.isAvailable()) {
            workbench.message("Comparing uses Git, which is not installed yet. Install it from Source Control")
            return
        }
        workbench.update { it.copy(activeDiff = null) }
        runCatching { git.compareWithBytes(workspace, path, editorBytes) }
            .onSuccess { outcome ->
                when (outcome) {
                    is GitOutcome.Success -> workbench.update {
                        it.copy(activeDiff = outcome.value ?: GitFileDiff(path = path, hunks = emptyList()))
                    }
                    is GitOutcome.Failure -> workbench.message(outcome.message)
                }
            }
            .onFailure { error -> workbench.report("git", "Unable to compare the two versions", error) }
    }

    /** Holds the one unstaged change at [path]; a file staged and edited again has a staged row too. */
    fun requestDiscard(path: String) = workbench.update { state ->
        state.copy(pendingDiscard = state.gitChanges.firstOrNull { it.path == path && it.discardable })
    }

    fun cancelDiscard() = workbench.update { it.copy(pendingDiscard = null) }

    /**
     * Discards exactly the change that was confirmed. Git is asked again
     * first: if the file changed since the question was put, what the user
     * agreed to is no longer what would be lost.
     */
    suspend fun confirmDiscard() {
        val confirmed = workbench.current.pendingDiscard ?: return
        workbench.update { it.copy(pendingDiscard = null) }
        if (!isDiscardablePath(confirmed.path)) {
            workbench.message("${confirmed.path} is not a change iCode can discard")
            return
        }
        act(operation = { workspace ->
            refresh(workspace)
            val current = workbench.current.gitChanges.firstOrNull { it.path == confirmed.path && it.discardable }
            if (current != confirmed) throw IOException("${confirmed.name} changed after you confirmed. Review it again")
            git.discard(workspace, confirmed.path, confirmed.status)
        })
    }

    /** Relative to the repository's top, never above it, never into Git's own `.git`. */
    private fun isDiscardablePath(path: String): Boolean {
        val segments = path.removeSuffix("/").split('/')
        return path.isNotBlank() && !path.startsWith('/') &&
            segments.none { it.isEmpty() || it == "." || it == ".." || it == ".git" }
    }

    suspend fun abort() {
        val inProgress = workbench.current.gitRepository?.inProgress ?: return
        act(operation = { workspace -> git.abortInProgress(workspace, inProgress) })
    }

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
