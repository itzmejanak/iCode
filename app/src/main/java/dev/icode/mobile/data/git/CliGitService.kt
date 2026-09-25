package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcess
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOutcome
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the real `git` binary inside the Linux runtime.
 *
 * Git remains the repository authority (architecture.md section 10.6): nothing
 * here reimplements Git behaviour, and no result is reported as successful
 * unless Git exited zero.
 */
internal class CliGitService(private val layout: RuntimeLayout) {

    @Volatile
    private var configuredSafeDirectory = false

    data class CommandResult(val exitCode: Int, val stdout: String, val stderr: String) {
        val succeeded: Boolean get() = exitCode == 0

        /**
         * Git's own message, reduced to the line that actually explains the
         * failure.
         *
         * Git prefixes real errors with `fatal:` or `error:`, but often prints
         * warnings first ("Permanently added 'github.com' to the list of known
         * hosts"). Leading with the warning would bury the reason, so the
         * explanatory line is preferred and the raw text is the fallback.
         */
        val errorMessage: String
            get() {
                val lines = stderr.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val explanation = lines.firstOrNull { line ->
                    EXPLANATORY_PREFIXES.any { line.startsWith(it, ignoreCase = true) }
                } ?: lines.firstOrNull { line ->
                    EXPLANATORY_PHRASES.any { line.contains(it, ignoreCase = true) }
                }
                val meaningful = explanation
                    ?: lines.firstOrNull { !it.startsWith("Warning:", ignoreCase = true) }
                    ?: stdout.trim().lines().firstOrNull { it.isNotBlank() }
                return meaningful?.removePrefix("fatal: ")?.removePrefix("error: ")
                    ?.ifEmpty { null }
                    ?: "git exited with code $exitCode"
            }
    }

    /** True once `git` is present in the guest; false until it is provisioned. */
    suspend fun isAvailable(): Boolean =
        layout.isInstalled && File(layout.rootfs, GIT_RELATIVE_PATH).exists()

    /**
     * Records the workspace exception in the guest's own Git configuration.
     *
     * Our commands pass `-c safe.directory=*` themselves, but the user's
     * terminal does not — without this, running `git status` by hand in the
     * workspace fails with "detected dubious ownership". The workspace is a
     * bound Android directory whose owner can never match the guest's root, so
     * the exception is a property of this environment, not a per-command flag.
     */
    suspend fun ensureSafeDirectoryConfigured() {
        if (configuredSafeDirectory) return
        val marker = File(layout.rootfs, SAFE_DIRECTORY_MARKER)
        if (marker.isFile) {
            configuredSafeDirectory = true
            return
        }
        val applied = runScript(null, "git config --global --add safe.directory '*'").succeeded
        if (applied) {
            runCatching {
                marker.parentFile?.mkdirs()
                marker.writeText("configured")
            }
            configuredSafeDirectory = true
        }
    }

