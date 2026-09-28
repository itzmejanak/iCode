package dev.icode.mobile.data.git

import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.domain.GitChange
import dev.icode.mobile.domain.GitCommitSummary
import dev.icode.mobile.domain.GitRepositoryState

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

    fun parse(workspacePath: String, output: String): GitWorkspaceSnapshot {
        // rev-parse failing is the normal "not a repository" answer, not an error.
        val sections = output.split(RECORD_SEPARATOR)
        val topLevel = sections.getOrNull(0)?.trim().orEmpty()
        if (topLevel.isEmpty()) return GitWorkspaceSnapshot(null, emptyList())
        // Git ran inside the guest, so it reports the bind mount's path. Every
        // caller compares this against Android paths — the Explorer decorates
        // files by it — so it is translated back before it leaves this parser.
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

    private const val SHORT_ID_LENGTH = 12
    /** See GitCommandLine: bind-mounted Android paths never match ownership. */
    private const val SAFE_DIRECTORY = "-c 'safe.directory=*'"
    private const val RECORD_SEPARATOR = '\u001E'
    private const val UNIT_SEPARATOR = '\u001F'
    private const val LOCAL_REF_PREFIX = "refs/heads/"
    private const val REMOTE_REF_PREFIX = "refs/remotes/"

    /**
     * One process, six facts, separated by RS.
     *
     * `rev-parse` runs first: an empty first section means "not a
     * repository" and the rest is ignored. The overall exit code is
     * deliberately `status`'s, not the last command's, because `git config`
     * exits non-zero simply when an identity is unset.
     */
    val SCRIPT = buildString {
        append("git ")
        append(SAFE_DIRECTORY)
        append(" rev-parse --show-toplevel 2>/dev/null; ")
        append("printf '\\036'; ")
        append("git -c core.quotepath=false ")
        append(SAFE_DIRECTORY)
        append(" --no-optional-locks status --porcelain=v2 --branch -z; S=$?; ")
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
