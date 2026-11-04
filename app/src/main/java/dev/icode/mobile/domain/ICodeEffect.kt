package dev.icode.mobile.domain

sealed interface ICodeEffect {
    data class RequestSystemPermission(val permission: SystemPermission) : ICodeEffect
    data class SystemPermissionResolved(val permission: SystemPermission, val granted: Boolean) : ICodeEffect
    data class OpenSystemSettings(val permission: SystemPermission) : ICodeEffect
    data class WorkspaceOpened(val location: String) : ICodeEffect
    data class WorkspaceTerminalOpened(val location: String) : ICodeEffect
    /** The answer to the sheet that asked, by request id: created or renamed, and where it is now. */
    data class WorkspaceItemChanged(val requestId: String, val location: String) : ICodeEffect
    data class WorkspaceItemFailed(val requestId: String, val message: String) : ICodeEffect
    data class ConfirmWorkspaceSwitch(val location: String, val openTerminal: Boolean, val dirtyCount: Int) : ICodeEffect
    data object GitInitialized : ICodeEffect
    data class DocumentOpened(val location: String, val lineNumber: Int? = null, val column: Int? = null) : ICodeEffect
    data class ConfirmDraftRecovery(
        val location: String,
        val name: String,
        val sourceChanged: Boolean,
    ) : ICodeEffect
    data class ConfirmRecoveredOverwrite(val location: String, val name: String) : ICodeEffect

    /** A file changed on disk under unsaved edits; the External Change screen asks which wins. */
    data class ExternalChangeDetected(val location: String) : ICodeEffect
    data object RuntimeRebuildStarted : ICodeEffect
    /** What iCode says, on the bell; the Notifications page and Android's shade keep it ([NoticeRules.kept]). */
    data class Notice(val notice: AppNotice.Message) : ICodeEffect

    /** A chat was exported: its transcript, to be shared as text. */
    data class ShareChat(val title: String, val text: String) : ICodeEffect

    /** A file asked to open that has a viewer of its own, shown in it (docs/file-viewers-plan.md). */
    data class ViewFile(val location: String, val viewer: FileViewer) : ICodeEffect

    /**
     * A file asked to open that the editor cannot show — not text, or larger than it reads — handed
     * to another app (docs/open-with-plan.md). Reopening the last session hands nothing out.
     */
    data class OpenInOtherApp(val location: String) : ICodeEffect

    /** Hand a URL to Android's browser; the app never renders it (PR-TERM-09). */
    data class OpenExternalUrl(val url: String) : ICodeEffect
}
