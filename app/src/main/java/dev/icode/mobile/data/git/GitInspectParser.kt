package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitSigningRules

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
 * The repository picture in one process: [SCRIPT] prints its sections, [parse]
 * reads them back. Both halves of the protocol live here, so they change together.
 *
 * Starting PRoot costs about a second, so a refresh that needs six Git facts
 * runs one script rather than six commands.
 */
internal object GitInspectParser {

    /** The branch a new repository starts on, and the name shown when Git reported none. */
    const val DEFAULT_BRANCH = "main"

    /**
     * [output] is [SCRIPT]'s stdout. Seven NUL-terminated sections come first;
     * none can hold a NUL (Git forbids it in paths, refs, config values and
     * commit headers), so a path containing a newline or any other byte cannot
     * move a section boundary. Status comes last and runs to the end: its own
     * records are NUL-terminated too. UTF-8 never encodes another character
     * with a zero byte, so splitting the decoded text is splitting the bytes.
     *
     * Git ran in the runtime, so it names the repository's top by its guest
     * path; [locationOf] turns that into the location every caller compares
     * with — the Explorer decorates files by it. A top outside the workspaces
     * (a repository made at the runtime's `/`) is not this workspace's.
     */
    fun parse(output: String, locationOf: (guestPath: String) -> String?): GitWorkspaceSnapshot {
        val sections = output.split(NUL, limit = SECTIONS + 1)
        // rev-parse failing is the normal "not a repository" answer, not an error.
        val topLevel = sections.getOrNull(TOP_LEVEL).orEmpty().removeSuffix("\n")
        if (topLevel.isEmpty()) return GitWorkspaceSnapshot(null, emptyList())
        val hostTopLevel = locationOf(topLevel) ?: return GitWorkspaceSnapshot(null, emptyList())

        val status = GitPorcelainParser.parse(sections.getOrNull(STATUS).orEmpty())
        val allRefs = sections.getOrNull(REFS).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val branches = allRefs
            .filter { it.startsWith(LOCAL_REF_PREFIX) }
            .map { it.removePrefix(LOCAL_REF_PREFIX) }
        // origin/HEAD is a symbolic pointer, not a branch anyone checks out.
        val remoteRefs = allRefs
            .filter { it.startsWith(REMOTE_REF_PREFIX) }
            .map { it.removePrefix(REMOTE_REF_PREFIX) }
            .filterNot { it.endsWith("/HEAD") }
        val remote = sections.getOrNull(REMOTES).orEmpty().lines().firstOrNull { it.isNotBlank() }?.trim()
        val authorName = sections.getOrNull(USER_NAME)?.trim()?.takeIf { it.isNotEmpty() }
        val authorEmail = sections.getOrNull(USER_EMAIL)?.trim()?.takeIf { it.isNotEmpty() }
        val lastCommit = parseLastCommit(sections.getOrNull(LAST_COMMIT).orEmpty())
        val markers = sections.getOrNull(MARKERS).orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val inProgress = GitInProgressDetector.detect(
            hasMergeHead = "MERGE_HEAD" in markers,
            hasRebase = "rebase-merge" in markers || "rebase-apply" in markers,
            hasCherryPick = "CHERRY_PICK_HEAD" in markers,
            hasRevert = "REVERT_HEAD" in markers,
            hasBisect = "BISECT_LOG" in markers,
        )

        // A detached HEAD is said the way Git says it; naming a branch that is
        // not checked out would send a commit or a push somewhere unintended.
        val detached = status.header.detached
        val branch = when {
            detached -> "(HEAD detached at ${status.header.oid.orEmpty().take(DETACHED_ID_LENGTH)})"
            else -> status.header.branch ?: DEFAULT_BRANCH
        }

        return GitWorkspaceSnapshot(
            repository = GitRepositoryState(
                workTreePath = hostTopLevel,
                branch = branch,
                hasCommits = status.header.hasCommits,
                remoteName = remote,
                authorName = authorName,
                authorEmail = authorEmail,
                // An unborn branch has no ref yet, but it is the branch.
                branches = branches.ifEmpty { if (detached) emptyList() else listOf(branch) },
                remoteBranches = remoteRefs,
                inProgress = inProgress,
                lastCommit = lastCommit,
                upstream = status.header.upstream,
                ahead = status.header.ahead,
                behind = status.header.behind,
                detached = detached,
            ),
            changes = status.changes,
        )
    }

