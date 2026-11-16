package dev.icode.mobile.data.workspace

import android.os.FileObserver
import dev.icode.mobile.domain.FileKind
import dev.icode.mobile.domain.ICodeSnapshot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live notice of changes in the workspaces while iCode is on screen: the
 * folder that holds them, the folders the Explorer shows and the folders of
 * open files, watched with inotify — so a workspace, file or folder a terminal
 * or an agent makes appears. Measured on the phone (2026-09-28): files a
 * terminal writes arrive (create, modify, close-after-write).
 *
 * A burst of writes — an agent at work — becomes one notice: after a second of
 * quiet, or at most every five seconds while it goes on.
 */
internal class WorkspaceWatcher(
    scope: CoroutineScope,
    /** [structure] when entries came or went, not only contents changed. */
    private val onSettled: (structure: Boolean) -> Unit,
) {
    private val events = Channel<Boolean>(Channel.UNLIMITED)
    private var observer: FileObserver? = null
    private var watched: Set<String> = emptySet()

    init {
        scope.launch { settle() }
    }

    @Synchronized
    fun watch(folders: Set<File>) {
        val paths = folders.filter { it.isDirectory }.map { it.path }.toSet()
        if (paths == watched) return
        observer?.stopWatching()
        watched = paths
        observer = paths.takeIf { it.isNotEmpty() }?.let { targets ->
            object : FileObserver(targets.map(::File), EVENTS) {
                // A null path is the folder itself (deleted or moved): the tree changed.
                override fun onEvent(event: Int, path: String?) {
                    events.trySend(path == null || event and STRUCTURE != 0)
                }
            }.also { it.startWatching() }
        }
    }

    @Synchronized
    fun stop() {
        observer?.stopWatching()
        observer = null
        watched = emptySet()
    }

    private suspend fun settle() {
        while (true) {
            var structure = events.receive()
            val first = System.currentTimeMillis()
            var last = first
            while (true) {
                val wait = settleDelay(first, last, System.currentTimeMillis())
                if (wait <= 0) break
                val next = withTimeoutOrNull(wait) { events.receive() } ?: break
                structure = structure || next
                last = System.currentTimeMillis()
            }
            onSettled(structure)
        }
    }

    private companion object {
        const val STRUCTURE = FileObserver.CREATE or FileObserver.DELETE or FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or FileObserver.DELETE_SELF or FileObserver.MOVE_SELF
        const val EVENTS = STRUCTURE or FileObserver.CLOSE_WRITE
    }
}

/** A second of quiet ends a burst; a burst that never quiets is still reported every five. */
internal const val WATCH_QUIET_MILLIS = 1_000L
internal const val WATCH_MAX_WAIT_MILLIS = 5_000L

/** How long to keep waiting for more events of a burst that began at [first] and was last seen at [last]. */
internal fun settleDelay(first: Long, last: Long, now: Long): Long =
    minOf(last + WATCH_QUIET_MILLIS, first + WATCH_MAX_WAIT_MILLIS) - now

/**
 * The folders worth watching: [workspacesRoot], where workspaces come and go, and in the open
 * workspace its top, every expanded folder, and each open file's folder.
 */
internal fun watchedFolders(snapshot: ICodeSnapshot, workspacesRoot: File): Set<File> {
    val browsed = snapshot.filesBrowser?.path?.let(::File)
    val root = snapshot.activeWorkspaceLocation ?: return setOfNotNull(workspacesRoot, browsed)
    return buildSet {
        add(workspacesRoot)
        browsed?.let(::add)
        add(File(root))
        snapshot.files.filter { it.kind == FileKind.Folder && it.expanded }.forEach { add(File(it.path)) }
        snapshot.openDocuments.mapNotNull { File(it.location).parentFile }.forEach(::add)
    }
}
