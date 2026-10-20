package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.storage.FileTransferTree
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.StorageTransfer
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ItemClipboard
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.countOf
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Copy and cut between iCode's storage and phone storage (docs/storage-explorer-plan.md §4.1), as a
 * task with Import's engine: counted first, and refused before anything is written when it would not
 * fit — or, for a cut, when not all of it can go. Into iCode's storage a folder is staged and put in
 * place once complete, a repository's checkout restored as Git recorded it; onto phone storage,
 * links and names that differ only in case are skipped and counted. A cut deletes its source only
 * once all of it was copied. Stopped or failed, nothing of the copy is left, and the source is as it was.
 */
internal class StorageTransfers(
    private val stateHolder: ICodeStateHolder,
    private val tasks: TaskRunner,
    paths: WorkspacePaths,
    private val git: GitCheckoutOperations,
    private val storage: PhoneStorage,
    /** Makes room on the workspaces' volume, having Android clear other apps' caches if need be. */
    private val reserveSpace: (bytes: Long) -> Boolean,
    /** Space the volume has, counting what Android can free: said when there is too little. */
    private val freeBytes: () -> Long,
) {
    private val staging = WorkspaceStaging(paths.root)

    /** [location] and [destination] are in different places: one in iCode's storage, the other on phone storage. */
    fun crosses(location: String, destination: String): Boolean = storage.contains(location) != storage.contains(destination)

    /**
     * [held] into the folder [destination], in the other place. [landed] runs under the mutation lock
     * when the task ends: with where it is now when all of it was carried, else with null.
     */
    fun paste(held: ItemClipboard, destination: String, landed: suspend (target: File?) -> Unit) {
        val toShared = storage.contains(destination)
        val source = File(held.location)
        // A cut keeps its name — refused before when it is taken — and a copy takes the first free one.
        val target = if (held.cut) File(destination, held.name) else freeName(File(destination).toPath(), held.name, held.folder).toFile()
        // Into iCode's storage a folder is staged, as Import's was: Git's repair runs before it is seen,
        // and a process ended midway leaves only what the next start sweeps.
        val staged = if (!toShared && held.folder) staging.reserve("import") else null
        var done: Transferred? = null
        tasks.start(
            title = if (held.cut) "Moving…" else "Copying…",
            subject = held.name,
            paths = listOfNotNull(held.location, target.path, staged?.path),
            cancellable = true,
            failure = "Unable to ${if (held.cut) "move" else "copy"} ${held.name}",
            work = { progress ->
                val context = currentCoroutineContext()
                if (!storage.hasAccess()) throw IOException(NO_ACCESS)
                val tree = FileTransferTree(source)
                if (!tree.top.copyable) throw IOException("${held.name} is a link: links cannot be copied to or from phone storage")
                val plan = StorageTransfer.plan(tree, caseInsensitive = toShared) { context.ensureActive() }
                if (held.cut && plan.skipped.isNotEmpty()) {
                    throw IOException("${held.name} holds ${notCarried(plan.skipped.size, toShared)} — copy it instead")
                }
                if (!reserveSpace(plan.bytes)) {
                    throw IOException("Not enough storage: ${ByteSize.format(plan.bytes)} required, ${ByteSize.format(freeBytes())} free")
                }
                progress.counted(if (tree.top.folder) plan.entries.size.toLong() else 1L)
                // Each item as a path from the folder it is in, as a copy or a delete says it.
                StorageTransfer.copy(tree, plan, staged ?: target) { progress.item(if (it.isEmpty()) held.name else "${held.name}/$it") }
                val unrepaired = staged?.let { settle(it, target, progress) }
                done = Transferred(target, plan.skipped.size, toShared, unrepaired, if (held.cut) removeSource(source, progress) else null)
            },
            finished = {
                val result = done
                if (result == null) staged?.let { runCatching { staging.discard(it) } }
                result?.summary(held)?.let { stateHolder.notify(NoticeTone.HeadsUp, it) }
                landed(result?.target)
            },
        )
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

    private class Transferred(
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
        fun summary(held: ItemClipboard): String? {
            if (skipped == 0 && unrepaired == null && leftBehind == null) return null
            return buildString {
                append(if (held.cut) "Moved " else "Copied ").append(target.name)
                if (skipped > 0) append(" without ").append(notCarried(skipped, toShared))
                if (unrepaired != null) append(". Git's executables and links were not restored: ").append(unrepaired)
                if (leftBehind != null) append(". The original could not be removed entirely: ").append(leftBehind)
            }
        }
    }

    private companion object {
        const val NO_ACCESS = "Allow access to phone storage first"

        /** "3 items phone storage cannot hold (links, or names that differ only in case)". */
        fun notCarried(count: Int, toShared: Boolean) = countOf(count, "item") +
            if (toShared) " phone storage cannot hold (links, or names that differ only in case)" else " that cannot be copied (links, or names a folder cannot hold)"
    }
}
