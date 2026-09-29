package dev.icode.mobile.data.workspace

import android.os.Environment
import dev.icode.mobile.domain.FolderBrowserPurpose
import dev.icode.mobile.domain.SystemPermission
import dev.icode.mobile.domain.WorkbenchEffect
import dev.icode.mobile.domain.WorkspaceNameRules

/**
 * iCode's own folder browser (S2): finding a folder to open, choosing where a
 * new one goes, and creating it. It needs all-files access, and waits for it
 * when onboarding has not granted it yet.
 */
internal class FolderBrowserCoordinator(
    private val workbench: WorkbenchStateHolder,
    private val source: DirectFileWorkspaceSource,
    /** Opening the chosen or created folder is a workspace switch, which the gateway sequences. */
    private val openWorkspace: suspend (location: String) -> Unit,
) {
    /** A browser the user asked for before storage access was granted. */
    private var pendingPurpose: FolderBrowserPurpose? = null

    suspend fun start(purpose: FolderBrowserPurpose) {
        if (!Environment.isExternalStorageManager()) {
            pendingPurpose = purpose
            workbench.emit(WorkbenchEffect.RequestSystemPermission(SystemPermission.Storage))
            return
        }
        runCatching { source.initialBrowser(purpose) }
            .onSuccess { browser ->
                workbench.update { snapshot -> snapshot.copy(folderBrowser = browser) }
                workbench.emit(WorkbenchEffect.FolderBrowserOpened)
            }
            .onFailure { error ->
                workbench.report("workspace", "Unable to read phone storage", error)
            }
    }

    /**
     * The storage prompt closed. A browser that was waiting opens if access was
     * granted; true when that happened, so the caller does not also report it.
     */
    suspend fun storageAnswered(granted: Boolean): Boolean {
        val purpose = pendingPurpose
        pendingPurpose = null
        if (!granted || purpose == null) return false
        start(purpose)
        return true
    }

    suspend fun showLocation(location: String) {
        if (!Environment.isExternalStorageManager()) {
            workbench.message("All files access is needed to browse this folder")
            return
        }
        runCatching { source.browse(location, FolderBrowserPurpose.OpenWorkspace) }
            .onSuccess { browser ->
                workbench.update { it.copy(folderBrowser = browser) }
                workbench.emit(WorkbenchEffect.FolderBrowserOpened)
            }
            .onFailure { error ->
                workbench.report("workspace", "Unable to locate folder", error)
            }
    }

    suspend fun browse(path: String) {
        val browser = workbench.current.folderBrowser ?: return
        runCatching { source.browse(path, browser.purpose) }
            .onSuccess { workbench.update { snapshot -> snapshot.copy(folderBrowser = it) } }
            .onFailure { error ->
                workbench.report("workspace", "Unable to read directory", error)
            }
    }

    suspend fun browseParent() {
        val browser = workbench.current.folderBrowser ?: return
        runCatching { source.parent(browser.currentPath, browser.purpose) }
            .onSuccess { workbench.update { snapshot -> snapshot.copy(folderBrowser = it) } }
            .onFailure { error ->
                workbench.report("workspace", "Unable to read parent directory", error)
            }
    }

    suspend fun confirmSelection() {
        val browser = workbench.current.folderBrowser ?: return
        when (browser.purpose) {
            FolderBrowserPurpose.OpenWorkspace -> openWorkspace(browser.currentPath)
            FolderBrowserPurpose.CreateFolderDestination -> {
                workbench.update { snapshot -> snapshot.copy(
                    createFolderDestination = browser.currentPath,
                    createFolderDestinationDisplay = browser.displayPath,
                ) }
                workbench.emit(WorkbenchEffect.FolderDestinationSelected(browser.currentPath))
            }
        }
    }

    suspend fun requestCreateFolder(rawName: String) {
        val name = rawName.trim()
        val error = WorkspaceNameRules.validationError(name)
        val parentLocation = workbench.current.createFolderDestination
        when {
            error != null -> workbench.message(error)
            parentLocation == null -> workbench.message("Choose a destination first")
            else -> runCatching {
                openWorkspace(source.createDirectory(parentLocation, name, root = null))
            }.onFailure { failure ->
                workbench.report("workspace", "Unable to create folder", failure)
            }
        }
    }
}
