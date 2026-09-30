package dev.icode.mobile.data.workspace

import android.content.Context
import android.content.SharedPreferences
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.notices.NoticeStore
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.domain.AppNotice
import dev.icode.mobile.domain.EarlierWorkspace
import java.io.File

/**
 * Brings an install into the shape workspaces in iCode's storage need (docs/workspace-storage-plan.md
 * §5), before anything reads the workspaces. Every start: what a process that ended mid-clone or
 * mid-import left in iCode's staging goes. Once: the earlier design's experiment leaves the
 * workspaces' folder, the folders opened in place on phone storage become one notice — to bring
 * them in with Import; their folders are never touched — and what was kept about them is
 * forgotten. Marked done in its own preferences; a failure is logged and tried again at the next
 * start, and never keeps iCode from starting.
 */
internal class WorkspaceStorageMigration(
    /** Where "done" is recorded, apart from everything it changes. */
    private val marker: SharedPreferences,
    private val paths: WorkspacePaths,
    /** The runtime's root filesystem, where old launches made one mount point per workspace. */
    private val rootfs: File,
    private val store: WorkspaceStore,
    private val addNotice: (AppNotice) -> Unit,
    private val forgetSettings: suspend (location: String) -> Unit,
) {
    constructor(
        context: Context,
        paths: WorkspacePaths,
        rootfs: File,
        store: WorkspaceStore,
        notices: NoticeStore,
        forgetSettings: suspend (location: String) -> Unit,
    ) : this(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE), paths, rootfs, store, notices::add, forgetSettings)

    suspend fun run() {
        runCatching { WorkspaceStaging(paths.root).sweep() }
            .onFailure { error -> BackendLog.w("workspace", "iCode's staging could not be cleared", error) }
        if (marker.getInt(KEY_LAYOUT, 0) >= LAYOUT) return
        runCatching {
            // S3b's private mount folders, named by the SHA-256 of a phone-storage path: its only
            // writer (WorkspaceStorage) is gone, and nothing else names a folder so.
            paths.root.listFiles().orEmpty().filter { EXPERIMENT.matches(it.name) }.forEach { deleteTree(it) }
            // One bind of the whole folder replaces a mount point per workspace; only empty ones go.
            File(rootfs, WorkspacePaths.GUEST_ROOT.removePrefix("/")).listFiles().orEmpty()
                .filter { it.isDirectory && it.list()?.isEmpty() == true }
                .forEach { it.delete() }
            val earlier = store.earlierRecents().filter { paths.workspaceOf(it.location) == null }
            store.forgetUnless { paths.workspaceOf(it) == it }
            earlier.forEach { forgetSettings(it.location) }
            if (earlier.isNotEmpty()) {
                addNotice(
                    AppNotice.WorkspacesMovedIn(
                        id = NOTICE_ID,
                        at = System.currentTimeMillis(),
                        read = false,
                        earlier = earlier.map { EarlierWorkspace(it.name, it.displayPath) },
                    ),
                )
            }
            store.clearEarlierRecents()
            marker.commitOrFail("Unable to record the workspaces' new home") { putInt(KEY_LAYOUT, LAYOUT) }
        }.onFailure { error ->
            BackendLog.w("workspace", "Moving to workspaces in iCode's storage did not finish; it runs again at the next start", error)
        }
    }

    private companion object {
        // Stored; do not rename.
        const val PREFERENCES_NAME = "storage_layout"
        const val KEY_LAYOUT = "layout"
        /** Workspaces in iCode's storage. The phone-storage layout before it had no mark. */
        const val LAYOUT = 2
        const val NOTICE_ID = "workspaces-moved-in"
        val EXPERIMENT = Regex("[0-9a-f]{64}")
    }
}
