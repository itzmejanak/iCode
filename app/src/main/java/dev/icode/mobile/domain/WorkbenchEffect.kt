package dev.icode.mobile.domain

sealed interface WorkbenchEffect {
    data class RequestSystemPermission(val permission: SystemPermission) : WorkbenchEffect
    data class SystemPermissionResolved(val permission: SystemPermission, val granted: Boolean) : WorkbenchEffect
    data class OpenSystemSettings(val permission: SystemPermission) : WorkbenchEffect
    /** A folder was chosen for Import: the Import screen names the new workspace. */
    data object ImportSourceChosen : WorkbenchEffect
    /** Opens Android's Files app at [documentUri] — a workspace's folder in iCode's provider — else at its root. */
    data class ShowInFiles(val documentUri: String, val rootUri: String) : WorkbenchEffect

    data class WorkspaceOpened(val location: String) : WorkbenchEffect
    data class WorkspaceTerminalOpened(val location: String) : WorkbenchEffect
    /** The answer to the dialog that asked, by request id: created or renamed, and where it is now. */
    data class WorkspaceItemChanged(val requestId: String, val location: String) : WorkbenchEffect
    data class WorkspaceItemFailed(val requestId: String, val message: String) : WorkbenchEffect
    data class ConfirmWorkspaceSwitch(val location: String, val openTerminal: Boolean, val dirtyCount: Int) : WorkbenchEffect
    data object GitInitialized : WorkbenchEffect
    data object GitCommitted : WorkbenchEffect
    data class DocumentOpened(val location: String, val lineNumber: Int? = null, val column: Int? = null) : WorkbenchEffect
    data class DocumentSaved(val location: String, val automatic: Boolean = false) : WorkbenchEffect
    data class ConfirmDraftRecovery(
        val location: String,
        val name: String,
        val sourceChanged: Boolean,
    ) : WorkbenchEffect
    data class ConfirmRecoveredOverwrite(val location: String, val name: String) : WorkbenchEffect

    /** A file changed on disk under unsaved edits; the External Change screen asks which wins. */
    data class ExternalChangeDetected(val location: String) : WorkbenchEffect
    data object RuntimeRebuildStarted : WorkbenchEffect
    data class Message(val text: String) : WorkbenchEffect

    /** Hand a URL to Android's browser; the app never renders it (PR-TERM-09). */
    data class OpenExternalUrl(val url: String) : WorkbenchEffect
}
