package dev.icode.mobile.domain

sealed interface WorkbenchIntent {
    /** Shows Android's own prompt for [permission]. */
    data class RequestSystemPermission(val permission: SystemPermission) : WorkbenchIntent
    /** Android's prompt for [permission] has closed, whatever the answer. */
    data class SystemPermissionAnswered(val permission: SystemPermission) : WorkbenchIntent
    /** "Not now" on an optional permission: onboarding does not offer it again. */
    data class DeclineSystemPermission(val permission: SystemPermission) : WorkbenchIntent
    /** Opens Android's settings page for [permission], where it can be changed either way. */
    data class OpenSystemSettings(val permission: SystemPermission) : WorkbenchIntent
    /** Re-reads what Android allows; it may have changed while iCode was away. */
    data object RefreshSystemAccess : WorkbenchIntent
    /** The Notifications page was opened: everything on it has been seen. */
    data object MarkNoticesRead : WorkbenchIntent
    data class DeleteNotice(val id: String) : WorkbenchIntent
    data object ClearNotices : WorkbenchIntent
    /** New Folder: a workspace named [name], made at `/workspace/<name>` and opened. */
    data class CreateWorkspace(val name: String) : WorkbenchIntent
    /** A whole workspace renamed; the dialog that asked is answered by [requestId]. */
    data class RenameWorkspace(val location: String, val newName: String, val requestId: String) : WorkbenchIntent
    /** A whole workspace deleted, once its menu confirmed it; runs as a task. */
    data class DeleteWorkspace(val location: String) : WorkbenchIntent
    /**
     * The Import browser opens: at [path], or where it was last, or at the root of internal storage.
     * Without All Files Access it waits for it (docs/workspace-storage-plan.md §4.5).
     */
    data class OpenImportBrowser(val path: String? = null) : WorkbenchIntent
    data class BrowseImportFolder(val path: String) : WorkbenchIntent
    data object BrowseImportParent : WorkbenchIntent
    /** The folder at [path] on phone storage is the one to import: the Import screen names it. */
    data class ChooseImportFolder(val path: String) : WorkbenchIntent
    /** Copies the chosen [ImportSource] into a new workspace named [name]; runs as a task. */
    data class ImportWorkspace(val name: String) : WorkbenchIntent
    /** Android's Files app, at the workspace. */
    data class ShowWorkspaceInFiles(val location: String) : WorkbenchIntent
    data class OpenWorkspace(val path: String) : WorkbenchIntent
    data class OpenWorkspaceTerminal(val path: String) : WorkbenchIntent
    data class ResolveWorkspaceSwitch(val path: String, val openTerminal: Boolean, val saveChanges: Boolean) : WorkbenchIntent
    /** [lineNumber] is one-based and [column] is zero-based, matching content-search results. */
    data class OpenFile(val path: String, val lineNumber: Int? = null, val column: Int? = null) : WorkbenchIntent
    data class CreateFile(
        val name: String,
        val parentLocation: String? = null,
        val expectedWorkspace: String? = null,
        val requestId: String? = null,
    ) : WorkbenchIntent
    /** Creates a subfolder without changing the active workspace. */
    data class CreateWorkspaceFolder(
        val name: String,
        val parentLocation: String,
        val expectedWorkspace: String,
        val requestId: String,
    ) : WorkbenchIntent
    /** Explorer file operations, inside the open workspace (as VS Code: Move is Cut, then Paste). */
    data class RenameWorkspaceItem(val location: String, val newName: String, val requestId: String) : WorkbenchIntent
    data class MoveWorkspaceItem(val location: String, val destinationFolder: String) : WorkbenchIntent
    data class CopyWorkspaceItem(val location: String, val destinationFolder: String) : WorkbenchIntent
    data class DeleteWorkspaceItem(val location: String) : WorkbenchIntent
    /** A long operation's dialog: Hide sends it to the background, Show brings it back. */
    sealed interface TaskIntent : WorkbenchIntent { val id: Long }
    data class HideTask(override val id: Long) : TaskIntent
    data class ShowTask(override val id: Long) : TaskIntent
    data class CancelTask(override val id: Long) : TaskIntent
    /** A failure, read. */
    data class DismissTask(override val id: Long) : TaskIntent
    /** The Components page (docs/runtime-components-plan.md): it never waits on the mutation lock. */
    sealed interface ComponentIntent : WorkbenchIntent
    /** What each component not installed would download; the lists are read first when needed. */
    data object MeasureComponents : ComponentIntent
    /** Install what [chosen] adds, remove what it leaves out. */
    data class ApplyComponents(val chosen: Set<String>) : ComponentIntent
    data object CancelComponents : ComponentIntent
    /** The page offered after a runtime build is answered without installing anything. */
    data object SkipComponents : ComponentIntent
    /** A failure, read. */
    data object DismissComponents : ComponentIntent
    data class UpdateDocumentText(val location: String, val text: String) : WorkbenchIntent
    /** Automatic requests carry the revision they observed; a later edit must start its own timer. */
    data class SaveDocument(
        val location: String,
        val automatic: Boolean = false,
        val expectedText: String? = null,
        val expectedWorkspace: String? = null,
    ) : WorkbenchIntent
    data class RestoreDraft(val location: String) : WorkbenchIntent
    /** From Document Recovery: opens the draft's file, in its own workspace if that is another one. */
    data class OpenRecoverableDraft(val location: String) : WorkbenchIntent
    data class DiscardDraft(val location: String) : WorkbenchIntent
    data class ConfirmRecoveredOverwrite(val location: String) : WorkbenchIntent

