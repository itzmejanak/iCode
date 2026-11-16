package dev.icode.mobile.data.git

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.GitRepositoryState
import dev.icode.mobile.domain.GitTransferLabel
import dev.icode.mobile.domain.notices.GitNotices
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.notices.NoticeRules
import java.util.concurrent.ConcurrentHashMap

/**
 * What a Git transfer tells as it ends (docs/engineering-rules.md): what a pull, a push or a
 * fetch did — counted where Git can say: HEAD before and after a pull, what the branch was ahead by
 * before a push, what its upstream gained by a fetch — or what it could not do, with Git's lines;
 * where it ran, as it was when it began; and, when it ran [NoticeRules.LONG_WORK_MILLIS] or more,
 * worth Android's shade should iCode be out of view by then. An automatic fetch says nothing when it
 * works, and a failure once, quietly, until a transfer in that workspace works again.
 */
internal class GitTransferOutcomes(
    private val stateHolder: ICodeStateHolder,
    private val git: GitTransferOperations,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Workspaces whose automatic fetch's failure was told. */
    private val quietlyFailed: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** A transfer of [label] in [workspace], begun now. */
    fun begin(workspace: String?, label: String, automatic: Boolean = false) = Begun(workspace, label, automatic)

    inner class Begun internal constructor(private val workspace: String?, private val label: String, private val automatic: Boolean) {
        private val before = stateHolder.current
        private val target = targetOf(before.gitRepository)
        private val place = NoticePlaces.git(before)
        private val at = clock()
        private val long: Boolean get() = clock() - at >= NoticeRules.LONG_WORK_MILLIS

        /** Git exited cleanly, its [transcript] printed; the workspace has been read again. */
        suspend fun succeeded(transcript: List<String>) {
            workspace?.let(quietlyFailed::remove)
            if (automatic) return
            val after = stateHolder.current.takeIf { it.activeWorkspaceLocation == workspace }?.gitRepository
            val upToDate = transcript.any { it.startsWith("Already up to date") || it.startsWith("Everything up-to-date") }
            val repository = before.gitRepository
            stateHolder.tell(
                when (label) {
                    GitTransferLabel.PULLING -> GitNotices.pulled(if (upToDate) 0 else pulled(repository, after), target, place, long)
                    GitTransferLabel.PUSHING -> GitNotices.pushed(
                        if (upToDate) 0 else repository?.takeIf { it.upstream != null }?.ahead?.takeIf { it > 0 },
                        target, place, long,
                    )
                    else -> GitNotices.fetched(after?.let { now -> repository?.let { (now.behind - it.behind).coerceAtLeast(0) } }, target, place, long)
                },
            )
        }

        /** Git failed, or never ran, for [reason]; what it printed is [transcript]. */
        suspend fun failed(reason: String, transcript: List<String> = emptyList()) {
            if (automatic && (workspace == null || !quietlyFailed.add(workspace))) return
            stateHolder.tell(GitNotices.transferFailed(label, reason, transcript, target, place, automatic))
        }

        /** HEAD before to HEAD after, counted by Git; null when either is not known. */
        private suspend fun pulled(before: GitRepositoryState?, after: GitRepositoryState?): Int? {
            val from = before?.lastCommit?.id ?: return null
            val to = after?.lastCommit?.id ?: return null
            if (from == to) return 0
            return runCatching { git.commitsBetween(workspace ?: return null, from, to) }.getOrNull()
        }
    }

    /** A clone, begun now: what it tells once it is whole, or what it could not do. */
    fun beginClone(name: String, url: String) = Clone(name, url)

    inner class Clone internal constructor(private val name: String, private val url: String) {
        private val at = clock()

        suspend fun succeeded(location: String) =
            stateHolder.tell(GitNotices.cloned(name, url, location, away = clock() - at >= NoticeRules.LONG_WORK_MILLIS))

        suspend fun failed(reason: String, transcript: List<String>) = stateHolder.tell(GitNotices.notCloned(name, url, reason, transcript))
    }

    private fun targetOf(repository: GitRepositoryState?) = GitNotices.Target(
        branch = repository?.takeUnless { it.detached }?.branch,
        upstream = repository?.upstream,
        remote = repository?.remoteName,
    )
}
