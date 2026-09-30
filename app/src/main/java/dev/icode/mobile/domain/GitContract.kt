package dev.icode.mobile.domain

// Git (S5): every Git model — status, repository, commits, diffs, conflicts,
// operations and credentials — so the feature's vocabulary lives in one file.

/** How one line participates in a diff. */
enum class GitDiffLineKind { Context, Added, Removed }

data class GitDiffLine(
    val kind: GitDiffLineKind,
    /** One-based line number in the old file, or null for an added line. */
    val oldLine: Int?,
    /** One-based line number in the new file, or null for a removed line. */
    val newLine: Int?,
    val text: String,
) {
    /** `15 15`, `16  -`, `17  +` — the gutter label the diff row renders. */
    val gutter: String
        get() = when (kind) {
            GitDiffLineKind.Context -> "${oldLine ?: ""} ${newLine ?: ""}"
            GitDiffLineKind.Removed -> "${oldLine ?: ""}  -"
            GitDiffLineKind.Added -> "${newLine ?: ""}  +"
        }
}

/** One `@@` section of a diff. */
data class GitDiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    /** The literal `@@ -a,b +c,d @@` header, shown above the hunk. */
    val header: String,
    val lines: List<GitDiffLine>,
)

/** A complete diff for one path. */
data class GitFileDiff(
    val path: String,
    /** Set when the file was renamed or copied. */
    val oldPath: String? = null,
    val hunks: List<GitDiffHunk> = emptyList(),
    /** Git refused to produce a textual diff. */
    val binary: Boolean = false,
    /** True when comparing index against HEAD rather than worktree against index. */
    val staged: Boolean = false,
) {
    val isEmpty: Boolean get() = hunks.isEmpty() && !binary
    val addedLines: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.kind == GitDiffLineKind.Added } }
    val removedLines: Int get() = hunks.sumOf { hunk -> hunk.lines.count { it.kind == GitDiffLineKind.Removed } }
}

/** One side of a conflict, as delimited by Git's merge markers. */
data class GitConflictSection(
    val currentLabel: String,
    val current: List<String>,
    val incomingLabel: String,
    val incoming: List<String>,
    /** Zero-based index of the `<<<<<<<` line in the file. */
    val startLine: Int,
    /** Zero-based index of the `>>>>>>>` line in the file. */
    val endLine: Int,
)

/**
 * A conflicted file, its full text and every marker block inside it.
 *
 * The lines are kept so a resolution can be rebuilt exactly, preserving
 * everything outside the conflict regions untouched.
 */
data class GitConflictFile(
    val path: String,
    val lines: List<String>,
    val sections: List<GitConflictSection>,
) {
    val isResolved: Boolean get() = sections.isEmpty()
}

/** Which way a conflict section is resolved. */
enum class GitConflictChoice { Current, Incoming, Both }

/** An operation Git reports as in progress, which makes the tree not "clean". */
enum class GitInProgress { None, Merge, Rebase, CherryPick, Revert, Bisect }

/**
 * The outcome of a Git command.
 *
 * Failure carries Git's own message rather than a rewritten one, because the
 * product contract requires showing the actionable command error (PR-GIT-12).
 */
sealed interface GitOutcome<out T> {
    data class Success<T>(val value: T) : GitOutcome<T>
    data class Failure(val message: String, val exitCode: Int) : GitOutcome<Nothing>
}

/** Progress reported by a long-running Git operation such as clone or fetch. */
data class GitProgress(
    val phase: String,
    /** 0..1 where Git reported a percentage, otherwise null. */
    val fraction: Float? = null,
)

/**
 * State of a Git operation that takes long enough to show progress.
 *
 * Clone, fetch, pull and push all contact a network the app does not control,
 * so none of them may be reported as finished until Git exits (PR-GIT-12).
 */
/** What a Git transfer is called while it runs: its state's label, its notice's and its notification's. */
object GitTransferLabel {
    const val CLONING = "Cloning"
    const val FETCHING = "Fetching"
    const val PULLING = "Pulling"
    const val PUSHING = "Pushing"

    /** What moves data to or from a remote: background work of its own. */
    val ALL = setOf(CLONING, FETCHING, PULLING, PUSHING)
}

sealed interface GitOperationState {
    data object Idle : GitOperationState

    /**
     * [progress] is null until Git reports a percentage of its own, so the bar
     * shows activity rather than a number nobody measured. [output] is Git's
     * stderr as a terminal would render it.
     */
    data class Running(
        val label: String,
        val progress: GitProgress? = null,
        val output: List<String> = emptyList(),
    ) : GitOperationState

    data class Failed(
        val label: String,
        val reason: String,
        val output: List<String> = emptyList(),
    ) : GitOperationState

    data class Succeeded(
        val label: String,
        val output: List<String> = emptyList(),
    ) : GitOperationState

    /**
     * Stopped by the user before Git finished. A cancelled clone leaves
     * nothing: it was writing iCode's own staging folder, which goes with it.
     */
    data class Cancelled(
        val label: String,
        val output: List<String> = emptyList(),
    ) : GitOperationState