    /** Answers to an [ExternalChange]: the disk wins, the editor wins, or look at both first. */
    data class ReloadFromDisk(val location: String) : WorkbenchIntent
    data class OverwriteDisk(val location: String) : WorkbenchIntent
    data class CompareWithDisk(val location: String) : WorkbenchIntent
    data class SelectDocument(val location: String) : WorkbenchIntent
    data class ReorderDocument(val movingLocation: String, val targetLocation: String) : WorkbenchIntent
    /** A dirty tab requires an explicit save/discard choice; null is only valid for clean tabs. */
    data class CloseDocument(val location: String, val saveChanges: Boolean? = null) : WorkbenchIntent
    data class CloseOtherDocuments(val keepLocation: String, val saveChanges: Boolean? = null) : WorkbenchIntent
    data object CloseSavedDocuments : WorkbenchIntent
    data object InitializeGit : WorkbenchIntent
    data class ToggleFolder(val path: String) : WorkbenchIntent
    data class StageFile(val path: String) : WorkbenchIntent
    data class UnstageFile(val path: String) : WorkbenchIntent
    data object StageAllFiles : WorkbenchIntent
    data object UnstageAllFiles : WorkbenchIntent
    data class CommitStaged(val message: String) : WorkbenchIntent
    data class ConfigureGitIdentity(val name: String, val email: String) : WorkbenchIntent
    data class ConfigureGlobalGitIdentity(val name: String, val email: String) : WorkbenchIntent
    data object ClearRepositoryGitIdentity : WorkbenchIntent
    data class SwitchBranch(val branch: String) : WorkbenchIntent
    data class CreateBranch(val branch: String) : WorkbenchIntent
    data class DeleteBranch(val branch: String) : WorkbenchIntent
    /** A local branch tracking [remoteBranch] (`origin/feature`), named by Git from the remote's refspec. */
    data class CheckoutRemoteBranch(val remoteBranch: String) : WorkbenchIntent

