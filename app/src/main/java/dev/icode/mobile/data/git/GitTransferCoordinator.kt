package dev.icode.mobile.data.git

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.workspace.WorkbenchStateHolder
import dev.icode.mobile.domain.GitOperationState
import dev.icode.mobile.domain.GitOutcome
import java.io.File

/**
 * Clone, fetch, pull and push (S5): each contacts a network the app does not
 * control, so each shows Git's own progress and reports only what Git returned.
 */
internal class GitTransferCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val git: GitTransferOperations,
    private val activeRoot: () -> String?,
    /** Git reads the credential helper itself; this points it at the token the user most recently saved. */
    private val applyCredentials: suspend () -> Unit,
    /** After a sync, what Git now says about the workspace. */
    private val refresh: suspend (workspace: String) -> Unit,
    /** A finished clone opens as the workspace. */
    private val openClone: suspend (location: String) -> Unit,
) {
    suspend fun fetch() = runRemote("Fetching") { workspace, onStderr -> git.fetch(workspace, onStderr) }

    suspend fun pull() = runRemote("Pulling") { workspace, onStderr -> git.pull(workspace, onStderr) }

    suspend fun push() {
        val repository = workbench.current.gitRepository
        val needsUpstream = repository?.upstream == null
        runRemote("Pushing") { workspace, onStderr ->
            git.push(
                workspace,
                setUpstream = needsUpstream,
                branch = repository?.branch.orEmpty(),
                onStderr = onStderr,
            )
        }
    }

    suspend fun clone(url: String, destination: String, directoryName: String) {
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
            git.clone(url, destination, directoryName) { stderr ->
                transcript = GitProgressParser.outputLines(stderr)
                publishProgress(label, stderr, transcript)
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
        label: String,
        operation: suspend (String, (String) -> Unit) -> Unit,
    ) {
        val workspace = activeRoot()
        if (workspace == null) {
            workbench.message("Open a workspace first")
            return
        }
        workbench.update { it.copy(gitOperation = GitOperationState.Running(label)) }
        applyCredentials()
        var transcript = emptyList<String>()
        runCatching {
            operation(workspace) { stderr ->
                transcript = GitProgressParser.outputLines(stderr)
                publishProgress(label, stderr, transcript)
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
     * Only applied while the operation is still running, so a late poll cannot
     * push a finished operation back into a running state.
     */
    private fun publishProgress(label: String, stderr: String, transcript: List<String>) {
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

    /** Prefers Git's own last word, such as "Already up to date." */
    private fun completionMessage(label: String, transcript: List<String>): String {
        val summary = transcript.lastOrNull { line ->
            line.startsWith("Already up to date") || line.startsWith("Everything up-to-date")
        }
        return summary ?: "$label complete"
    }
}