    /** Git's own output for whichever phase this operation is in. */
    val transcript: List<String>
        get() = when (this) {
            is Running -> output
            is Failed -> output
            is Succeeded -> output
            is Cancelled -> output
            Idle -> emptyList()
        }
}

/** How the user authenticates to a remote. */
enum class GitAuthMethod { PersonalAccessToken, SshKey }

/**
 * A Git hosting provider.
 *
 * Choosing one up front lets the screen show a single relevant action instead
 * of every host's button at once. [Other] covers self-hosted and anything not
 * listed, where the app can only offer the generic fields.
 */
enum class GitProvider(
    val displayName: String,
    val host: String,
    /** Where this provider accepts a new SSH public key. */
    val sshKeyUrl: String?,
    /** Where this provider issues personal access tokens. */
    val tokenUrl: String?,
) {
    GitHub(
        displayName = "GitHub",
        host = "github.com",
        sshKeyUrl = "https://github.com/settings/ssh/new",
        tokenUrl = "https://github.com/settings/tokens",
    ),
    GitLab(
        displayName = "GitLab",
        host = "gitlab.com",
        sshKeyUrl = "https://gitlab.com/-/user_settings/ssh_keys",
        tokenUrl = "https://gitlab.com/-/user_settings/personal_access_tokens",
    ),
    Bitbucket(
        displayName = "Bitbucket",
        host = "bitbucket.org",
        sshKeyUrl = "https://bitbucket.org/account/settings/ssh-keys/",
        tokenUrl = "https://bitbucket.org/account/settings/app-passwords/",
    ),
    Other(
        displayName = "Other / self-hosted",
        host = "",
        sshKeyUrl = null,
        tokenUrl = null,
    ),
}

/**
 * Authentication state, without ever carrying the secret itself.
 *
 * [httpsAccount] is a display string such as `octocat@github.com`; the token
 * stays in Keystore-encrypted storage and never reaches the snapshot, a log or
 * the screen.
 */
data class GitAuthState(
    val provider: GitProvider = GitProvider.GitHub,
    val method: GitAuthMethod = GitAuthMethod.PersonalAccessToken,
    val httpsAccount: String? = null,
    val hasSshKey: Boolean = false,
    val sshPublicKey: String? = null,
    /** Result of the last connection test, or null when never tested. */
    val lastTest: GitAuthTestResult? = null,
    /** What the page is doing, one thing at a time; each button says its own. */
    val work: GitAuthWork? = null,
) {
    val hasHttpsCredential: Boolean get() = httpsAccount != null
}

/** Generating installs the SSH client first when it is missing: that shows as the client's install. */
enum class GitAuthWork { GeneratingKey, Testing }

data class GitAuthTestResult(val succeeded: Boolean, val message: String)

/** A commit author's email, checked the same way by the credentials form and the backend. */
object GitIdentityRules {
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun isValidEmail(email: String): Boolean = EMAIL.matches(email)
}

data class GitChange(
    val status: String,
    val name: String,
    val path: String,
    val staged: Boolean,
) {
    /**
     * Only an unstaged edit, deletion or new file is thrown away: staged work
     * is unstaged first, and a conflict is resolved, never discarded.
     */
    val discardable: Boolean
        get() = !staged && status in setOf(GitStatusCodes.MODIFIED, GitStatusCodes.DELETED, GitStatusCodes.UNTRACKED)
}

data class GitCommitSummary(
    val id: String,
    val message: String,
    val author: String,
    val authoredAtEpochMillis: Long,
)

data class GitRepositoryState(
    val workTreePath: String,
    /** The checked-out branch; when [detached], Git's own "(HEAD detached at …)" instead. */
    val branch: String,
    val hasCommits: Boolean,
    val remoteName: String?,
    val authorName: String?,
    val authorEmail: String?,
    val branches: List<String>,
    /** Remote-tracking branches as `remotes/origin/main`, for checking one out. */
    val remoteBranches: List<String> = emptyList(),
    /** An operation Git has half-finished, which the user must end before moving on. */
    val inProgress: GitInProgress = GitInProgress.None,
    val lastCommit: GitCommitSummary?,
    /** Upstream branch name when one is configured, otherwise null. */
    val upstream: String? = null,
    /** Commits on the local branch the upstream does not have. */
    val ahead: Int = 0,
    /** Commits on the upstream the local branch does not have. */
    val behind: Int = 0,
    /** HEAD is a commit, not a branch: there is nothing to push or commit onto by name. */
    val detached: Boolean = false,
) {
    /** Remote synchronization is only meaningful once a remote exists. */
    val hasRemote: Boolean get() = remoteName != null
}

/** Git status codes shared by Source Control, Explorer decorations and diffs. */
object GitStatusCodes {
    const val ADDED = "A"
    const val MODIFIED = "M"
    const val DELETED = "D"
    const val UNTRACKED = "?"
    const val CONFLICTED = "U"
}
