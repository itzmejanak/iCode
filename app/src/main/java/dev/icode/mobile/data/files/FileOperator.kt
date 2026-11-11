package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.measureTree
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.notices.FileNotices
import dev.icode.mobile.domain.notices.NoticePlaces
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.TrashedItem
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Each change an operation, an undo or a redo makes, one item at a time inside its task
 * (docs/explorer-gaps-plan.md §3.5–§3.8): moved — renamed in its place, carried between the two —
 * copied, put in Trash, restored, an empty folder removed or made. Each reports its item first, where
 * a Cancel stops it before it starts, and returns the step it did; what follows it — tabs, the tree,
 * recent files, a workspace closed, renamed or retired — is done under the mutation lock as it
 * happens, and done whatever stops the task afterwards.
 */
internal class FileOperator(
    private val stateHolder: ICodeStateHolder,
    val places: FilePlaces,
    val transfers: StorageTransfers,
    val trash: FileTrash,
    private val follower: FileFollower,
) {
    /** [from] to [to]: a rename in one place, carried between the two. */
    suspend fun move(from: String, to: String, progress: TaskRunner.Progress): FileStep.Moved {
        val crossing = transfers.crosses(from, to)
        if (!crossing) progress.item(File(from).name)
        val reopen = locked { follower.leaving(from) }
        try {
            if (crossing) said(transfers.carry(File(from), File(to), cut = true, progress).summary(cut = true), to)
            else places.of(from).move(from, to)
        } catch (stopped: Throwable) {
            locked { follower.stayed(from, reopen) }
            throw stopped
        }
        locked { follower.moved(from, to, reopen) }
        return FileStep.Moved(from, to)
    }

    /** [from] copied as [to], in its place or into the other. */
    suspend fun copy(from: String, to: String, progress: TaskRunner.Progress): FileStep.Made {
        if (transfers.crosses(from, to)) said(transfers.carry(File(from), File(to), cut = false, progress).summary(cut = false), to)
        else places.of(from).copy(from, to, progress::item)
        return FileStep.Made(to)
    }

    /** [location] into its place's Trash, measured first: a whole workspace is closed before and retired after. */
    suspend fun trash(location: String, progress: TaskRunner.Progress): FileStep.Trashed {
        progress.item(File(location).name)
        val bytes = measureTree(File(location), MEASURED_ENTRIES).bytes
        val reopen = locked { follower.leaving(location) }
        val item = try {
            trash.put(location, bytes, workspace = follower.isWorkspace(location))
        } catch (stopped: Throwable) {
            locked { follower.stayed(location, reopen) }
            throw stopped
        }
        locked { follower.removed(location) }
        return FileStep.Trashed(item.id, location)
    }

    /** [item] back from Trash, at [to]. */
    suspend fun restore(item: TrashedItem, to: String, progress: TaskRunner.Progress): FileStep.Restored {
        progress.item(item.name)
        trash.restore(item, to)
        return FileStep.Restored(item.id, to)
    }

    /** What an operation [staged] — an archive, what one held — put in place at [to], on its own volume: made there. */
    suspend fun settle(staged: String, to: String, progress: TaskRunner.Progress): FileStep.Made {
        progress.item(File(to).name)
        places.of(to).refusal(to)?.let { throw IOException(it) }
        Files.createDirectories(File(to).toPath().parent)
        Files.move(File(staged).toPath(), File(to).toPath())
        return FileStep.Made(to)
    }

    /** The empty folder [location] removed: one a merge moved everything out of, or one made and undone. */
    suspend fun removeEmpty(location: String, progress: TaskRunner.Progress): FileStep.Removed {
        progress.item(File(location).name)
        Files.delete(File(location).toPath())
        locked { follower.removed(location) }
        return FileStep.Removed(location)
    }

    /** The empty folder [location] made again, with any folder above it that has gone. */
    suspend fun makeFolder(location: String, progress: TaskRunner.Progress): FileStep.Made {
        progress.item(File(location).name)
        val path = File(location).toPath()
        Files.createDirectories(path.parent)
        Files.createDirectory(path)
        return FileStep.Made(location)
    }

    /** [location] is a folder holding nothing. */
    fun isEmptyFolder(location: String): Boolean =
        Files.isDirectory(File(location).toPath(), LinkOption.NOFOLLOW_LINKS) && File(location).list()?.isEmpty() == true

    /** What a transfer to [to] could not carry, said once it is done, where it went. */
    private suspend fun said(summary: String?, to: String) {
        summary?.let { stateHolder.tell(FileNotices.headsUp(it, NoticePlaces.of(stateHolder.current, to))) }
    }

    /** What follows a change, under the mutation lock: done even when the task is stopped meanwhile. */
    private suspend fun <T> locked(block: suspend () -> T): T = withContext(NonCancellable) { stateHolder.mutations.withLock { block() } }

    private companion object {
        /** A folder in Trash is measured this far; beyond, its size is unknown rather than wrong. */
        const val MEASURED_ENTRIES = 200_000
    }
}
