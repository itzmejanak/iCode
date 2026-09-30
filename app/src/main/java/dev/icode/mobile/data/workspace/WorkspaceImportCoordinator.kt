package dev.icode.mobile.data.workspace

import dev.icode.mobile.data.git.GitCheckoutOperations
import dev.icode.mobile.data.storage.FolderImport
import dev.icode.mobile.data.storage.ImportTree
import dev.icode.mobile.data.storage.WorkspacePaths
import dev.icode.mobile.data.storage.WorkspaceStaging
import dev.icode.mobile.data.tasks.TaskRunner
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.GitOutcome
import dev.icode.mobile.domain.ImportSource
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspaceNameRules
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Import (docs/workspace-storage-plan.md §4.5): a folder picked in Android's folder picker is copied
 * into iCode's staging as a task — counted first, and refused before anything is copied when it
 * would not fit — an imported repository's checkout is restored as Git recorded it, and it becomes
 * a workspace in one rename and opens. Stopped or failed, nothing is left; the source is only read.
 */
internal class WorkspaceImportCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val tasks: TaskRunner,
    private val paths: WorkspacePaths,
    private val git: GitCheckoutOperations,
    /** The picked folder, read through its provider. */
    private val openTree: (uri: String) -> ImportTree,
    /** A folder picked from iCode's own Files provider is a workspace already. */
    private val isOwnTree: (uri: String) -> Boolean,
    /** Makes room on the workspaces' volume, having Android clear other apps' caches if need be. */
    private val reserveSpace: (bytes: Long) -> Boolean,
    /** Space the workspaces can have, counting what Android can free: said when there is too little. */
    private val freeBytes: () -> Long,
    private val open: suspend (location: String) -> Unit,
) {
    private val staging = WorkspaceStaging(paths.root)

    /** Android's picker answered: the Import screen opens with the folder's name proposed. */
    suspend fun choose(uri: String) {
        if (isOwnTree(uri)) return workbench.message("This folder is already in iCode's workspaces")
        runCatching { withContext(Dispatchers.IO) { openTree(uri).top.name } }
            .onSuccess { name ->
                workbench.update { it.copy(importSource = ImportSource(uri, name)) }
                workbench.emit(WorkbenchEffect.ImportSourceChosen)
            }
            .onFailure { error -> workbench.report("import", "Unable to read the chosen folder", error) }
    }

    suspend fun import(rawName: String) {
        val source = workbench.current.importSource ?: return workbench.message("Choose a folder to import first")
        val name = rawName.trim()
        WorkspaceNameRules.workspaceNameError(name)?.let { return workbench.message(it) }
        if (File(paths.root, name).exists()) return workbench.message("A workspace named $name already exists. Choose another name")
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
                val tree = openTree(source.uri)
                val plan = FolderImport.plan(tree) { context.ensureActive() }
                if (!reserveSpace(plan.bytes)) {
                    throw IOException("Not enough storage: ${ByteSize.format(plan.bytes)} required, ${ByteSize.format(freeBytes())} free")
                }
                progress.counted(plan.entries.size.toLong())
                FolderImport.copy(tree, plan, folder, progress::item)
                val unrepaired = if (File(folder, ".git").isDirectory) restoreCheckout(folder, progress) else null
                val workspace = staging.complete(folder, name)
                imported = Imported(workspace.path, plan.entries.size, plan.skipped.size, unrepaired)
            },
            finished = { failure ->
                val result = imported
                if (failure != null || result == null) {
                    runCatching { staging.discard(folder) }
                } else {
                    workbench.update { it.copy(importSource = null) }
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
