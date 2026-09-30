package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitActionRules
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.GitRemoteRules
import dev.icode.mobile.domain.GitSyncAction
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.WorkspaceRuntimePath
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Clone, fetch, pull and push (S5): each contacts a network the app does not
 * control, so each shows Git's own progress and reports only what Git returned.
 *
 * One runs at a time, as its own job: the screen that asked may close, and
 * nothing else waits behind a slow remote. Each gets an operation number, and
 * only the current one may change what the screens show.
 */
internal class GitTransferCoordinator(
    private val workbench: WorkbenchStateHolder,
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
    private val ledger: TransferLedger,
) {
    private val operations = AtomicLong()
    private var job: Job? = null
    private var running = ""
    private val staging = WorkspaceStaging(workspaces.root)

    fun fetch(): Job? = start(GitTransferLabel.FETCHING) { id ->
        if (refused(GitSyncAction.Fetch)) return@start
        runRemote(id, GitTransferLabel.FETCHING) { workspace, config, onStderr -> git.fetch(workspace, config, onStderr) }
    }

    fun pull(): Job? = start(GitTransferLabel.PULLING) { id ->
        if (refused(GitSyncAction.Pull)) return@start
        runRemote(id, GitTransferLabel.PULLING) { workspace, config, onStderr -> git.pull(workspace, config, onStderr) }
    }

    fun push(): Job? = start(GitTransferLabel.PUSHING) { id -> push(id) }

    /** Clones into the new workspace [directoryName]; [branch] blank means the remote's default. */
    fun clone(url: String, directoryName: String, branch: String = ""): Job? =
        start(GitTransferLabel.CLONING) { id -> clone(id, url.trim(), directoryName.trim(), branch.trim()) }

    /** Starts [work] as the one transfer, or says which one is still running. */
    private fun start(label: String, work: suspend (id: Long) -> Unit): Job? = synchronized(this) {
        if (job?.isActive == true) {
            val busy = running.lowercase()
            scope.launch { workbench.message("Git is still $busy; try again when it finishes") }
            return null
        }
        val id = operations.incrementAndGet()
        running = label
        scope.launch {
            try {
                work(id)
            } catch (cancelled: CancellationException) {
                // Stopped wherever it was — even installing Git first — it never stays "running".
                workbench.update { state ->
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
     * rule, but the repository may have changed since. Says why, in its words.
     */
    private suspend fun refused(action: GitSyncAction): Boolean {
        val reason = workbench.current.gitRepository?.let { GitActionRules.syncBlocked(action, it) } ?: return false
        workbench.message(reason)
        return true
    }

    private suspend fun push(id: Long) {
        if (refused(GitSyncAction.Push)) return
        val repository = workbench.current.gitRepository
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
     * Git writes into iCode's staging (docs/workspace-storage-plan.md §4.5), renamed to
     * [directoryName] only when the clone is whole: a failed or cancelled one leaves nothing, and
     * no half repository is ever listed as a workspace.
     */
    private suspend fun clone(id: Long, url: String, directoryName: String, branch: String) {
        val label = GitTransferLabel.CLONING
        // Checked again here, just before Git runs: the form's view may be old.
        cloneProblem(url, directoryName, branch)?.let { problem ->
            workbench.update { it.copy(gitOperation = GitOperationState.Failed(label, problem)) }
            return
        }
        // A new install has no Git until something needs it, and a clone is
        // often the first thing that does: install it as part of this clone.
        if (!git.isAvailable()) {
            val installing = listOf("Installing Git…")
            workbench.update { it.copy(gitOperation = GitOperationState.Running(label, output = installing)) }
            val installed = runCatching { git.provision() }.getOrNull()
            if (!git.isAvailable()) {
                val reason = (installed as? GitOutcome.Failure)?.message ?: "Git could not be installed"
                BackendLog.w("git", "Git install before clone failed: $reason")
                workbench.update { it.copy(gitOperation = GitOperationState.Failed(label, reason, installing)) }
                return
            }
            workbench.update { it.copy(gitAvailable = true) }
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running(label)) }
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
            staging.complete(folder, directoryName)
        } catch (cancelled: CancellationException) {
            runCatching { staging.discard(folder) }
            workbench.update { it.copy(gitOperation = GitOperationState.Cancelled(label, transcript)) }
            throw cancelled
        } catch (error: Exception) {
            runCatching { staging.discard(folder) }
            workbench.update {
                it.copy(gitOperation = GitOperationState.Failed(label, workbench.failureText("git", "Clone failed", error), transcript))
            }
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Succeeded(label, transcript)) }
        openClone(target.path)
    }

    private fun cloneProblem(url: String, directoryName: String, branch: String): String? {
        GitRemoteRules.urlError(url)?.let { return it }
        if (branch.isNotEmpty()) GitRemoteRules.branchError(branch)?.let { return it }
        if (directoryName.isEmpty()) return "The address does not name a repository folder"
        return WorkspaceNameRules.newWorkspaceError(directoryName) { File(workspaces.root, it).exists() }
    }

    /**
     * Runs a network operation, reporting only what Git actually returned.
     *
     * The UI never claims success while offline: failure carries Git's message.
     */
    private suspend fun runRemote(
        id: Long,
        label: String,
        operation: suspend (workspace: String, config: List<String>, onStderr: (String) -> Unit) -> Unit,
    ) {
        val workspace = activeRoot()
        if (workspace == null) {
            workbench.message("Open a workspace first")
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        ledger.started(label, workspaces.guestOf(workspace) ?: workspace)
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
            workbench.update {
                it.copy(gitOperation = GitOperationState.Failed(label, workbench.failureText("git", "$label failed", error), transcript))
            }
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Succeeded(label, transcript)) }
        refresh(workspace)
        // The progress surface closes itself, so the outcome is
        // reported here or it is never reported at all.
        workbench.message(completionMessage(label, transcript))
    }

    /**
     * Publishes whatever Git has printed so far.
     *
     * Only applied while this operation is the current one and still running,
     * so a late poll cannot push a finished operation back into a running state.
     */
    private fun publishProgress(id: Long, label: String, stderr: String, transcript: List<String>) {
        if (!isCurrent(id)) return
        workbench.update { state ->
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

    /** Prefers Git's own last word, such as "Already up to date." */
    private fun completionMessage(label: String, transcript: List<String>): String {
        val summary = transcript.lastOrNull { line ->
            line.startsWith("Already up to date") || line.startsWith("Everything up-to-date")
        }
        return summary ?: "$label complete"
    }

    private companion object {
        const val MAX_LINE_CHARS = 400
    }
}
