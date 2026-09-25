package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitConflictChoice
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitChange
import java.io.IOException

/**
 * One repository's state and its changes, read together.
 *
 * [repository] is null when the path is not inside a Git repository, which is
 * how the activity rail chooses between "Initialize Git" and "Source control".
 */
internal data class GitWorkspaceSnapshot(
    val repository: GitRepositoryState?,
    val changes: List<GitChange>,
)

/**
 * The repository operations the workbench needs, run through the real `git`.
 *
 * Every method either succeeds or throws with Git's own message, so callers
 * report the actionable command error rather than a rewritten one
 * (PR-GIT-12). Nothing here reimplements Git behaviour.
 */
internal class GitRepositoryClient(private val cli: CliGitService) {

    suspend fun isAvailable(): Boolean = cli.isAvailable()

    /**
     * Reads the whole repository picture in one process.
     *
     * Returns null when the path is not inside a repository, which is how the
     * activity rail decides between "Initialize Git" and "Source control".
     */
    suspend fun inspect(workspacePath: String): GitWorkspaceSnapshot {
        // Also makes `git` usable from the user's own terminal in this
        // workspace, not just from the commands this client runs.
        cli.ensureSafeDirectoryConfigured()
        val result = cli.runScript(workspacePath, INSPECT_SCRIPT)
        // rev-parse failing is the normal "not a repository" answer, not an error.
        val sections = result.stdout.split(RECORD_SEPARATOR)
        val topLevel = sections.getOrNull(0)?.trim().orEmpty()
        if (topLevel.isEmpty()) return GitWorkspaceSnapshot(null, emptyList())
        // Git ran inside the guest, so it reports the bind mount's path. Every
        // caller compares this against Android paths — the Explorer decorates
        // files by it — so it is translated back before it leaves this client.
        val guestWorkspace = RuntimeLayout.guestWorkspace(workspacePath)
        val hostTopLevel = if (topLevel == guestWorkspace) {
            workspacePath
        } else {
            topLevel.replaceFirst(guestWorkspace, workspacePath.trimEnd('/'))
        }

        val status = GitPorcelainParser.parse(sections.getOrNull(1).orEmpty())
        val allRefs = sections.getOrNull(2).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val branches = allRefs
            .filter { it.startsWith(LOCAL_REF_PREFIX) }
            .map { it.removePrefix(LOCAL_REF_PREFIX) }
        // origin/HEAD is a symbolic pointer, not a branch anyone checks out.
        val remoteRefs = allRefs
            .filter { it.startsWith(REMOTE_REF_PREFIX) }
            .map { it.removePrefix(REMOTE_REF_PREFIX) }
            .filterNot { it.endsWith("/HEAD") }
        val remote = sections.getOrNull(3).orEmpty().lines().firstOrNull { it.isNotBlank() }?.trim()
        val authorName = sections.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }
        val authorEmail = sections.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
        val lastCommit = parseLastCommit(sections.getOrNull(6).orEmpty())
        val markers = sections.getOrNull(7).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val inProgress = GitInProgressDetector.detect(
            hasMergeHead = "MERGE_HEAD" in markers,
            hasRebase = "rebase-merge" in markers || "rebase-apply" in markers,
            hasCherryPick = "CHERRY_PICK_HEAD" in markers,
            hasRevert = "REVERT_HEAD" in markers,
            hasBisect = "BISECT_LOG" in markers,
        )

        val branch = status.header.branch?.takeUnless { it == GitStatusHeader.DETACHED }
            ?: branches.firstOrNull()
            ?: DEFAULT_BRANCH