    /**
     * Hash, author time, author, the signature's verdict, key and signer, then the subject, one per
     * line: none of them can hold a newline. The verdict is `-` where GnuPG is not installed.
     */
    private fun parseLastCommit(section: String): GitCommitSummary? {
        val fields = section.split('\n', limit = 7)
        if (fields.size < 7 || fields[0].isBlank()) return null
        return GitCommitSummary(
            id = fields[0].take(GitCommitSummary.ID_LENGTH),
            message = fields[6].removeSuffix("\n"),
            author = fields[2],
            authoredAtEpochMillis = (fields[1].trim().toLongOrNull() ?: 0L) * 1000L,
            signature = GitSigningRules.signature(fields[3], fields[4], fields[5]),
        )
    }

    /** What `git branch` shows for a detached HEAD. */
    private const val DETACHED_ID_LENGTH = 7
    /** See GitCommandLine: bind-mounted Android paths never match ownership. */
    private const val SAFE_DIRECTORY = "-c 'safe.directory=*'"
    private const val NUL = '\u0000'
    private const val LOCAL_REF_PREFIX = "refs/heads/"
    private const val REMOTE_REF_PREFIX = "refs/remotes/"

    // Section order in [SCRIPT]; status is last and runs to the end.
    private const val TOP_LEVEL = 0
    private const val REFS = 1
    private const val REMOTES = 2
    private const val USER_NAME = 3
    private const val USER_EMAIL = 4
    private const val LAST_COMMIT = 5
    private const val MARKERS = 6
    private const val STATUS = 7
    private const val SECTIONS = 7

    /**
     * One process, eight facts. `rev-parse` runs first: an empty first section
     * means "not a repository" and the rest is ignored. Status runs last, so
     * the script's exit code is status's — `git config` exits non-zero simply
     * because an identity is unset, which is no failure.
     */
    val SCRIPT = buildString {
        append("git ")
        append(SAFE_DIRECTORY)
        append(" rev-parse --show-toplevel 2>/dev/null; printf '\\0'; ")
        append("git ")
        append(SAFE_DIRECTORY)
        // The full refname, not the short one: only `refs/heads/` versus
        // `refs/remotes/` distinguishes a local branch from a remote one.
        append(" branch -a --format='%(refname)' 2>/dev/null; printf '\\0'; ")
        append("git remote 2>/dev/null; printf '\\0'; ")
        append("git config user.name 2>/dev/null; printf '\\0'; ")
        append("git config user.email 2>/dev/null; printf '\\0'; ")
        // The signature, in Git's own verdict, only where GnuPG can check one: without it Git says N
        // of a signed commit too.
        append("S='-%n%n'; command -v gpg >/dev/null 2>&1 && S='%G?%n%GK%n%GS'; git ")
        append(SAFE_DIRECTORY)
        append(" log -1 --format=\"%H%n%at%n%an <%ae>%n\$S%n%s\" 2>/dev/null; printf '\\0'; ")
        // An interrupted merge, rebase, cherry-pick, revert or bisect is
        // recorded as a file in .git. Reading them is how Git itself knows,
        // and it costs nothing compared with another command.
        append("G=\$(git ")
        append(SAFE_DIRECTORY)
        append(" rev-parse --git-dir 2>/dev/null); ")
        append("for f in MERGE_HEAD rebase-merge rebase-apply CHERRY_PICK_HEAD REVERT_HEAD BISECT_LOG; do ")
        append("[ -e \"\$G/\$f\" ] && printf '%s\\n' \"\$f\"; ")
        append("done; printf '\\0'; ")
        append("git -c core.quotepath=false ")
        append(SAFE_DIRECTORY)
        // Every new file, not one entry per new folder — as VS Code asks — so each can be seen and staged.
        append(" --no-optional-locks status --porcelain=v2 --branch --untracked-files=all -z")
    }
}
