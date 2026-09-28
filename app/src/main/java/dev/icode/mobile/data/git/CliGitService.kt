package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestMount
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimePackageInstaller
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOutcome
import java.io.File

/**
 * Runs the real `git` binary inside the Linux runtime.
 *
 * Git remains the repository authority (architecture.md section 10.6): nothing
 * here reimplements Git behaviour, and no result is reported as successful
 * unless Git exited zero.
 */
internal class CliGitService(
    private val layout: RuntimeLayout,
    private val packages: RuntimePackageInstaller,
) {

    private val guest = GuestCommand(layout)

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
     * Runs one Git command for [workspaceHostPath]: at the top of the
     * repository that holds it ([GitMountResolver]), or in the folder itself
     * when none does. The folder is bound into the guest rather than copied, so
     * Git operates on the user's real repository. Cancelling the caller stops
     * Git and every process it started; [onStderr] sees Git's progress as it is printed.
     */
    suspend fun run(
        workspaceHostPath: String?,
        arguments: List<String>,
        onStderr: ((String) -> Unit)? = null,
    ): CommandResult = capture(workspaceHostPath?.let(GitMountResolver::resolve), arguments, onStderr)

    /** Runs Git in exactly [directoryHostPath], whatever repository holds it: a clone into it, two files compared. */
    suspend fun runIn(
        directoryHostPath: String,
        arguments: List<String>,
        onStderr: ((String) -> Unit)? = null,
    ): CommandResult = capture(GuestMount(directoryHostPath), arguments, onStderr)

    private suspend fun capture(mount: GuestMount?, arguments: List<String>, onStderr: ((String) -> Unit)?): CommandResult {
        val result = guest.capture(mount, GitCommandLine.argv(arguments), "git", onStderr = onStderr)
        return CommandResult(result.exitCode, result.stdout, result.stderr)
    }

    /**
     * Runs a constant shell script, with stderr captured, in [mount] (a
     * repository's top) or the guest home.
     *
     * Starting PRoot costs roughly a second, so a refresh that needs eight Git
     * facts runs one script that prints them all rather than eight processes.
     */
    suspend fun runScript(mount: GuestMount?, script: String): CommandResult {
        val result = guest.captureScript(mount, script, "git-script")
        return CommandResult(result.exitCode, result.stdout, result.stderr)
    }

    /** Installs git through the runtime's one package installer; apt's own words explain a failure. */
    suspend fun provision(): GitOutcome<Unit> =
        when (val outcome = packages.ensure(PACKAGE) { File(layout.rootfs, GIT_RELATIVE_PATH).exists() }) {
            RuntimePackageInstaller.Outcome.Ready -> GitOutcome.Success(Unit)
            is RuntimePackageInstaller.Outcome.Failed -> GitOutcome.Failure(outcome.reason, 1)
        }

    /**
     * The disk version of [relativePath] against [editorBytes], as Git diffs
     * two files (`diff --no-index`). The bytes go to a scratch file in the
     * guest's /tmp, never into the workspace, and are removed afterwards.
     */
    suspend fun compareWithBytes(workspaceHostPath: String, relativePath: String, editorBytes: ByteArray): GitOutcome<GitFileDiff?> {
        val scratch = File(layout.rootfs, COMPARE_SCRATCH_RELATIVE).apply { mkdirs() }
        val copy = File(scratch, "editor-" + relativePath.substringAfterLast('/'))
        copy.writeBytes(editorBytes)
        try {
            val guestCopy = "/$COMPARE_SCRATCH_RELATIVE/${copy.name}"
            val result = runIn(workspaceHostPath, listOf("diff", "--no-index", "--no-color", "--", relativePath, guestCopy))
            // --no-index exits 1 when the files differ, which is the answer asked for here.
            return if (result.exitCode == 0 || result.exitCode == 1) {
                GitOutcome.Success(GitDiffParser.parse(result.stdout).firstOrNull()?.copy(path = relativePath))
            } else {
                GitOutcome.Failure(result.errorMessage, result.exitCode)
            }
        } finally {
            copy.delete()
        }
    }

    private companion object {
        /** Guest /tmp, inside the rootfs: seen by Git, never part of any workspace. */
        const val COMPARE_SCRATCH_RELATIVE = "tmp/icode-compare"

        const val GIT_RELATIVE_PATH = "usr/bin/git"
        const val PACKAGE = "git"
        const val SAFE_DIRECTORY_MARKER = "root/.icode-git-configured"
        val EXPLANATORY_PREFIXES = listOf("fatal:", "error:")
        val EXPLANATORY_PHRASES = listOf("Permission denied", "Authentication failed", "could not read")
    }
}
