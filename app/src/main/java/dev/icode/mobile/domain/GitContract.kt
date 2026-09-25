package dev.icode.mobile.domain

/**
 * Git types that the CLI-backed service produces.
 *
 * These live apart from [WorkbenchSnapshot] so the Git feature can grow its own
 * vocabulary — diffs, hunks, conflicts — without widening the shared contract
 * every time.
 */

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

    /** Git's own output for whichever phase this operation is in. */
    val transcript: List<String>
        get() = when (this) {
            is Running -> output
            is Failed -> output
            is Succeeded -> output
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
    val sshAvailable: Boolean = true,
    /** Result of the last connection test, or null when never tested. */
    val lastTest: GitAuthTestResult? = null,
    val busy: Boolean = false,
) {
    val hasHttpsCredential: Boolean get() = httpsAccount != null
}

data class GitAuthTestResult(val succeeded: Boolean, val message: String)