    /**
     * Runs one Git command in [workspaceHostPath].
     *
     * The workspace is bound into the guest rather than copied, so Git operates
     * on the user's real repository.
     */
    suspend fun run(
        workspaceHostPath: String?,
        arguments: List<String>,
        onStderr: ((String) -> Unit)? = null,
    ): CommandResult = withContext(Dispatchers.IO) {
        val stderrFile = File(layout.rootfs, GitCommandLine.STDERR_RELATIVE_PATH)
        runCatching {
            stderrFile.parentFile?.mkdirs()
            stderrFile.writeText("")
        }

        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                guestCommand = listOf("/bin/sh", "-c", GitCommandLine.script(arguments)),
                workspaceHostPath = workspaceHostPath,
                guestWorkingDirectory = workspaceHostPath
                    ?.let(RuntimeLayout::guestWorkspace)
                    ?: RuntimeLayout.GUEST_HOME,
            ),
            environment = layout.prootEnvironment(),
            workingDirectory = layout.runtimeRoot.absolutePath,
        )
        val watcher = onStderr?.let { report -> watchStderr(stderrFile, report) }
        val stdout = process.output.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        watcher?.cancel()
        process.destroy()
        val stderr = runCatching { stderrFile.readText() }.getOrDefault("")
        // The watcher polls, so the final state may never have been sampled.
        onStderr?.invoke(stderr)
        CommandResult(exitCode, stdout, stderr)
    }

    /**
     * Reports Git's stderr while the command is still running.
     *
     * Git writes progress to stderr, which the guest redirects to a file so
     * NUL-delimited stdout stays byte-exact. Tailing that file is what makes a
     * clone or push report real progress instead of an invented percentage;
     * polling is enough because Git rewrites the counter a few times a second.
     */
    private fun CoroutineScope.watchStderr(file: File, onStderr: (String) -> Unit): Job =
        launch(Dispatchers.IO) {
            var reportedLength = -1L
            while (isActive) {
                delay(PROGRESS_POLL_MILLIS)
                val length = file.length()
                if (length == reportedLength) continue
                reportedLength = length
                runCatching { file.readText() }.getOrNull()?.let(onStderr)
            }
        }

    /**
     * Runs a raw shell script in the workspace, with stderr captured.
     *
     * Starting PRoot costs roughly a second, so a refresh that needs six Git
     * facts runs one script that emits them separated by RS rather than six
     * processes.
     */
    suspend fun runScript(
        workspaceHostPath: String?,
        script: String,
    ): CommandResult = withContext(Dispatchers.IO) {
        val stderrFile = File(layout.rootfs, GitCommandLine.STDERR_RELATIVE_PATH)
        runCatching {
            stderrFile.parentFile?.mkdirs()
            stderrFile.writeText("")
        }
        val process = RuntimeProcess.start(
            argv = layout.prootCommand(
                guestCommand = listOf("/bin/sh", "-c", script),
                workspaceHostPath = workspaceHostPath,
                guestWorkingDirectory = workspaceHostPath
                    ?.let(RuntimeLayout::guestWorkspace)
                    ?: RuntimeLayout.GUEST_HOME,
            ),
            environment = layout.prootEnvironment(),
            workingDirectory = layout.runtimeRoot.absolutePath,
        )
        val stdout = process.output.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        process.destroy()
        val stderr = runCatching { stderrFile.readText() }.getOrDefault("")
        CommandResult(exitCode, stdout, stderr)
    }

    /** `git status --porcelain=v2 --branch -z`, the one status every surface shares. */
    suspend fun status(workspaceHostPath: String): GitOutcome<GitStatus> {
        val result = run(workspaceHostPath, listOf("status", "--porcelain=v2", "--branch", "-z"))
        return if (result.succeeded) {
            GitOutcome.Success(GitPorcelainParser.parse(result.stdout))
        } else {
            GitOutcome.Failure(result.errorMessage, result.exitCode)
        }
    }

    /**
     * Diff for one path.
     *
     * [staged] compares the index against HEAD; otherwise the worktree against
     * the index. The two are distinct comparisons and never merged (US-GIT-004).
     */
    suspend fun diff(
        workspaceHostPath: String,
        path: String,
        staged: Boolean,
    ): GitOutcome<GitFileDiff?> {
        val arguments = buildList {
            add("diff")
            if (staged) add("--cached")
            // Renames are detected so a moved file is not shown as delete+add.
            add("--find-renames")
            add("--no-color")
            add("--")
            add(path)
        }
        val result = run(workspaceHostPath, arguments)
        return if (result.succeeded) {
            GitOutcome.Success(GitDiffParser.parse(result.stdout, staged).firstOrNull())
        } else {
            GitOutcome.Failure(result.errorMessage, result.exitCode)
        }
    }

    /** Installs git through the guest's package manager; reports Git's own output on failure. */
    suspend fun provision(onOutput: (String) -> Unit = {}): GitOutcome<Unit> {
        val update = run(null, listOf("--version"))
        if (update.succeeded) return GitOutcome.Success(Unit)
        onOutput("Installing git…")
        val install = runShell(null, "apt-get update && apt-get install -y --no-install-recommends git")
        return if (install.succeeded && isAvailable()) {
            GitOutcome.Success(Unit)
        } else {
            GitOutcome.Failure(install.errorMessage, install.exitCode)
        }
    }

    /** Escape hatch for provisioning, which is not itself a Git command. */
    private suspend fun runShell(workspaceHostPath: String?, script: String): CommandResult =
        withContext(Dispatchers.IO) {
            val process = RuntimeProcess.start(
                argv = layout.prootCommand(
                    guestCommand = listOf("/bin/sh", "-c", script),
                    workspaceHostPath = workspaceHostPath,
                    guestWorkingDirectory = RuntimeLayout.GUEST_HOME,
                ),
                environment = layout.prootEnvironment(mapOf("DEBIAN_FRONTEND" to "noninteractive")),
                workingDirectory = layout.runtimeRoot.absolutePath,
            )
            val stdout = process.output.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            process.destroy()
            CommandResult(exitCode, stdout, "")
        }

    private companion object {
        const val GIT_RELATIVE_PATH = "usr/bin/git"
        const val PROGRESS_POLL_MILLIS = 200L
        const val SAFE_DIRECTORY_MARKER = "root/.icode-git-configured"
        val EXPLANATORY_PREFIXES = listOf("fatal:", "error:")
        val EXPLANATORY_PHRASES = listOf("Permission denied", "Authentication failed", "could not read")
    }
}
