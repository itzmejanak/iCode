package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitOutcome
import java.io.IOException

/** The local operations on the open workspace's repository; tests put a fake in their place. */
internal interface GitWorkspaceOperations {
    suspend fun isAvailable(): Boolean
    suspend fun provision(): GitOutcome<Unit>
    suspend fun inspect(workspacePath: String): GitWorkspaceSnapshot
    suspend fun initialize(workspacePath: String)
    suspend fun stage(workspacePath: String, path: String)
    suspend fun unstage(workspacePath: String, path: String)
    suspend fun stageAll(workspacePath: String)
    suspend fun unstageAll(workspacePath: String)
    suspend fun commit(workspacePath: String, message: String)
    suspend fun switchBranch(workspacePath: String, branch: String)
    suspend fun createBranch(workspacePath: String, branch: String)
    suspend fun deleteBranch(workspacePath: String, branch: String)
    suspend fun diff(workspacePath: String, path: String, staged: Boolean): GitFileDiff?
    suspend fun discard(workspacePath: String, path: String, status: String)
    suspend fun abortInProgress(workspacePath: String, inProgress: GitInProgress)
    suspend fun markResolved(workspacePath: String, path: String)
}

/**
 * The repository operations the workbench needs, run through the real `git`.
 *
 * Every method either succeeds or throws with Git's own message, so callers
 * report the actionable command error rather than a rewritten one
 * (PR-GIT-12). Nothing here reimplements Git behaviour.
 */
internal class GitRepositoryClient(private val cli: CliGitService) : GitWorkspaceOperations {

    override suspend fun isAvailable(): Boolean = cli.isAvailable()

    /** Installs Git into the runtime; Git's own output explains a failure. */
    override suspend fun provision(): GitOutcome<Unit> = cli.provision()

    /** Reads the whole repository picture in one process ([GitInspectParser]). */
    override suspend fun inspect(workspacePath: String): GitWorkspaceSnapshot {
        // Also makes `git` usable from the user's own terminal in this
        // workspace, not just from the commands this client runs.
        cli.ensureSafeDirectoryConfigured()
        return GitInspectParser.parse(workspacePath, cli.runScript(workspacePath, GitInspectParser.SCRIPT).stdout)
    }

    override suspend fun initialize(workspacePath: String) =
        expect(workspacePath, listOf("init", "--initial-branch=${GitInspectParser.DEFAULT_BRANCH}"))

    override suspend fun stage(workspacePath: String, path: String) =
        // `add -A` on a path stages a deletion too, which plain `add` would skip.
        expect(workspacePath, listOf("add", "-A", "--", path))

    override suspend fun unstage(workspacePath: String, path: String) {
        // Before the first commit there is no HEAD to reset against.
        val hasHead = cli.run(workspacePath, listOf("rev-parse", "--verify", "HEAD")).succeeded
        val arguments = if (hasHead) listOf("reset", "--quiet", "HEAD", "--", path) else listOf("rm", "--cached", "-r", "--", path)
        expect(workspacePath, arguments)
    }

    override suspend fun stageAll(workspacePath: String) = expect(workspacePath, listOf("add", "-A"))

    override suspend fun unstageAll(workspacePath: String) {
        val hasHead = cli.run(workspacePath, listOf("rev-parse", "--verify", "HEAD")).succeeded
        val arguments = if (hasHead) listOf("reset", "--quiet", "HEAD") else listOf("rm", "--cached", "-r", "--quiet", ".")
        expect(workspacePath, arguments)
    }

    /**
     * Throws away a working-tree change.
     *
     * Irreversible, which is why the caller must have confirmed the exact path
     * first (US-GIT-006). An untracked file has nothing to restore from, so it
     * is removed instead.
     */
    /**
     * Throws away one file's changes, whatever state it is in.
     *
     * The three cases genuinely differ: an untracked file has no committed
     * version to restore so it is removed, a newly added file must leave the
     * index as well, and a tracked file is restored from HEAD in both the
     * index and the worktree — `checkout --` alone would only restore the
     * worktree and silently leave a staged change behind.
     */
    override suspend fun discard(workspacePath: String, path: String, status: String) {
        when (status) {
            GitStatusCodes.UNTRACKED -> expect(workspacePath, listOf("clean", "-fd", "--", path))
            GitStatusCodes.ADDED -> expect(workspacePath, listOf("rm", "-f", "--", path))
            else -> expect(
                workspacePath,
                listOf("restore", "--source=HEAD", "--staged", "--worktree", "--", path),
            )
        }
    }

