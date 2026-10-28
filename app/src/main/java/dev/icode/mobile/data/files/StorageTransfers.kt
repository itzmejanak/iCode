package dev.icode.mobile.data.files

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.storage.FileTransferTree
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.StorageTransfer
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.countOf
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Copy and cut between iCode's storage and phone storage (docs/storage-explorer-plan.md §4.1), one
 * item at a time inside the operation's task, with Import's engine: refused before anything is
 * written when it would not fit — or, for a cut, when not all of it can go. Into iCode's storage a
 * folder is staged and put in place once complete, a repository's checkout restored as Git
 * recorded it; onto phone storage, links and names that differ only in case are skipped and
 * counted. A cut deletes its source only once all of it was copied. Stopped or failed, nothing of
 * the copy is left, and the source is as it was.
 */
internal class StorageTransfers(
    private val stateHolder: ICodeStateHolder,
    paths: WorkspacePaths,
    private val git: GitCheckoutOperations,
    private val storage: PhoneStorage,
    /** Makes room on the workspaces' volume, having Android clear other apps' caches if need be. */
    private val reserveSpace: (bytes: Long) -> Boolean,
    /** Space the volume has, counting what Android can free: said when there is too little. */
    private val freeBytes: () -> Long,
) {
    private val staging = WorkspaceStaging(paths.root)

    /**
     * Why [bytes] cannot be written now, or null: room is made on the workspaces' volume, which phone
     * storage shares (`/data/media`), Android clearing other apps' caches if need be.
     */
    fun roomRefusal(bytes: Long): String? =
        if (reserveSpace(bytes)) null else "Not enough storage: ${ByteSize.format(bytes)} required, ${ByteSize.format(freeBytes())} free"

    /** [location] and [destination] are in different places: one in iCode's storage, the other on phone storage. */
    fun crosses(location: String, destination: String): Boolean = storage.contains(location) != storage.contains(destination)

    /**
     * [source] carried to [target], in the other place, its entries reported to [progress] as paths
     * from the folder [source] is in; a [cut] removes [source] once all of it is there.
     */
    suspend fun carry(source: File, target: File, cut: Boolean, progress: TaskRunner.Progress): Carried {
        val context = currentCoroutineContext()
        if (!storage.hasAccess()) throw IOException(FilePlaces.NO_ACCESS)
        val toShared = storage.contains(target.path)
        val tree = FileTransferTree(source)
        if (!tree.top.copyable) throw IOException("${source.name} is a link: links cannot be copied to or from phone storage")
        val plan = StorageTransfer.plan(tree, caseInsensitive = toShared) { context.ensureActive() }
        if (cut && plan.skipped.isNotEmpty()) {
            throw IOException("${source.name} holds ${notCarried(plan.skipped.size, toShared)} — copy it instead")
        }
        roomRefusal(plan.bytes)?.let { throw IOException(it) }
        // Into iCode's storage a folder is staged, as Import's was: Git's repair runs before it is seen,
        // and a process ended midway leaves only what the next start sweeps.
        val staged = if (!toShared && tree.top.folder) staging.reserve("import") else null
        try {
            StorageTransfer.copy(tree, plan, staged ?: target) { progress.item(if (it.isEmpty()) source.name else "${source.name}/$it") }
            val unrepaired = staged?.let { settle(it, target, progress) }
            return Carried(target, plan.skipped.size, toShared, unrepaired, if (cut) removeSource(source, progress) else null)
        } catch (stopped: Throwable) {
            staged?.let { withContext(NonCancellable) { runCatching { staging.discard(it) } } }
            throw stopped
        }
    }

    /** [staged], a repository's checkout restored, put in place as [target]; why Git could not restore it, or null. */
    private suspend fun settle(staged: File, target: File, progress: TaskRunner.Progress): String? {
        val unrepaired = if (File(staged, ".git").isDirectory) restoreCheckout(staged, progress) else null
        staging.complete(staged, target.name, target.parentFile ?: throw IOException("${target.name} has no folder to go in"))
        return unrepaired
    }

    /**
     * A cut's source, once all of it was copied: deleted without Cancel, since half of it gone would
     * be neither moved nor kept. Why some of it is left, or null.
     */
    private suspend fun removeSource(source: File, progress: TaskRunner.Progress): String? = withContext(NonCancellable) {
        progress.stage("Removing the original…")
        runCatching { deleteTree(source) }.exceptionOrNull()
            ?.let { stateHolder.failureText("workspace", "The original could not be removed", it) }
    }

    /** Null once restored; else why it could not be — the copy completes either way. */
    private suspend fun restoreCheckout(top: File, progress: TaskRunner.Progress): String? = try {
        if (!git.isAvailable()) {
            progress.stage("Installing Git…")
            val installed = git.provision()
            if (!git.isAvailable()) return (installed as? GitOutcome.Failure)?.message ?: "Git could not be installed"
        }
        progress.stage("Restoring Git's executables and links")
        git.restoreCheckout(top.path)
        null
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        stateHolder.failureText("git", "Git could not restore the checkout", error)
    }

    /** One item carried: where it is now, and what of it was not. */
    class Carried(
        val target: File,
        val skipped: Int,
        val toShared: Boolean,
        val unrepaired: String?,
        /** A cut's source that could not be removed entirely: why. */
        val leftBehind: String?,
    ) {
        /**
         * What was not carried, if anything: "Copied app without 3 items phone storage cannot hold (…)".
         * A transfer that carried everything says nothing, as a paste in one place does.
         */
        fun summary(cut: Boolean): String? {
            if (skipped == 0 && unrepaired == null && leftBehind == null) return null
            return buildString {
                append(if (cut) "Moved " else "Copied ").append(target.name)
                if (skipped > 0) append(" without ").append(notCarried(skipped, toShared))
                if (unrepaired != null) append(". Git's executables and links were not restored: ").append(unrepaired)
                if (leftBehind != null) append(". The original could not be removed entirely: ").append(leftBehind)
            }
        }
    }

    private companion object {
        /** "3 items phone storage cannot hold (links, or names that differ only in case)". */
        fun notCarried(count: Int, toShared: Boolean) = countOf(count, "item") +
            if (toShared) " phone storage cannot hold (links, or names that differ only in case)" else " that cannot be copied (links, or names a folder cannot hold)"
    }
}
