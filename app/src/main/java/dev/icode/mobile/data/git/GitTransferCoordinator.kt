package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import java.io.File
import java.util.concurrent.atomic.AtomicLong
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
    private val activeRoot: () -> String?,
    /** Runs one transfer with the saved token handed to Git for that command alone. */
    private val withCredentials: suspend (block: suspend (config: List<String>) -> Unit) -> Unit,
    /** After a sync, what Git now says about the workspace. */
    private val refresh: suspend (workspace: String) -> Unit,
    /** A finished clone opens as the workspace. */
    private val openClone: suspend (location: String) -> Unit,
) {
    private val operations = AtomicLong()
    private var job: Job? = null
    private var running = ""

    fun fetch(): Job? = start("Fetching") { id ->
        runRemote(id, "Fetching") { workspace, config, onStderr -> git.fetch(workspace, config, onStderr) }
    }

    fun pull(): Job? = start("Pulling") { id ->
        runRemote(id, "Pulling") { workspace, config, onStderr -> git.pull(workspace, config, onStderr) }
    }

    fun push(): Job? = start("Pushing") { id -> push(id) }

    fun clone(url: String, destination: String, directoryName: String): Job? =
        start("Cloning") { id -> clone(id, url, destination, directoryName) }

    /** Starts [work] as the one transfer, or says which one is still running. */
    private fun start(label: String, work: suspend (id: Long) -> Unit): Job? = synchronized(this) {
        if (job?.isActive == true) {
            val busy = running.lowercase()
            scope.launch { workbench.message("Git is still $busy; try again when it finishes") }
            return null
        }
        val id = operations.incrementAndGet()
        running = label
        scope.launch { work(id) }.also { job = it }
    }

    private fun isCurrent(id: Long) = id == operations.get()

    private suspend fun push(id: Long) {
        val repository = workbench.current.gitRepository
        if (repository?.detached == true) {
            workbench.message("HEAD is detached. Check out a branch before pushing")
            return
        }
        val needsUpstream = repository?.upstream == null
        runRemote(id, "Pushing") { workspace, config, onStderr ->
            git.push(
                workspace,
                setUpstream = needsUpstream,
                branch = repository?.branch.orEmpty(),
                config = config,
                onStderr = onStderr,
            )
        }
    }

    private suspend fun clone(id: Long, url: String, destination: String, directoryName: String) {
        val label = "Cloning"
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
        var transcript = emptyList<String>()
        runCatching {
            withCredentials { config ->
                git.clone(url, destination, directoryName, config) { stderr ->
                    transcript = transcriptOf(stderr)
                    publishProgress(id, label, stderr, transcript)
                }
            }
        }
            .onSuccess {
                workbench.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                openClone(File(destination, directoryName).path)
            }
            .onFailure { error ->
                workbench.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            workbench.failureText("git", "Clone failed", error),
                            transcript,
                        ),
                    )
                }
            }
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
        var transcript = emptyList<String>()
        runCatching {
            withCredentials { config ->
                operation(workspace, config) { stderr ->
                    transcript = transcriptOf(stderr)
                    publishProgress(id, label, stderr, transcript)
                }
            }
        }
            .onSuccess {
                workbench.update {
                    it.copy(gitOperation = GitOperationState.Succeeded(label, transcript))
                }
                refresh(workspace)
                // The progress surface closes itself, so the outcome is
                // reported here or it is never reported at all.
                workbench.message(completionMessage(label, transcript))
            }
            .onFailure { error ->
                workbench.update {
                    it.copy(
                        gitOperation = GitOperationState.Failed(
                            label,
                            workbench.failureText("git", "$label failed", error),
                            transcript,
                        ),
                    )
                }
            }
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
