package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitStatusCodes
import dev.icode.mobile.domain.GitInProgress
import dev.icode.mobile.domain.GitFileDiff
import dev.icode.mobile.domain.GitIdentityRules
import dev.icode.mobile.domain.GitOutcome
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The repository operations the snapshot needs, run through the real `git`.
 *
 * Every method either succeeds or throws with Git's own message, so callers
 * report the actionable command error rather than a rewritten one
 * (PR-GIT-12). Nothing here reimplements Git behaviour.
 */
internal class GitRepositoryClient(private val cli: CliGitService) :
    GitWorkspaceOperations, GitTransferOperations, GitConfigOperations, GitCheckoutOperations {

    override suspend fun isAvailable(): Boolean = cli.isAvailable()

    /** Installs Git into the runtime; Git's own output explains a failure. */
    override suspend fun provision(): GitOutcome<Unit> = cli.provision()

    override fun branchOf(workspacePath: String): String? = cli.locator.branchOf(workspacePath)

    override fun isRepository(workspacePath: String): Boolean = cli.locator.isRepository(workspacePath)

    override fun repositoriesIn(workspacePath: String): List<String> = cli.locator.repositoriesIn(workspacePath)

    override fun chooseRepository(workspacePath: String, repository: String) = cli.locator.choose(workspacePath, repository)

    /** Reads the whole repository picture in one process ([GitInspectParser]). */
    override suspend fun inspect(workspacePath: String): GitWorkspaceSnapshot {
        // Also makes `git` usable from the user's own terminal in this
        // workspace, not just from the commands this client runs.
        cli.ensureSafeDirectoryConfigured()
        val result = cli.runScript(cli.workingFolderOf(workspacePath), GitInspectParser.SCRIPT)
        val snapshot = GitInspectParser.parse(result.stdout, cli::locationOf)
        // Status failing inside a repository (a corrupt index, say) is a real
        // failure: shown as "no changes" it would hide the user's work.
        if (snapshot.repository != null && !result.succeeded) throw IOException(result.errorMessage)
        return snapshot
    }

    /**
     * Reads what Git recorded, restores it on disk, and only then tells Git the filesystem holds
     * modes and links: stopped anywhere before, the repository reads as it did.
     */
    override suspend fun restoreCheckout(top: String): GitCheckoutRepair.Restored {
        cli.ensureSafeDirectoryConfigured()
        val reader = GitCheckoutRepair.Reader()
        val read = cli.streamScript(top, GitCheckoutRepair.SCRIPT, reader::accept)
        if (!read.succeeded) throw IOException(read.errorMessage)
        val restored = withContext(Dispatchers.IO) { GitCheckoutRepair.restore(File(top), reader.recorded()) }
        val settings = cli.runScript(top, GitCheckoutRepair.SETTINGS_SCRIPT)
        if (!settings.succeeded) throw IOException(settings.errorMessage)
        return restored
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
     * Throws away the unstaged changes ([GitChange.discardable]) of one kind at
     * [path]: a file's, or all a folder holds of that kind.
     *
     * An edit or a deletion returns to the index — the staged version when
     * there is one, else the last commit — so staged work survives. A new file
     * or folder has no version to return to, so it is removed; `clean` keeps
     * whatever inside it Git ignores.
     */
    override suspend fun discard(workspacePath: String, path: String, status: String) {
        when (status) {
            GitStatusCodes.UNTRACKED -> expect(workspacePath, listOf("clean", "-f", "-d", "--", path))
            GitStatusCodes.MODIFIED, GitStatusCodes.DELETED ->
                expect(workspacePath, listOf("restore", "--worktree", "--", path))
            else -> throw IOException("Only unstaged changes can be discarded")
        }
    }

    override suspend fun commit(workspacePath: String, message: String) {
        require(message.isNotBlank()) { "Enter a commit message" }
        expect(workspacePath, listOf("commit", "-m", message))
    }

    override suspend fun configureIdentity(workspacePath: String, name: String, email: String) {
        require(name.isNotBlank()) { "Enter a Git author name" }
        require(GitIdentityRules.isValidEmail(email)) { "Enter a valid Git author email" }
        writeConfig(GitConfigScope.Local, "user.name", name.trim(), workspacePath)
        writeConfig(GitConfigScope.Local, "user.email", email.trim(), workspacePath)
    }

    override suspend fun readConfig(scope: GitConfigScope, key: String, workspacePath: String?): String? =
        cli.run(workspacePath, listOf("config", scope.option, "--get", key))
            .takeIf { it.succeeded }?.stdout?.trim()?.takeIf { it.isNotEmpty() }

    override suspend fun writeConfig(scope: GitConfigScope, key: String, value: String, workspacePath: String?) {
        val result = cli.run(workspacePath, listOf("config", scope.option, key, value))
        if (!result.succeeded) throw IOException(result.errorMessage)
    }

    override suspend fun unsetConfig(scope: GitConfigScope, key: String, workspacePath: String?) {
        // `--unset-all` fails on a key that is not set, which is not a failure here.
        if (!cli.run(workspacePath, listOf("config", scope.option, "--get", key)).succeeded) return
        val result = cli.run(workspacePath, listOf("config", scope.option, "--unset-all", key))
        if (!result.succeeded) throw IOException(result.errorMessage)
    }

    override suspend fun switchBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", branch))

    override suspend fun createBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("checkout", "-b", branch))

    override suspend fun deleteBranch(workspacePath: String, branch: String) =
        expect(workspacePath, listOf("branch", "-d", branch))

    /**
     * The full remote-tracking ref leaves nothing to guess: which remote, and —
     * from that remote's refspec — the local name. A local branch of that name
     * already existing is Git's refusal to report, not a switch to make.
     */
    override suspend fun checkoutRemote(workspacePath: String, remoteBranch: String) =
        expect(workspacePath, listOf("checkout", "--track", "refs/remotes/$remoteBranch"))

    override suspend fun addRemote(workspacePath: String, name: String, url: String) =
        expect(workspacePath, listOf("remote", "add", "--", name, url))

    override fun repositoryTopOf(location: String): String? = cli.locator.topOf(File(location).parent ?: location)?.path

    override fun indexStampOf(top: String): String? =
        cli.locator.indexOf(top)?.takeIf { it.isFile }?.let { "${it.length()}:${it.lastModified()}" }

    override suspend fun indexVersions(top: String, paths: List<String>): Map<String, IndexVersion> = cli.indexVersions(top, paths)

    override suspend fun stageVersion(top: String, path: String, mode: String, bytes: ByteArray) = cli.stageVersion(top, path, mode, bytes)

    override suspend fun compareWithBytes(workspacePath: String, path: String, editorBytes: ByteArray): GitOutcome<GitFileDiff?> =
        cli.compareWithBytes(workspacePath, path, editorBytes)

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

    suspend fun markResolved(workspacePath: String, path: String) =
        expect(workspacePath, listOf("add", "--", path))

    /**
     * Network operations take `--progress` because Git only reports it when
     * stderr is a terminal, and here stderr is a file the host tails.
     */
    override suspend fun fetch(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?) =
        expect(workspacePath, listOf("fetch", "--prune", "--progress"), onStderr, config)

    override suspend fun pull(workspacePath: String, config: List<String>, onStderr: ((String) -> Unit)?) =
        expect(workspacePath, listOf("pull", "--ff-only", "--progress"), onStderr, config)

    override suspend fun push(
        workspacePath: String,
        setUpstream: Boolean,
        branch: String,
        config: List<String>,
        onStderr: ((String) -> Unit)?,
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
        expect(workspacePath, arguments, onStderr, config)
    }

    /** Clones into [destinationPath]; the caller opens the result only on success. */
    override suspend fun clone(
        url: String,
        destinationPath: String,
        directoryName: String,
        branch: String?,
        config: List<String>,
        onStderr: ((String) -> Unit)?,
    ) {
        require(url.isNotBlank()) { "Enter a repository URL" }
        val arguments = buildList {
            add("clone")
            add("--progress")
            if (branch != null) {
                add("--branch")
                add(branch)
            }
            // After `--` the address is an address, never an option such as --upload-pack.
            add("--")
            add(url)
            add(directoryName)
        }
        val result = cli.runIn(destinationPath, arguments, onStderr, config)
        if (!result.succeeded) throw IOException(result.errorMessage)
    }

    private suspend fun expect(
        workspacePath: String,
        arguments: List<String>,
        onStderr: ((String) -> Unit)? = null,
        config: List<String> = emptyList(),
    ) {
        val result = cli.run(workspacePath, arguments, onStderr, config)
        if (!result.succeeded) throw IOException(result.errorMessage)
    }
}
