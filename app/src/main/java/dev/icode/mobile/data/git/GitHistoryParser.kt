package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitHistoryCommit
import dev.icode.mobile.domain.GitRef
import dev.icode.mobile.domain.GitRefKind
import java.io.IOException

/**
 * HEAD's history a page at a time (docs/git-history-plan.md §2): [arguments] asks `git log` for it,
 * [parse] reads it back. Both halves of the protocol live here, so they change together.
 */
internal object GitHistoryParser {
    /**
     * [count] commits after the first [skip], each before its parents (`--topo-order`, as the graph needs),
     * their names in full (`--decorate=full`), so a branch is told from a remote's and a tag by Git's own word.
     * `log.showSignature` set would print gpg's verdict between the records: `--no-show-signature` keeps it out.
     */
    fun arguments(skip: Int, count: Int): List<String> = listOf(
        "log", "--topo-order", "--decorate=full", "--no-show-signature", "-z",
        "--skip=$skip", "-n", "$count", "--format=$FORMAT", "HEAD",
    )

    /**
     * [output] holds [FIELDS] fields a commit, each ended by a NUL: Git forbids a NUL in a commit's
     * message, its author and its refs, so none can move a field's end.
     */
    fun parse(output: String): List<GitHistoryCommit> {
        if (output.isEmpty()) return emptyList()
        val fields = output.removeSuffix(NUL.toString()).split(NUL)
        if (fields.size % FIELDS != 0) throw IOException("Git's history did not read as $FIELDS fields a commit")
        return fields.chunked(FIELDS).map { field ->
            GitHistoryCommit(
                id = field[0],
                parents = field[1].split(' ').filter { it.isNotEmpty() },
                author = field[2],
                authoredAtEpochMillis = field[3].toLong() * 1000L,
                refs = refsOf(field[4]),
                subject = field[5],
                body = field[6].trimEnd('\n'),
            )
        }
    }

    /** `HEAD -> refs/heads/main, refs/remotes/origin/main, tag: refs/tags/v1.0`, as `--decorate=full` writes it. */
    private fun refsOf(decoration: String): List<GitRef> = decoration.split(", ").filter { it.isNotEmpty() }.mapNotNull { name ->
        when {
            name == HEAD -> GitRef(HEAD, GitRefKind.Head)
            name.startsWith(HEAD_OF) -> GitRef(name.removePrefix(HEAD_OF).removePrefix(BRANCH), GitRefKind.Head)
            name.startsWith(TAG) -> GitRef(name.removePrefix(TAG), GitRefKind.Tag)
            name.startsWith(BRANCH) -> GitRef(name.removePrefix(BRANCH), GitRefKind.Branch)
            // A remote's HEAD names which of its branches is its own: not a branch of its own.
            name.startsWith(REMOTE) && !name.endsWith("/HEAD") -> GitRef(name.removePrefix(REMOTE), GitRefKind.Remote)
            // The stash and notes are not names a commit is known by.
            else -> null
        }
    }

    private const val NUL = '\u0000'
    private const val FIELDS = 7
    /** Id, parents, author as Last Commit names one, author time, refs, subject, body. */
    private const val FORMAT = "%H%x00%P%x00%an <%ae>%x00%at%x00%D%x00%s%x00%b"
    private const val HEAD = "HEAD"
    private const val HEAD_OF = "HEAD -> "
    private const val BRANCH = "refs/heads/"
    private const val REMOTE = "refs/remotes/"
    private const val TAG = "tag: refs/tags/"
}
