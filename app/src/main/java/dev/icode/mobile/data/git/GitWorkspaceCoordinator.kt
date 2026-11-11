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
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.notices.NoticePlace
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.notices.SourceControlNotices
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
        if (workspace == null) return stateHolder.tell(SourceControlNotices.openWorkspaceFirst())
        if (git.isRepository(workspace)) return stateHolder.tell(SourceControlNotices.alreadyRepository(placeNow()))
        runCatching {
            git.initialize(workspace)
            refresh(workspace)
            stateHolder.emit(ICodeEffect.GitInitialized)
        }.onFailure { error ->
            stateHolder.tell(SourceControlNotices.notInitialized(placeNow()), "git", error)
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

    suspend fun stage(path: String) = act({ SourceControlNotices.notStaged(path, it) }, { workspace -> git.stage(workspace, path) })

    suspend fun unstage(path: String) = act({ SourceControlNotices.notUnstaged(path, it) }, { workspace -> git.unstage(workspace, path) })

    suspend fun stageAll() = act({ SourceControlNotices.notStaged(null, it) }, { workspace -> git.stageAll(workspace) })

    /** One change of an open file, from the editor's change sheet (docs/editor-plan.md §3.5). */
    suspend fun stageLineChange(intent: StageLineChange) = act(SourceControlNotices::changeNotStaged, { lineChanges.stage(intent) })

    suspend fun unstageAll() = act({ SourceControlNotices.notUnstaged(null, it) }, { workspace -> git.unstageAll(workspace) })

    /** Null once committed — told as what went in, read [before] — else Git's words, told too unless [quiet] (Commit & Push tells them). */
    suspend fun commit(message: String, quiet: Boolean = false): String? = stateHolder.current.let { before ->
        act({ SourceControlNotices.notCommitted(before) }, { workspace -> git.commit(workspace, message) }, { stateHolder.tell(SourceControlNotices.committed(before, message)) }, quiet)
    }

    suspend fun switchBranch(branch: String) = act({ SourceControlNotices.notSwitched(branch, it) }, { workspace -> git.switchBranch(workspace, branch) })

    suspend fun createBranch(branch: String) = act({ SourceControlNotices.branchNotCreated(branch, it) }, { workspace -> git.createBranch(workspace, branch) })

    suspend fun deleteBranch(branch: String) = act({ SourceControlNotices.branchNotDeleted(branch, it) }, { workspace -> git.deleteBranch(workspace, branch) })

    suspend fun checkoutRemote(remoteBranch: String) =
        act({ SourceControlNotices.notCheckedOut(remoteBranch, it) }, { workspace -> git.checkoutRemote(workspace, remoteBranch) })

    suspend fun addRemote(name: String, url: String) {
        val problem = GitRemoteRules.nameError(name) ?: GitRemoteRules.urlError(url)
        if (problem != null) return stateHolder.tell(SourceControlNotices.remoteRefused(problem, placeNow()))
        act({ SourceControlNotices.remoteNotAdded(name, it) }, { workspace -> git.addRemote(workspace, name, url) })
    }

    suspend fun openDiff(path: String, staged: Boolean) {
        val workspace = activeRoot() ?: return
        runCatching { git.diff(workspace, path, staged) }
            .onSuccess { diff -> stateHolder.update { if (it.activeWorkspaceLocation != workspace) it else it.copy(activeDiff = diff) } }
            .onFailure { error ->
                stateHolder.update { it.copy(activeDiff = null) }
                stateHolder.tell(SourceControlNotices.diffUnread(path, placeNow()), "git", error)
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
        if (!git.isAvailable()) return stateHolder.tell(SourceControlNotices.compareNeedsGit())
        stateHolder.update { it.copy(activeDiff = null) }
        runCatching { git.compareWithBytes(workspace, path, editorBytes) }
            .onSuccess { outcome ->
                when (outcome) {
                    is GitOutcome.Success -> stateHolder.update {
                        it.copy(activeDiff = outcome.value ?: GitFileDiff(path = path, hunks = emptyList()))
                    }
                    is GitOutcome.Failure -> stateHolder.tell(SourceControlNotices.notCompared(path, placeNow()).copy(output = outcome.message))
                }
            }
            .onFailure { error -> stateHolder.tell(SourceControlNotices.notCompared(path, placeNow()), "git", error) }
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
                stateHolder.tell(SourceControlNotices.notDiscardable(confirmed.path, placeNow()))
                return
            }
            act({ SourceControlNotices.notDiscarded(confirmed.name, it) }, { workspace ->
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
        act({ SourceControlNotices.notAborted(inProgress, it) }, { workspace -> git.abortInProgress(workspace, inProgress) })
    }

    /**
     * Runs [operation] on the open workspace's repository and reads it again. Returns null when it
     * was done, else what went wrong in Git's words — told too, unless [quiet], as [failed] says
     * what could not be done, where it was (read as it starts), those words its output.
     */
    suspend fun act(
        failed: (NoticePlace?) -> Notice,
        operation: suspend (String) -> Unit,
        onSuccess: suspend () -> Unit = {},
        quiet: Boolean = false,
    ): String? {
        val workspace = activeRoot() ?: return OPEN_A_WORKSPACE.also { if (!quiet) stateHolder.tell(SourceControlNotices.openWorkspaceFirst()) }
        val place = placeNow()
        val error = runCatching {
            operation(workspace)
            refresh(workspace)
            onSuccess()
        }.exceptionOrNull() ?: return null
        val notice = failed(place)
        val failure = stateHolder.failureText("git", notice.title, error)
        if (!quiet) stateHolder.tell(notice.copy(output = failure.takeIf { it != notice.title }))
        return failure
    }

    /** Where Git works now: the workspace, the repository among several, the branch and where it goes. */
    private fun placeNow() = NoticePlaces.git(stateHolder.current)

    private companion object {
        /** Long enough to absorb a burst of saves, short enough to feel immediate. */
        const val REFRESH_DEBOUNCE_MILLIS = 350L
        const val OPEN_A_WORKSPACE = "Open a workspace first"
    }
}
