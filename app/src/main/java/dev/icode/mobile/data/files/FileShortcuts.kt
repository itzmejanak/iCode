package dev.icode.mobile.data.files

import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.data.workspace.StoredRecentDocument
import dev.icode.mobile.data.workspace.WorkspaceStore
import dev.icode.mobile.data.workspace.touchRecentDocument
import dev.icode.mobile.domain.FilesIntent
import dev.icode.mobile.domain.files.BrowsedItem
import dev.icode.mobile.domain.files.FavoriteFolder
import dev.icode.mobile.domain.files.FileStep
import dev.icode.mobile.domain.files.StorageInsights
import dev.icode.mobile.domain.files.StoragePlace
import dev.icode.mobile.domain.notices.FileNotices
import dev.icode.mobile.domain.notices.Notice
import java.io.File

/**
 * The explorers' ways back (docs/explorer-gaps-plan.md §3.14), kept in the workspace store: Recent,
 * the files opened in iCode — by the editor, a viewer, Open with — in workspaces and on phone
 * storage, newest first, a save putting its file on top; and Favorites, folders pinned app-wide.
 * Both follow iCode's own moves. What went is kept and not shown, so a restore brings it back. The
 * editor's empty page and the explorer read the one list.
 */
internal class FileShortcuts(
    private val stateHolder: ICodeStateHolder,
    private val store: WorkspaceStore,
    private val paths: WorkspacePaths,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The store is read, changed and written whole: one change at a time. */
    private val lock = Any()

    /** [location] opened, or saved: Recent's top. */
    suspend fun opened(location: String) = change(FileNotices.recentNotKept()) {
        store.updateRecentDocuments { touchRecentDocument(it, StoredRecentDocument(location, clock()), paths::workspaceOf) }
    }

    suspend fun handle(intent: FilesIntent.Shortcut) {
        when (intent) {
            is FilesIntent.Opened -> opened(intent.location)
            is FilesIntent.Favorite -> change(FileNotices.favoritesNotKept()) {
                store.updateFavorites { pinned -> (pinned - intent.location).let { if (intent.pinned) it + intent.location else it } }
            }
        }
    }

    /**
     * iCode's own [steps], as they are kept — in order, under the mutation lock: what moved is
     * followed, by itself or with a folder above it; what went stays as it was.
     */
    fun follow(steps: List<FileStep>) {
        val moves = steps.filterIsInstance<FileStep.Moved>()
        fun moved(location: String) = StorageInsights.after(location, moves) ?: location
        if (moves.isNotEmpty()) runCatching {
            synchronized(lock) {
                store.updateRecentDocuments { recent -> recent.map { it.copy(location = moved(it.location)) }.distinctBy { it.location } }
                store.updateFavorites { pinned -> pinned.map(::moved).distinct() }
            }
        }.onFailure { error -> stateHolder.failureText("files", "Recent files and Favorites could not follow the change", error) }
        publish()
    }

    /** As the screens show them: Recent's files, as the explorer lists them, and the pinned folders that are there now. */
    fun publish() {
        val (stored, pinned) = synchronized(lock) {
            runCatching { store.loadRecentDocuments() to store.loadFavorites() }.getOrDefault(emptyList<StoredRecentDocument>() to emptyList())
        }
        // Opened in a workspace, or on phone storage by a viewer or Open with.
        val recent = stored.mapNotNull { kept ->
            val file = File(kept.location).takeIf { it.isFile } ?: return@mapNotNull null
            val place = if (paths.workspaceOf(kept.location) != null) StoragePlace.Private else StoragePlace.Shared
            BrowsedItem(file.name, kept.location, paths.runtimePathOf(kept.location), place, folder = false, bytes = file.length(), changedAt = kept.lastOpenedAt)
        }
        val favorites = pinned.filter { File(it).isDirectory }.map { FavoriteFolder(File(it).name, it, paths.runtimePathOf(it)) }
        stateHolder.update { it.copy(explorer = it.explorer.copy(recent = recent, favorites = favorites)) }
    }

    private suspend fun change(failed: Notice, write: () -> Unit) {
        runCatching { synchronized(lock, write) }.onFailure { error -> stateHolder.tell(failed, "files", error) }
        publish()
    }
}
