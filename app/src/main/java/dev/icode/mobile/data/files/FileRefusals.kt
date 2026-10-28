package dev.icode.mobile.data.files

import dev.icode.mobile.data.io.isAtOrUnder
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.data.workspace.WorkspaceUsage
import dev.icode.mobile.domain.WorkspaceNameRules
import dev.icode.mobile.domain.files.FileChange
import dev.icode.mobile.domain.files.FileClipboard
import dev.icode.mobile.domain.files.HeldItem
import dev.icode.mobile.domain.files.Reversal
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.files.TrashedItem
import dev.icode.mobile.domain.files.paths
import java.io.File

/**
 * Why an operation cannot start now (docs/explorer-gaps-plan.md §3.5), each of its items checked
 * before any is touched: phone storage needs All Files Access, an item gone is said gone, nothing
 * goes inside itself, a file is never a workspace, an item in use is not moved, a running task holds
 * its paths, and Trash's own folders stay where they are. Null when it can start.
 */
internal class FileRefusals(
    private val places: FilePlaces,
    private val trash: FileTrash,
    private val tasks: TaskRunner,
    private val usage: WorkspaceUsage,
    private val follower: FileFollower,
    /** Private's top: a folder put there is a workspace. */
    private val workspaces: File,
) {
    /** [items] moved ([move]) or copied into the folder [into]. */
    suspend fun transfer(items: List<HeldItem>, into: String, move: Boolean): String? {
        places.accessRefusal(items.map { it.location } + into)?.let { return it }
        places.of(into).folderRefusal(into)?.let { return it }
        if (trash.holds(into)) return "Only what is deleted goes in Trash"
        val asWorkspaces = into == workspaces.path
        if (asWorkspaces) FileClipboard(items, cut = move).notAWorkspace?.let { return it }
        for (item in items) {
            gone(item.location)?.let { return it }
            if (into.isAtOrUnder(item.location)) return "${item.name} cannot go inside itself"
            places.of(into).refusal(File(into, item.name).path)?.let { return it }
            if (asWorkspaces) WorkspaceNameRules.workspaceNameError(item.name)?.let { return it }
            (if (move) moving(item.location) else tasks.waitReason(item.location))?.let { return it }
        }
        return tasks.waitReason(into)
    }

    /** Each of [locations] copied beside itself. */
    fun copies(locations: List<String>): String? {
        places.accessRefusal(locations)?.let { return it }
        return locations.firstNotNullOfOrNull { location ->
            gone(location) ?: tasks.waitReason(location) ?: tasks.waitReason(File(location).parent.orEmpty())
        }
    }

    /** An archive made in [folder]: never at Private's top, where every item is a workspace. */
    fun archiveInto(folder: String): String? =
        if (folder == workspaces.path) "Only workspaces are at Private's top: copy it to Shared, or into a workspace, and compress it there" else null

    /** Each of [locations] renamed, moved or put in Trash where it is. */
    suspend fun changes(locations: List<String>): String? {
        places.accessRefusal(locations)?.let { return it }
        for (location in locations) (gone(location) ?: moving(location))?.let { return it }
        return null
    }

    /** What [archive] holds, into the folder [into]. */
    fun extraction(archive: String, into: String): String? {
        places.accessRefusal(listOf(archive, into))?.let { return it }
        gone(archive)?.let { return it }
        places.of(into).folderRefusal(into)?.let { return it }
        if (trash.holds(into)) return "Only what is deleted goes in Trash"
        return tasks.waitReason(archive) ?: tasks.waitReason(into)
    }

    /** Each of [items] back where it was. */
    fun restore(items: List<TrashedItem>): String? =
        places.accessRefusal(items.map { it.from }) ?: items.firstNotNullOfOrNull { tasks.waitReason(it.from) }

    /** The [names] given in [folder] — at Private's top, workspaces' names. */
    fun workspaceNames(folder: String, names: List<String>): String? =
        if (folder == workspaces.path) names.firstNotNullOfOrNull(WorkspaceNameRules::workspaceNameError) else null

    /** [change] undone, or redone: nothing it touches changed since (§3.8), is held, or in use. */
    suspend fun reversal(change: FileChange): String? {
        val reversals = change.reversals
        val paths = reversals.flatMap { it.paths }
        places.accessRefusal(paths)?.let { return it }
        change.refusal(::fileExists) { trash.item(it) != null }?.let { return it }
        paths.firstNotNullOfOrNull(tasks::waitReason)?.let { return it }
        for (reversal in reversals) {
            val leaving = when (reversal) {
                is Reversal.MoveBack -> reversal.from
                is Reversal.TrashMade -> reversal.location
                is Reversal.Restore, is Reversal.MakeFolder -> continue
            }
            moving(leaving)?.let { return it }
        }
        return null
    }

    /** Where a change says things went: "Private", "Shared", or the folder's name. */
    fun nameOf(folder: String): String = when (folder) {
        workspaces.path -> StoragePlace.Private.label
        places.storage.root.path -> StoragePlace.Shared.label
        else -> File(folder).name
    }

    /** [folder] is a place's top: Private's workspaces, or phone storage's own. */
    fun isPlaceTop(folder: String): Boolean = folder == workspaces.path || folder == places.storage.root.path

    /** [location] is gone, or is no item its place lets change. */
    private fun gone(location: String): String? =
        if (!fileExists(location)) "${File(location).name} no longer exists" else places.of(location).refusal(location)

    /** Why [location] cannot move or go now: Trash's own, in use, or a whole workspace something works in. */
    private suspend fun moving(location: String): String? =
        if (trash.holds(location)) "${File(location).name} holds iCode's Trash" else usage.holding(location, whole = follower.isWorkspace(location))
}