        return GitWorkspaceSnapshot(
            repository = GitRepositoryState(
                workTreePath = hostTopLevel,
                branch = branch,
                hasCommits = status.header.hasCommits,
                remoteName = remote,
                authorName = authorName,
                authorEmail = authorEmail,
                branches = branches.ifEmpty { listOf(branch) },
                remoteBranches = remoteRefs,
                inProgress = inProgress,
                lastCommit = lastCommit,
                upstream = status.header.upstream,
                ahead = status.header.ahead,
                behind = status.header.behind,
            ),
            changes = status.changes,
        )
    }

    suspend fun initialize(workspacePath: String) =
        expect(workspacePath, listOf("init", "--initial-branch=$DEFAULT_BRANCH"))

    suspend fun stage(workspacePath: String, path: String) =
        // `add -A` on a path stages a deletion too, which plain `add` would skip.
        expect(workspacePath, listOf("add", "-A", "--", path))

    suspend fun unstage(workspacePath: String, path: String) {
        // Before the first commit there is no HEAD to reset against.
        val hasHead = cli.run(workspacePath, listOf("rev-parse", "--verify", "HEAD")).succeeded
        val arguments = if (hasHead) listOf("reset", "--quiet", "HEAD", "--", path) else listOf("rm", "--cached", "-r", "--", path)
        expect(workspacePath, arguments)
    }

    suspend fun stageAll(workspacePath: String) = expect(workspacePath, listOf("add", "-A"))

    suspend fun unstageAll(workspacePath: String) {
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
    suspend fun discard(workspacePath: String, path: String, status: String) {
        when (status) {
            GitStatusCodes.UNTRACKED -> expect(workspacePath, listOf("clean", "-fd", "--", path))
            GitStatusCodes.ADDED -> expect(workspacePath, listOf("rm", "-f", "--", path))
            else -> expect(
                workspacePath,
                listOf("restore", "--source=HEAD", "--staged", "--worktree", "--", path),
            )
        }
    }

    suspend fun commit(workspacePath: String, message: String) {
        require(message.isNotBlank()) { "Enter a commit message" }
        expect(workspacePath, listOf("commit", "-m", message))
    }

    suspend fun configureIdentity(workspacePath: String, name: String, email: String) {
        require(name.isNotBlank()) { "Enter a Git author name" }
        require(email.matches(EMAIL_PATTERN)) { "Enter a valid Git author email" }
        expect(workspacePath, listOf("config", "user.name", name.trim()))
        expect(workspacePath, listOf("config", "user.email", email.trim()))
    }

    suspend fun switchBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", branch))

    suspend fun createBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", "-b", branch))

    suspend fun deleteBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("branch", "-d", branch))

    suspend fun diff(workspacePath: String, path: String, staged: Boolean): GitFileDiff? {
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
    suspend fun abortInProgress(workspacePath: String, inProgress: GitInProgress) {
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

    suspend fun markResolved(workspacePath: String, path: String) =
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

    private fun parseLastCommit(section: String): GitCommitSummary? {
        val line = section.lineSequence().firstOrNull { it.isNotBlank() } ?: return null
        val fields = line.split(UNIT_SEPARATOR)
        if (fields.size < 4) return null
        return GitCommitSummary(
            id = fields[0].take(SHORT_ID_LENGTH),
            message = fields[1],
            author = fields[2],
            authoredAtEpochMillis = (fields[3].trim().toLongOrNull() ?: 0L) * 1000L,
        )
    }

    private companion object {
        const val DEFAULT_BRANCH = "main"
        const val SHORT_ID_LENGTH = 12
        /** See GitCommandLine: bind-mounted Android paths never match ownership. */
        const val SAFE_DIRECTORY = "-c 'safe.directory=*'"
        const val RECORD_SEPARATOR = '\u001E'
        const val UNIT_SEPARATOR = '\u001F'
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /**
         * One process, six facts, separated by RS.
         *
         * `rev-parse` runs first: an empty first section means "not a
         * repository" and the rest is ignored. The overall exit code is
         * deliberately `status`'s, not the last command's, because `git config`
         * exits non-zero simply when an identity is unset.
         */
        const val LOCAL_REF_PREFIX = "refs/heads/"
        const val REMOTE_REF_PREFIX = "refs/remotes/"
        val INSPECT_SCRIPT = buildString {
            append("git ")
            append(SAFE_DIRECTORY)
            append(" rev-parse --show-toplevel 2>/dev/null; ")
            append("printf '\\036'; ")
            append("git -c core.quotepath=false ")
            append(SAFE_DIRECTORY)
            append(" --no-optional-locks status --porcelain=v2 --branch -z 2>")
            append(GitCommandLine.GUEST_STDERR_PATH)
            append("; S=$?; ")
            append("printf '\\036'; ")
            append("git ")
            append(SAFE_DIRECTORY)
            // The full refname, not the short one: only `refs/heads/` versus
            // `refs/remotes/` distinguishes a local branch from a remote one.
            append(" branch -a --format='%(refname)' 2>/dev/null; ")
            append("printf '\\036'; ")
            append("git remote 2>/dev/null; ")
            append("printf '\\036'; ")
            append("git config user.name 2>/dev/null; ")
            append("printf '\\036'; ")
            append("git config user.email 2>/dev/null; ")
            append("printf '\\036'; ")
            append("git ")
            append(SAFE_DIRECTORY)
            append(" log -1 --format='%H%x1f%s%x1f%an <%ae>%x1f%at' 2>/dev/null; ")
            append("printf '\\036'; ")
            // An interrupted merge, rebase, cherry-pick, revert or bisect is
            // recorded as a file in .git. Reading them is how Git itself knows,
            // and it costs nothing compared with another command.
            append("G=\$(git ")
            append(SAFE_DIRECTORY)
            append(" rev-parse --git-dir 2>/dev/null); ")
            append("for f in MERGE_HEAD rebase-merge rebase-apply CHERRY_PICK_HEAD REVERT_HEAD BISECT_LOG; do ")
            append("[ -e \"\$G/\$f\" ] && printf '%s\\n' \"\$f\"; ")
            append("done; ")
            append("exit \$S")
        }
    }
}
