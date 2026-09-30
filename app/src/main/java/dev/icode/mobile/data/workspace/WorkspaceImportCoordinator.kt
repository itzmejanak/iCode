package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.storage.FileImportTree
import dev.icode.mobile.data.storage.FolderImport
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ImportSource
import dev.icode.mobile.domain.WorkbenchEffect
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Import (docs/workspace-storage-plan.md §4.5): iCode's browser of phone storage, which waits for All
 * Files Access where it is needed; the folder chosen there is copied into iCode's staging as a task
 * — counted first, and refused before anything is copied when it would not fit — an imported
 * repository's checkout is restored as Git recorded it, and it becomes a workspace in one rename
 * and opens. Stopped or failed, nothing is left; the source is only read.
 */
internal class WorkspaceImportCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val tasks: TaskRunner,
    private val paths: WorkspacePaths,
    private val git: GitCheckoutOperations,
    private val storage: PhoneStorage,
    /** Makes room on the workspaces' volume, having Android clear other apps' caches if need be. */
    private val reserveSpace: (bytes: Long) -> Boolean,
    /** Space the workspaces can have, counting what Android can free: said when there is too little. */
    private val freeBytes: () -> Long,
    private val open: suspend (location: String) -> Unit,
) {
    private val staging = WorkspaceStaging(paths.root)

    /** The browser opens at [path], or where it was last, or at the root of phone storage. */
    suspend fun openBrowser(path: String?) = browse(path ?: workbench.current.importBrowser?.currentPath ?: storage.root.path)

    suspend fun browse(path: String) {
        runCatching { withContext(Dispatchers.IO) { storage.browse(path) } }
            .onSuccess { browser -> workbench.update { it.copy(importBrowser = browser) } }
            .onFailure { error ->
                // A folder gone since (an earlier workspace moved): its words, and the root instead.
                workbench.report("import", "Unable to read this folder", error)
                if (path != storage.root.path) browse(storage.root.path)
            }
    }

    /**
     * Follows All Files Access as iCode reads it — Android's page answered, or iCode came back to the
     * front: a browser that waited for it lists its folder, and one that lost it waits again.
     */
    fun followAccess(scope: CoroutineScope) = scope.launch {
        workbench.snapshot.map { it.systemAccess?.storage == true }.distinctUntilChanged().collect { granted ->
            workbench.mutations.withLock {
                val browser = workbench.current.importBrowser
                if (browser != null && browser.access != granted) browse(browser.currentPath)
            }
        }
    }

    /** [path] is the folder to import: the Import screen opens with its name proposed. */
    suspend fun choose(path: String) {
        if (!storage.hasAccess()) return workbench.message("Allow access to phone storage first")
        val folder = withContext(Dispatchers.IO) { storage.folder(path) }
        if (folder == null || folder == storage.root) return workbench.message("Choose a folder on phone storage")
        workbench.update { it.copy(importSource = ImportSource(folder.path, folder.name, storage.displayPath(folder))) }
        workbench.emit(WorkbenchEffect.ImportSourceChosen)
    }

    /** As a new workspace, or into the folder [into] of one. */
    suspend fun import(rawName: String, into: String? = null) {
        val source = workbench.current.importSource ?: return workbench.message("Choose a folder to import first")
        val name = rawName.trim()
        val destination = into?.let(::File) ?: paths.root
        staging.nameProblem(name, destination)?.let { return workbench.message(it) }
        val folder = staging.reserve("import")
        var imported: Imported? = null
        tasks.start(
            title = "Importing…",
            subject = name,
            paths = listOf(folder.path),
            cancellable = true,
            failure = "Unable to import $name",
            work = { progress ->
                val context = currentCoroutineContext()
                // Still there, and still readable: the permission can be taken back meanwhile.
                val tree = storage.folder(source.path)?.takeIf { storage.hasAccess() }?.let(::FileImportTree)
                    ?: throw IOException("${source.displayPath} can no longer be read")
                val plan = FolderImport.plan(tree) { context.ensureActive() }
                if (!reserveSpace(plan.bytes)) {
                    throw IOException("Not enough storage: ${ByteSize.format(plan.bytes)} required, ${ByteSize.format(freeBytes())} free")
                }
                progress.counted(plan.entries.size.toLong())
                // Each item as a path from the folder it is in, as a copy or a delete says it.
                FolderImport.copy(tree, plan, folder) { progress.item("$name/$it") }
                val unrepaired = if (File(folder, ".git").isDirectory) restoreCheckout(folder, progress) else null
                val workspace = staging.complete(folder, name, destination)
                imported = Imported(workspace.path, plan.entries.size, plan.skipped.size, unrepaired)
            },
            finished = { failure ->
                val result = imported
                if (failure != null || result == null) {
                    runCatching { staging.discard(folder) }
                } else {
                    workbench.update { it.copy(importSource = null, projectDestination = null) }
                    open(result.location)
                    workbench.message(result.summary(name))
                }
            },
        )
    }

    /** Null once restored; else why it could not be — the import completes either way. */
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
        workbench.failureText("git", "Git could not restore the checkout", error)
    }

    private data class Imported(val location: String, val entries: Int, val skipped: Int, val unrepaired: String?) {
        fun summary(name: String) = buildString {
            append("Imported $name")
            if (skipped > 0) append(" — $skipped item${if (skipped == 1) "" else "s"} not copied (cloud files, or names a folder cannot hold)")
            if (unrepaired != null) append(". Git's executables and links were not restored: $unrepaired")
        }
    }
}
