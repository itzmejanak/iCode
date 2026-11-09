package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.WorkspaceTree
import dev.icode.mobile.domain.GitDiscard
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.ICodeEffect
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.StageLineChange
import dev.icode.mobile.domain.WorkspaceSummary
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * The open workspace's repository (S5): what Git reports about it and the
 * local changes made to it — staging, commits, branches, diffs and discards.
 * Conflicts, network transfers and credentials have their own owners.
 */
internal class GitWorkspaceCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val git: GitWorkspaceOperations,
    private val tree: WorkspaceTree,
    private val activeRoot: () -> String?,
    /** The launcher names each workspace's branch, which a Git operation may have just changed. */
    private val workspaces: () -> List<WorkspaceSummary>,
    /** Git was just installed; the credentials surface depends on it. */
    private val onInstalled: suspend () -> Unit,
) {
    private var refreshJob: Job? = null

    /** The editor's change markers: they read the index again whenever the repository is read. */
    val lineChanges = GitLineChanges(stateHolder, scope, git)

    suspend fun read(root: String): GitPicture {
        val available = git.isAvailable()
        val none = GitWorkspaceSnapshot(repository = null, changes = emptyList())
        // Without git the inspect script cannot run, but .git on disk still
        // tells us this is a repository - so the UI can offer to install Git
        // instead of wrongly offering to initialise a repo that already exists.
        val repositories = git.repositoriesIn(root)
        if (!available) return GitPicture(false, none, git.branchOf(root).orEmpty(), repositories = repositories)
        // A repository Git cannot read is still the user's folder: its files open, and Source
        // Control says what Git said — neither "no changes" nor "not a repository".
        return try {
            val state = git.inspect(root)
            GitPicture(true, state, state.repository?.branch ?: git.branchOf(root).orEmpty(), repositories = repositories)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            GitPicture(true, none, git.branchOf(root).orEmpty(), stateHolder.failureText("git", "Git cannot read this repository", error), repositories)
        }
    }

    /**
     * Tree decoration, Git state and the workspace list, after the worktree changed. Git
     * takes a second or more; if another workspace opened meanwhile, this one's
     * answer is dropped rather than painted over it.
     */
    suspend fun refresh(root: String) {
        val picture = read(root)
        val workspaces = workspaces()
        val files = tree.rows(root, picture.state)
        stateHolder.update {
            if (it.activeWorkspaceLocation != root) return@update it
            it.copy(workspaces = workspaces, files = files).withGit(picture)
        }
        lineChanges.repositoryRead()
    }

    /** The workspace is closing: a refresh still waiting for it has nothing left to show. */
    fun workspaceClosing() {
        refreshJob?.cancel()
        refreshJob = null
    }

    suspend fun refreshActive() {
        activeRoot()?.let { refresh(it) }
    }

    /** Source Control's picker: Git works in [repository] of the open workspace from now on. */
    suspend fun choose(repository: String) {
        val root = activeRoot() ?: return
        git.chooseRepository(root, repository)
        refresh(root)
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
            stateHolder.mutations.withLock { refresh(root) }
        }
    }

    suspend fun initialize() {
        val workspace = activeRoot()
        if (workspace == null) {
            stateHolder.notify(NoticeTone.HeadsUp, "Open a workspace before initializing Git")
            return
        }
        if (git.isRepository(workspace)) {
            stateHolder.notify(NoticeTone.HeadsUp, "This folder is already inside a Git repository")
            return
        }
        runCatching {
            git.initialize(workspace)
            refresh(workspace)
            stateHolder.emit(ICodeEffect.GitInitialized)
        }.onFailure { error ->
            stateHolder.report("git", "Unable to initialize Git", error)
        }
    }

    /**
     * Git is absent from a fresh runtime; this installs it and reports honestly. apt runs off the
     * mutation lock; only the repository's refresh after it takes the lock.
     */
    suspend fun provision() {
        if (git.isAvailable()) {
            stateHolder.update { it.copy(gitAvailable = true) }
            return
        }
        stateHolder.update { it.copy(gitOperation = GitOperationState.Running("Installing Git")) }
        val installed = runCatching { git.provision() }.getOrNull()
        val available = git.isAvailable()
        stateHolder.update {
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
        if (available) stateHolder.mutations.withLock { refreshActive() }
    }

    suspend fun stage(path: String) = act(operation = { workspace -> git.stage(workspace, path) })

    suspend fun unstage(path: String) = act(operation = { workspace -> git.unstage(workspace, path) })

    suspend fun stageAll() = act(operation = { workspace -> git.stageAll(workspace) })

    /** One change of an open file, from the editor's change sheet (docs/editor-plan.md §3.5). */
    suspend fun stageLineChange(intent: StageLineChange) = act(operation = { lineChanges.stage(intent) })

    suspend fun unstageAll() = act(operation = { workspace -> git.unstageAll(workspace) })

    /** Null once committed, else Git's words — said as a message too, unless [quiet] (Commit & Push shows them). */
    suspend fun commit(message: String, quiet: Boolean = false): String? = act(
        operation = { workspace -> git.commit(workspace, message) },
        onSuccess = { stateHolder.notify(NoticeTone.Done, "Commit created") },
        quiet = quiet,
    )

    suspend fun switchBranch(branch: String) = act(operation = { workspace -> git.switchBranch(workspace, branch) })

    suspend fun createBranch(branch: String) = act(operation = { workspace -> git.createBranch(workspace, branch) })

    suspend fun deleteBranch(branch: String) = act(operation = { workspace -> git.deleteBranch(workspace, branch) })

    suspend fun checkoutRemote(remoteBranch: String) =
        act(operation = { workspace -> git.checkoutRemote(workspace, remoteBranch) })

    suspend fun addRemote(name: String, url: String) {
        val problem = GitRemoteRules.nameError(name) ?: GitRemoteRules.urlError(url)
        if (problem != null) {
            stateHolder.notify(NoticeTone.HeadsUp, problem)
            return
        }
        act(operation = { workspace -> git.addRemote(workspace, name, url) })
    }

    suspend fun openDiff(path: String, staged: Boolean) {
        val workspace = activeRoot() ?: return
        runCatching { git.diff(workspace, path, staged) }
            .onSuccess { diff -> stateHolder.update { if (it.activeWorkspaceLocation != workspace) it else it.copy(activeDiff = diff) } }
            .onFailure { error ->
                stateHolder.update { it.copy(activeDiff = null) }
                stateHolder.report("git", "Unable to read the diff", error)
            }
    }

    fun closeDiff() = stateHolder.update { it.copy(activeDiff = null) }

    /**
     * The disk version of [path] against the editor's unsaved text, [editorBytes]
     * as a save would write them, into the diff view (US-EDIT-009 "Compare shows
     * both relevant versions").
     */
    suspend fun compareWithEditor(path: String, editorBytes: ByteArray) {
        val workspace = activeRoot() ?: return
        if (!git.isAvailable()) {
            stateHolder.notify(NoticeTone.HeadsUp, "Comparing uses Git, which is not installed yet. Install it from Source Control")
            return
        }
        stateHolder.update { it.copy(activeDiff = null) }
        runCatching { git.compareWithBytes(workspace, path, editorBytes) }
            .onSuccess { outcome ->
                when (outcome) {
                    is GitOutcome.Success -> stateHolder.update {
                        it.copy(activeDiff = outcome.value ?: GitFileDiff(path = path, hunks = emptyList()))
                    }
                    is GitOutcome.Failure -> stateHolder.failed(COMPARE_FAILED, outcome.message)
                }
            }
            .onFailure { error -> stateHolder.report("git", COMPARE_FAILED, error) }
    }

    /** Holds what discarding [path] — a file, or all a folder holds — would throw away, for its confirmation. */
    fun requestDiscard(path: String) = stateHolder.update { it.copy(pendingDiscard = GitDiscard.of(it.gitChanges, path)) }

    fun cancelDiscard() = stateHolder.update { it.copy(pendingDiscard = null) }

    /**
     * Discards exactly the changes that were confirmed. Git is asked again
     * first: if anything changed since the question was put — in the file, or
     * in the folder — what the user agreed to is no longer what would be lost.
     * The question stays, its button busy, until it is done (docs/loading-plan.md §4.2).
     */
    suspend fun confirmDiscard() {
        val confirmed = stateHolder.current.pendingDiscard ?: return
        try {
            if (!isDiscardablePath(confirmed.path)) {
                stateHolder.notify(NoticeTone.HeadsUp, "${confirmed.path} is not a change iCode can discard")
                return
            }
            act(operation = { workspace ->
                refresh(workspace)
                val current = GitDiscard.of(stateHolder.current.gitChanges, confirmed.path)
                if (current?.changes?.toSet() != confirmed.changes.toSet()) throw IOException("${confirmed.name} changed after you confirmed. Review it again")
                // Edits and deletions return from the index at once; what is new is removed at once.
                confirmed.changes.map { if (it.status == GitStatusCodes.UNTRACKED) it.status else GitStatusCodes.MODIFIED }.distinct()
                    .forEach { kind -> git.discard(workspace, confirmed.path, kind) }
            })
        } finally {
            stateHolder.update { if (it.pendingDiscard == confirmed) it.copy(pendingDiscard = null) else it }
        }
    }

    /** Relative to the repository's top, never above it, never into Git's own `.git`. */
    private fun isDiscardablePath(path: String): Boolean {
        val segments = path.removeSuffix("/").split('/')
        return path.isNotBlank() && !path.startsWith('/') &&
            segments.none { it.isEmpty() || it == "." || it == ".." || it == ".git" }
    }

    suspend fun abort() {
        val inProgress = stateHolder.current.gitRepository?.inProgress ?: return
        act(operation = { workspace -> git.abortInProgress(workspace, inProgress) })
    }

    /**
     * Runs [operation] on the open workspace's repository and reads it again. Returns null when it
     * was done, else what went wrong in Git's words — said on the bell too, unless [quiet].
     */
    suspend fun act(
        operation: suspend (String) -> Unit,
        onSuccess: suspend () -> Unit = {},
        quiet: Boolean = false,
    ): String? {
        val workspace = activeRoot() ?: return OPEN_A_WORKSPACE.also { if (!quiet) stateHolder.notify(NoticeTone.HeadsUp, it) }
        val failure = runCatching {
            operation(workspace)
            refresh(workspace)
            onSuccess()
        }.exceptionOrNull()?.let { error -> stateHolder.failureText("git", GIT_FAILED, error) }
        if (failure != null && !quiet) stateHolder.failed(GIT_FAILED, failure)
        return failure
    }

    private companion object {
        /** Long enough to absorb a burst of saves, short enough to feel immediate. */
        const val REFRESH_DEBOUNCE_MILLIS = 350L
        const val OPEN_A_WORKSPACE = "Open a workspace first"
        const val GIT_FAILED = "Git operation failed"
        const val COMPARE_FAILED = "Unable to compare the two versions"
    }
}
