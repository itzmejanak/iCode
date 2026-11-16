package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitActionRules
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.GitSyncAction
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.ICodeIntent
import dev.icode.mobile.domain.notices.GitNotices
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.WorkspaceRuntimePath
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Clone, fetch, pull and push: each contacts a network the app does not
 * control, so each shows Git's own progress and reports only what Git returned.
 *
 * One runs at a time, as its own job: the screen that asked may close, and
 * nothing else waits behind a slow remote. Each gets an operation number, and
 * only the current one may change what the screens show. Each is running from
 * the moment it is accepted and ends succeeded, failed or cancelled — so the
 * page opened with it never shows the one before.
 */
internal class GitTransferCoordinator(
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    private val git: GitTransferOperations,
    /** A clone goes into the workspaces, through iCode's staging; transfers are recorded by their runtime path. */
    private val workspaces: WorkspacePaths,
    private val activeRoot: () -> String?,
    /** Runs one transfer with the saved token handed to Git for that command alone. */
    private val withCredentials: suspend (block: suspend (config: List<String>) -> Unit) -> Unit,
    /** After a sync, what Git now says about the workspace. */
    private val refresh: suspend (workspace: String) -> Unit,
    /** A finished clone opens as the workspace. */
    private val openClone: suspend (location: String) -> Unit,
    /** Commit & Push's commit: null once made, else Git's words. */
    private val commit: suspend (message: String) -> String?,
    private val ledger: TransferLedger,
    clock: () -> Long = System::currentTimeMillis,
) {
    private val outcomes = GitTransferOutcomes(stateHolder, git, clock)
    private val operations = AtomicLong()
    private var job: Job? = null
    private var running = ""
    private val staging = WorkspaceStaging(workspaces.root)

    /** The transfer [intent] asks for; its job, when one started. */
    fun handle(intent: ICodeIntent.TransferIntent): Job? = when (intent) {
        is ICodeIntent.FetchRemote -> fetch(intent.automatic)
        ICodeIntent.PullRemote -> pull()
        ICodeIntent.PushRemote -> push()
        is ICodeIntent.CommitAndPush -> commitAndPush(intent.message)
        is ICodeIntent.CloneRepository -> clone(intent.url, intent.directoryName, intent.branch, intent.into)
        ICodeIntent.CancelGitTransfer -> cancel().let { null }
    }

    /** [automatic]: iCode's own, as a workspace opens or iCode comes back — it says nothing unless it fails, once. */
    fun fetch(automatic: Boolean = false): Job? = start(GitTransferLabel.FETCHING, automatic) { id ->
        if (refused(GitTransferLabel.FETCHING, GitSyncAction.Fetch)) return@start
        runRemote(id, GitTransferLabel.FETCHING, automatic) { workspace, config, onStderr -> git.fetch(workspace, config, onStderr) }
    }

    fun pull(): Job? = start(GitTransferLabel.PULLING) { id ->
        if (refused(GitTransferLabel.PULLING, GitSyncAction.Pull)) return@start
        runRemote(id, GitTransferLabel.PULLING) { workspace, config, onStderr -> git.pull(workspace, config, onStderr) }
    }

    fun push(): Job? = start(GitTransferLabel.PUSHING) { id -> push(id) }

    /** Commits what is staged, then pushes it — one transfer; a commit Git refuses is not pushed. */
    fun commitAndPush(message: String): Job? = start(GitTransferLabel.COMMITTING) { id ->
        commit(message)?.let { failure ->
            outcomes.begin(activeRoot(), GitTransferLabel.COMMITTING).failed(failure)
            return@start finish(GitOperationState.Failed(GitTransferLabel.COMMITTING, failure))
        }
        push(id)
    }

    /** Clones into the new workspace [directoryName]; [branch] blank means the remote's default. */
    fun clone(url: String, directoryName: String, branch: String = "", into: String? = null): Job? =
        start(GitTransferLabel.CLONING) { id -> clone(id, url.trim(), directoryName.trim(), branch.trim(), into?.let(::File) ?: workspaces.root) }

    /** Starts [work] as the one transfer, or says which one is still running — unless iCode asked for it itself ([automatic]). */
    private fun start(label: String, automatic: Boolean = false, work: suspend (id: Long) -> Unit): Job? = synchronized(this) {
        if (job?.isActive == true) {
            val busy = GitNotices.busy(running, NoticePlaces.git(stateHolder.current))
            if (!automatic) scope.launch { stateHolder.tell(busy) }
            return null
        }
        val id = operations.incrementAndGet()
        running = label
        stateHolder.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        scope.launch {
            try {
                work(id)
            } catch (cancelled: CancellationException) {
                // Stopped wherever it was — even installing Git first — it never stays "running".
                stateHolder.update { state ->
                    val stopped = state.gitOperation as? GitOperationState.Running
                    if (stopped == null || !isCurrent(id)) state
                    else state.copy(gitOperation = GitOperationState.Cancelled(stopped.label, stopped.output))
                }
                throw cancelled
            } finally {
                ledger.finished()
            }
        }.also { job = it }
    }

    /** Stops the running transfer; its job kills and reaps Git. */
    fun cancel() {
        synchronized(this) { job }?.cancel()
    }

    private fun isCurrent(id: Long) = id == operations.get()

    /**
     * Checked again as the job starts: the screen offered the action by the same
     * rule, but the repository may have changed since. Ends it failed, in its words.
     */
    private fun refused(label: String, action: GitSyncAction): Boolean {
        val reason = stateHolder.current.gitRepository?.let { GitActionRules.syncBlocked(action, it) } ?: return false
        finish(GitOperationState.Failed(label, reason))
        return true
    }

    private fun finish(state: GitOperationState) = stateHolder.update { it.copy(gitOperation = state) }

    private suspend fun push(id: Long) {
        if (refused(GitTransferLabel.PUSHING, GitSyncAction.Push)) return
        val repository = stateHolder.current.gitRepository
        val needsUpstream = repository?.upstream == null
        runRemote(id, GitTransferLabel.PUSHING) { workspace, config, onStderr ->
            git.push(
                workspace,
                setUpstream = needsUpstream,
                branch = repository?.branch.orEmpty(),
                config = config,
                onStderr = onStderr,
            )
        }
    }

    /**
     * Git writes into iCode's staging (docs/workspaces-and-files.md), renamed to
     * [directoryName] only when the clone is whole: a failed or cancelled one leaves nothing, and
     * no half repository is ever listed as a workspace.
     */
    private suspend fun clone(id: Long, url: String, directoryName: String, branch: String, into: File) {
        val label = GitTransferLabel.CLONING
        // Checked again here, just before Git runs: the form's view may be old.
        cloneProblem(url, directoryName, branch, into)?.let { problem ->
            stateHolder.update { it.copy(gitOperation = GitOperationState.Failed(label, problem)) }
            return
        }
        val told = outcomes.beginClone(directoryName, url)
        // A new install has no Git until something needs it, and a clone is
        // often the first thing that does: install it as part of this clone.
        if (!git.isAvailable()) {
            val installing = listOf("Installing Git…")
            stateHolder.update { it.copy(gitOperation = GitOperationState.Running(label, output = installing)) }
            val installed = runCatching { git.provision() }.getOrNull()
            if (!git.isAvailable()) {
                val reason = (installed as? GitOutcome.Failure)?.message ?: "Git could not be installed"
                BackendLog.w("git", "Git install before clone failed: $reason")
                stateHolder.update { it.copy(gitOperation = GitOperationState.Failed(label, reason, installing)) }
                told.failed(reason, installing)
                return
            }
            stateHolder.update { it.copy(gitAvailable = true) }
        }
        stateHolder.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        val folder = staging.reserve("clone")
        ledger.started(label, WorkspaceRuntimePath.of(directoryName))
        var transcript = emptyList<String>()
        val target = try {
            withCredentials { config ->
                git.clone(url, workspaces.root.path, folder.name, branch.ifEmpty { null }, config) { stderr ->
                    transcript = transcriptOf(stderr)
                    publishProgress(id, label, stderr, transcript)
                }
            }
            staging.complete(folder, directoryName, into)
        } catch (cancelled: CancellationException) {
            runCatching { staging.discard(folder) }
            stateHolder.update { it.copy(gitOperation = GitOperationState.Cancelled(label, transcript)) }
            throw cancelled
        } catch (error: Exception) {
            runCatching { staging.discard(folder) }
            val reason = stateHolder.failureText("git", "Clone failed", error)
            stateHolder.update { it.copy(gitOperation = GitOperationState.Failed(label, reason, transcript)) }
            told.failed(reason, transcript)
            return
        }
        // The clone is whole: what it made is shown before it says so, so the progress screen gives
        // way to it and never to the workspace open before; Cancel no longer applies.
        withContext(NonCancellable) { openClone(target.path) }
        stateHolder.update { it.copy(gitOperation = GitOperationState.Succeeded(label, transcript), projectDestination = null) }
        told.succeeded(target.path)
    }

    private fun cloneProblem(url: String, directoryName: String, branch: String, into: File): String? {
        GitRemoteRules.urlError(url)?.let { return it }
        if (branch.isNotEmpty()) GitRemoteRules.branchError(branch)?.let { return it }
        if (directoryName.isEmpty()) return "The address does not name a repository folder"
        return staging.nameProblem(directoryName, into)
    }

    /**
     * Runs a network operation, reporting only what Git actually returned.
     *
     * The UI never claims success while offline: failure carries Git's message.
     */
    private suspend fun runRemote(
        id: Long,
        label: String,
        automatic: Boolean = false,
        operation: suspend (workspace: String, config: List<String>, onStderr: (String) -> Unit) -> Unit,
    ) {
        val workspace = activeRoot() ?: return finish(GitOperationState.Failed(label, "Open a workspace first"))
        val outcome = outcomes.begin(workspace, label, automatic)
        synchronized(this) { running = label }
        stateHolder.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        val repository = stateHolder.current.gitRepository?.takeUnless { it.detached }
        ledger.started(label, workspaces.guestOf(workspace) ?: workspace, repository?.branch, repository?.upstream)
        var transcript = emptyList<String>()
        try {
            withCredentials { config ->
                operation(workspace, config) { stderr ->
                    transcript = transcriptOf(stderr)
                    publishProgress(id, label, stderr, transcript)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val reason = stateHolder.failureText("git", "$label failed", error)
            stateHolder.update { it.copy(gitOperation = GitOperationState.Failed(label, reason, transcript)) }
            outcome.failed(reason, transcript)
            return
        }
        stateHolder.update { it.copy(gitOperation = GitOperationState.Succeeded(label, transcript)) }
        refresh(workspace)
        // The progress surface closes itself, so the outcome is told here or never at all.
        outcome.succeeded(transcript)
    }

    /**
     * Publishes whatever Git has printed so far.
     *
     * Only applied while this operation is the current one and still running,
     * so a late poll cannot push a finished operation back into a running state.
     */
    private fun publishProgress(id: Long, label: String, stderr: String, transcript: List<String>) {
        if (!isCurrent(id)) return
        stateHolder.update { state ->
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

    /**
     * Git's output as the progress screen and Output show it. A remote's error
     * can echo its address with an account and token in it, so every line is
     * masked here, before it enters state; each is bounded as well.
     */
    private fun transcriptOf(stderr: String): List<String> =
        GitProgressParser.outputLines(stderr).map { line ->
            val safe = BackendLog.credentialSafe(line)
            if (safe.length > MAX_LINE_CHARS) safe.take(MAX_LINE_CHARS) + "…" else safe
        }

    private companion object {
        const val MAX_LINE_CHARS = 400
    }
}