    override suspend fun commit(workspacePath: String, message: String) {
        require(message.isNotBlank()) { "Enter a commit message" }
        expect(workspacePath, listOf("commit", "-m", message))
    }

    suspend fun configureIdentity(workspacePath: String, name: String, email: String) {
        require(name.isNotBlank()) { "Enter a Git author name" }
        require(email.matches(EMAIL_PATTERN)) { "Enter a valid Git author email" }
        expect(workspacePath, listOf("config", "user.name", name.trim()))
        expect(workspacePath, listOf("config", "user.email", email.trim()))
    }

    override suspend fun switchBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", branch))

    override suspend fun createBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", "-b", branch))

    override suspend fun deleteBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("branch", "-d", branch))

    override suspend fun diff(workspacePath: String, path: String, staged: Boolean): GitFileDiff? {
        val arguments = buildList {
            add("diff")
            if (staged) add("--cached")
            add("--find-renames")
            add("--no-color")
            add("--")
            add(path)
        }
        val result = cli.run(workspacePath, arguments)
        if (!result.succeeded) throw IOException(result.errorMessage)
        return GitDiffParser.parse(result.stdout, staged).firstOrNull()
    }

    /** Reads a conflicted file's marker blocks straight from the worktree. */
    suspend fun conflict(workspacePath: String, path: String): List<String> {
        val result = cli.run(workspacePath, listOf("show", ":1:$path"))
        return if (result.succeeded) result.stdout.lines() else emptyList()
    }

    /**
     * Resolves a conflict by taking one side wholesale.
     *
     * `--ours`/`--theirs` are Git's own resolution, so the result is whatever
     * Git would have produced; marking resolved stays a separate explicit step
     * (US-GIT-010).
     */
    suspend fun resolveConflict(workspacePath: String, path: String, choice: GitConflictChoice) {
        when (choice) {
            GitConflictChoice.Current -> expect(workspacePath, listOf("checkout", "--ours", "--", path))
            GitConflictChoice.Incoming -> expect(workspacePath, listOf("checkout", "--theirs", "--", path))
            // "Both" keeps the merged file with its markers for manual editing.
            GitConflictChoice.Both -> Unit
        }
    }

    /**
     * Ends whatever Git has half-finished.
     *
     * Each operation has its own abort, and bisect uses `reset`; none of them
     * is a plain `git reset`, which would leave the merge state behind.
     */
    override suspend fun abortInProgress(workspacePath: String, inProgress: GitInProgress) {
        val arguments = when (inProgress) {
            GitInProgress.Merge -> listOf("merge", "--abort")
            GitInProgress.Rebase -> listOf("rebase", "--abort")
            GitInProgress.CherryPick -> listOf("cherry-pick", "--abort")
            GitInProgress.Revert -> listOf("revert", "--abort")
            GitInProgress.Bisect -> listOf("bisect", "reset")
            GitInProgress.None -> return
        }
        expect(workspacePath, arguments)
    }

    override suspend fun markResolved(workspacePath: String, path: String) =
        expect(workspacePath, listOf("add", "--", path))

    /**
     * Network operations take `--progress` because Git only reports it when
     * stderr is a terminal, and here stderr is a file the host tails.
     */
    suspend fun fetch(workspacePath: String, onStderr: ((String) -> Unit)? = null) =
        expect(workspacePath, listOf("fetch", "--prune", "--progress"), onStderr)

    suspend fun pull(workspacePath: String, onStderr: ((String) -> Unit)? = null) =
        expect(workspacePath, listOf("pull", "--ff-only", "--progress"), onStderr)

    suspend fun push(
        workspacePath: String,
        setUpstream: Boolean,
        branch: String,
        onStderr: ((String) -> Unit)? = null,
    ) {
        val arguments = buildList {
            add("push")
            add("--progress")
            if (setUpstream) {
                add("--set-upstream")
                add("origin")
                add(branch)
            }
        }
        expect(workspacePath, arguments, onStderr)
    }

    /** Clones into [destinationPath]; the caller opens the result only on success. */
    suspend fun clone(
        url: String,
        destinationPath: String,
        directoryName: String,
        onStderr: ((String) -> Unit)? = null,
    ) {
        require(url.isNotBlank()) { "Enter a repository URL" }
        val result = cli.run(destinationPath, listOf("clone", "--progress", url, directoryName), onStderr)
        if (!result.succeeded) throw IOException(result.errorMessage)
    }

    private suspend fun expect(
        workspacePath: String,
        arguments: List<String>,
        onStderr: ((String) -> Unit)? = null,
    ) {
        val result = cli.run(workspacePath, arguments, onStderr)
        if (!result.succeeded) throw IOException(result.errorMessage)
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