    /** Installs Git into the runtime when it is not present yet. */
    data object ProvisionGit : WorkbenchIntent
    data object RefreshGit : WorkbenchIntent
    /** [staged] selects the index-vs-HEAD comparison instead of worktree-vs-index. */
    data class OpenDiff(val path: String, val staged: Boolean) : WorkbenchIntent
    data object CloseDiff : WorkbenchIntent
    /** Holds the unstaged change at [path] for the confirmation screen; nothing is discarded yet. */
    data class RequestDiscardChanges(val path: String) : WorkbenchIntent
    data object CancelDiscardChanges : WorkbenchIntent
    /** Irreversible: discards exactly the change that was held, if Git still reports it. */
    data object ConfirmDiscardChanges : WorkbenchIntent
    /** Ends a half-finished merge, rebase, cherry-pick or revert. */
    data object AbortInProgress : WorkbenchIntent
    /** Adds a remote; one that already exists is Git's refusal to report, never replaced. */
    data class AddRemote(val name: String, val url: String) : WorkbenchIntent
    data object FetchRemote : WorkbenchIntent
    data object PullRemote : WorkbenchIntent
    data object PushRemote : WorkbenchIntent
    /** Stops the running clone or sync: Git and everything it started are ended. */
    data object CancelGitTransfer : WorkbenchIntent
    data class OpenConflict(val path: String) : WorkbenchIntent
    data object CloseConflict : WorkbenchIntent
    /** Resolves the one marker block that starts at line [sectionStart] (zero-based). */
    data class ResolveConflict(val path: String, val sectionStart: Int, val choice: GitConflictChoice) : WorkbenchIntent
    data class MarkConflictResolved(val path: String) : WorkbenchIntent
    data class UpdateCloneForm(val url: String, val name: String, val branch: String) : WorkbenchIntent
    /** Clones into `/workspace/<directoryName>`; [branch] blank means the remote's default branch. */
    data class CloneRepository(
        val url: String,
        val directoryName: String,
        val branch: String = "",
    ) : WorkbenchIntent

    data class SelectGitProvider(val provider: GitProvider) : WorkbenchIntent
    data class SelectGitAuthMethod(val method: GitAuthMethod) : WorkbenchIntent
    /** The token is consumed straight into encrypted storage and never echoed back. */
    data class SaveGitToken(val host: String, val username: String, val token: String) : WorkbenchIntent
    data object ClearGitToken : WorkbenchIntent
    data object GenerateSshKey : WorkbenchIntent
    data class TestGitConnection(val url: String) : WorkbenchIntent
    data object RefreshGitAuth : WorkbenchIntent
    data class SelectLanguageService(val id: String) : WorkbenchIntent
    data class SaveLanguageService(
        val id: String?,
        val name: String,
        val command: String,
        val patterns: String,
        val rootMarkers: String,
        val initializationOptions: String,
    ) : WorkbenchIntent
    data class RemoveLanguageService(val id: String) : WorkbenchIntent
    data class StartLanguageService(val id: String) : WorkbenchIntent
    data class StopLanguageService(val id: String) : WorkbenchIntent
    data class RestartLanguageService(val id: String) : WorkbenchIntent
    data object PrepareWorkspaceSearch : WorkbenchIntent
    data class RunContentSearch(
        val query: String,
        val matchCase: Boolean,
        val wholeWord: Boolean,
        val regex: Boolean,
    ) : WorkbenchIntent
    data object ClearContentSearch : WorkbenchIntent

    data object InstallRuntime : WorkbenchIntent
    data object RefreshRuntimeDetails : WorkbenchIntent
    data object RefreshStorage : WorkbenchIntent
    data object ClearAppCache : WorkbenchIntent
    /** Rebuilds the base system, keeping the guest home directory. */
    data object RepairRuntime : WorkbenchIntent
    /** Rebuilds the base system and discards the guest home directory. */
    data object ResetRuntime : WorkbenchIntent
    /** "+": always a new session. */
    data object OpenTerminalSession : WorkbenchIntent
    /** The panel needs a session: the open workspace's running one, or a new one only when there is none. */
    data object EnsureTerminalSession : WorkbenchIntent
    data class SelectTerminalSession(val id: String) : WorkbenchIntent

    /** Bring a session to the front: its workspace, the Terminal panel, that tab (from its notification). */
    data class ShowTerminalSession(val id: String) : WorkbenchIntent
    data class ReorderTerminalSession(val movingId: String, val targetId: String) : WorkbenchIntent
    data class CloseTerminalSession(val id: String) : WorkbenchIntent
    data class OpenExternalUrl(val url: String) : WorkbenchIntent

    data object Refresh : WorkbenchIntent

    /** iCode came on screen or left it: the open workspace is watched only while it is seen. */
    data class ScreenVisible(val visible: Boolean) : WorkbenchIntent
}
