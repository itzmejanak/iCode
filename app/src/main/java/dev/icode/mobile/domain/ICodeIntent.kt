package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.StoragePlace

sealed interface ICodeIntent {
    /** Shows Android's own prompt for [permission]. */
    data class RequestSystemPermission(val permission: SystemPermission) : ICodeIntent
    /** Android's prompt for [permission] has closed, whatever the answer. */
    data class SystemPermissionAnswered(val permission: SystemPermission) : ICodeIntent
    /** "Not now" on an optional permission: onboarding does not offer it again. */
    data class DeclineSystemPermission(val permission: SystemPermission) : ICodeIntent
    /** Opens Android's settings page for [permission], where it can be changed either way. */
    data class OpenSystemSettings(val permission: SystemPermission) : ICodeIntent
    /** Re-reads what Android allows; it may have changed while iCode was away. */
    data object RefreshSystemAccess : ICodeIntent
    /** New Folder on the Workspaces page: answered by [requestId], and not opened — it appears in the list. */
    data class CreateWorkspace(val name: String, val requestId: String) : ICodeIntent
    data class OpenWorkspace(val path: String) : ICodeIntent
    data class OpenWorkspaceTerminal(val path: String) : ICodeIntent
    data class ResolveWorkspaceSwitch(val path: String, val openTerminal: Boolean, val saveChanges: Boolean) : ICodeIntent
    /** [lineNumber] is one-based and [column] is zero-based, matching content-search results. */
    data class OpenFile(val path: String, val lineNumber: Int? = null, val column: Int? = null) : ICodeIntent
    data class CreateFile(
        val name: String,
        val parentLocation: String? = null,
        val expectedWorkspace: String? = null,
        val requestId: String? = null,
    ) : ICodeIntent
    /** Creates a subfolder without changing the active workspace. */
    data class CreateWorkspaceFolder(
        val name: String,
        val parentLocation: String,
        val expectedWorkspace: String,
        val requestId: String,
    ) : ICodeIntent
    /**
     * An item renamed in its folder — a whole workspace, or anything in one or on phone storage; the
     * dialog that asked is answered by [requestId].
     */
    data class RenameWorkspaceItem(val location: String, val newName: String, val requestId: String) : ICodeIntent
    /** A long operation's dialog: Hide sends it to the background, Show brings it back. */
    sealed interface TaskIntent : ICodeIntent { val id: Long }
    data class HideTask(override val id: Long) : TaskIntent
    data class ShowTask(override val id: Long) : TaskIntent
    data class CancelTask(override val id: Long) : TaskIntent
    /** A failure, read. */
    data class DismissTask(override val id: Long) : TaskIntent
    /** The tools page (docs/runtime-tools-plan.md): it never waits on the mutation lock. */
    sealed interface RuntimeToolsIntent : ICodeIntent
    /** What each tool not installed would download; the lists are read first when needed. */
    data object MeasureRuntimeTools : RuntimeToolsIntent
    /** Install what [chosen] adds, remove what it leaves out; [accepted] are the licences the user accepted. */
    data class ApplyRuntimeTools(val chosen: Set<String>, val accepted: Set<String> = emptySet()) : RuntimeToolsIntent
    data object CancelRuntimeTools : RuntimeToolsIntent
    /** The page offered after a runtime build is answered without installing anything. */
    data object SkipRuntimeTools : RuntimeToolsIntent
    /** A failure, read. */
    data object DismissRuntimeTools : RuntimeToolsIntent
    /** The Terminal panel's Run tab (docs/run-tab-plan.md), for the open workspace: it never waits on the mutation lock. */
    sealed interface RunIntent : ICodeIntent
    data class StartRun(val configuration: String) : RunIntent
    /** Stops its process, or abandons the install that waits on Android. */
    data class StopRun(val configuration: String) : RunIntent
    data object ConfirmAndroidAppInstall : RunIntent
    /** A new command when [id] is null; otherwise that one, changed. */
    data class SaveRunCommand(val id: String?, val name: String, val command: String) : RunIntent
    data class DeleteRunCommand(val id: String) : RunIntent
    data class UpdateDocumentText(val location: String, val text: String) : ICodeIntent
    /** Automatic requests carry the revision they observed; a later edit must start its own timer. */
    data class SaveDocument(
        val location: String,
        val automatic: Boolean = false,
        val expectedText: String? = null,
        val expectedWorkspace: String? = null,
    ) : ICodeIntent
    data class RestoreDraft(val location: String) : ICodeIntent
    /** From Document Recovery: opens the draft's file, in its own workspace if that is another one. */
    data class OpenRecoverableDraft(val location: String) : ICodeIntent
    data class DiscardDraft(val location: String) : ICodeIntent
    data class ConfirmRecoveredOverwrite(val location: String) : ICodeIntent

    /** Answers to an [ExternalChange]: the disk wins, the editor wins, or look at both first. */
    data class ReloadFromDisk(val location: String) : ICodeIntent
    data class OverwriteDisk(val location: String) : ICodeIntent
    data class CompareWithDisk(val location: String) : ICodeIntent
    data class SelectDocument(val location: String) : ICodeIntent
    data class ReorderDocument(val movingLocation: String, val targetLocation: String) : ICodeIntent
    /** A dirty tab requires an explicit save/discard choice; null is only valid for clean tabs. */
    data class CloseDocument(val location: String, val saveChanges: Boolean? = null) : ICodeIntent
    data class CloseOtherDocuments(val keepLocation: String, val saveChanges: Boolean? = null) : ICodeIntent
    data object CloseSavedDocuments : ICodeIntent
    data object InitializeGit : ICodeIntent
    data class ToggleFolder(val path: String) : ICodeIntent
    data class StageFile(val path: String) : ICodeIntent
    data class UnstageFile(val path: String) : ICodeIntent
    data object StageAllFiles : ICodeIntent
    data object UnstageAllFiles : ICodeIntent
    data class CommitStaged(val message: String) : ICodeIntent
    data class ConfigureGitIdentity(val name: String, val email: String) : ICodeIntent
    data class ConfigureGlobalGitIdentity(val name: String, val email: String) : ICodeIntent
    data object ClearRepositoryGitIdentity : ICodeIntent
    data class SwitchBranch(val branch: String) : ICodeIntent
    data class CreateBranch(val branch: String) : ICodeIntent
    data class DeleteBranch(val branch: String) : ICodeIntent
    /** A local branch tracking [remoteBranch] (`origin/feature`), named by Git from the remote's refspec. */
    data class CheckoutRemoteBranch(val remoteBranch: String) : ICodeIntent

    /** Installs Git into the runtime when it is not present yet. */
    data object ProvisionGit : ICodeIntent
    data object RefreshGit : ICodeIntent
    /** Source Control's picker: Git works in this repository of the open workspace. */
    data class ChooseGitRepository(val location: String) : ICodeIntent
    /** [staged] selects the index-vs-HEAD comparison instead of worktree-vs-index. */
    data class OpenDiff(val path: String, val staged: Boolean) : ICodeIntent
    data object CloseDiff : ICodeIntent
    /** Holds the unstaged change at [path] for the confirmation screen; nothing is discarded yet. */
    data class RequestDiscardChanges(val path: String) : ICodeIntent
    data object CancelDiscardChanges : ICodeIntent
    /** Irreversible: discards exactly the change that was held, if Git still reports it. */
    data object ConfirmDiscardChanges : ICodeIntent
    /** Ends a half-finished merge, rebase, cherry-pick or revert. */
    data object AbortInProgress : ICodeIntent
    /** Adds a remote; one that already exists is Git's refusal to report, never replaced. */
    data class AddRemote(val name: String, val url: String) : ICodeIntent
    /** What talks to a remote: each its own job, one at a time, followed on the sync page or Clone's. */
    sealed interface TransferIntent : ICodeIntent
    data object FetchRemote : TransferIntent
    data object PullRemote : TransferIntent
    data object PushRemote : TransferIntent
    /** Commits what is staged with [message], then pushes it: one transfer. */
    data class CommitAndPush(val message: String) : TransferIntent
    /** Stops the running clone or sync: Git and everything it started are ended. */
    data object CancelGitTransfer : TransferIntent
    data class OpenConflict(val path: String) : ICodeIntent
    data object CloseConflict : ICodeIntent
    /** Resolves the one marker block that starts at line [sectionStart] (zero-based). */
    data class ResolveConflict(val path: String, val sectionStart: Int, val choice: GitConflictChoice) : ICodeIntent
    data class MarkConflictResolved(val path: String) : ICodeIntent
    data class UpdateCloneForm(val url: String, val name: String, val branch: String) : ICodeIntent
    /** Clones into `/workspace/<directoryName>`, or into the folder [into] of a workspace; [branch] blank: the remote's default. */
    data class CloneRepository(
        val url: String,
        val directoryName: String,
        val branch: String = "",
        val into: String? = null,
    ) : TransferIntent

    data class SelectGitProvider(val provider: GitProvider) : ICodeIntent
    data class SelectGitAuthMethod(val method: GitAuthMethod) : ICodeIntent
    /** The token is consumed straight into encrypted storage and never echoed back. */
    data class SaveGitToken(val host: String, val username: String, val token: String) : ICodeIntent
    data object ClearGitToken : ICodeIntent
    data object GenerateSshKey : GitAuthWorkIntent
    data class TestGitConnection(val url: String) : GitAuthWorkIntent
    data object RefreshGitAuth : ICodeIntent
    data class SelectLanguageService(val id: String) : ICodeIntent
    data class SaveLanguageService(
        val id: String?,
        val name: String,
        val command: String,
        val patterns: String,
        val rootMarkers: String,
        val initializationOptions: String,
    ) : ICodeIntent
    data class RemoveLanguageService(val id: String) : ICodeIntent
    data class StartLanguageService(val id: String) : ICodeIntent
    data class StopLanguageService(val id: String) : ICodeIntent
    data class RestartLanguageService(val id: String) : ICodeIntent
    data object PrepareWorkspaceSearch : ICodeIntent
    data class RunContentSearch(
        val query: String,
        val matchCase: Boolean,
        val wholeWord: Boolean,
        val regex: Boolean,
    ) : ICodeIntent
    data object ClearContentSearch : ICodeIntent

    data object InstallRuntime : ICodeIntent
    data object RefreshRuntimeDetails : ICodeIntent
    /** Reading what storage holds: it changes nothing, so it never waits on the mutation lock. */
    sealed interface StorageIntent : ICodeIntent
    /** The file explorer at [path], in whichever place it is — where it was when none, the workspaces folder at first — and not in a view. */
    data class BrowseFiles(val path: String? = null) : StorageIntent
    /** The file explorer in [place], where it was last there; at its top the first time. */
    data class BrowsePlace(val place: StoragePlace) : StorageIntent
    /** The file explorer showing [view] in place of a folder (docs/explorer-gaps-plan.md §3.10); a browse leaves it. */
    data class BrowseView(val view: ExplorerView) : StorageIntent
    /** Where the next clone goes: the folder [path] of a workspace, or none — a new workspace. */
    data class ChooseDestination(val path: String?) : StorageIntent
    /** The device, the runtime and the cache. */
    data object RefreshStorage : StorageIntent
    /** Each workspace's size, without measuring the runtime and the cache. */
    data object MeasureWorkspaces : StorageIntent
    data object ClearAppCache : ICodeIntent
    /** Rebuilds the base system, keeping the guest home directory. */
    data object RepairRuntime : ICodeIntent
    /** Rebuilds the base system and discards the guest home directory. */
    data object ResetRuntime : ICodeIntent
    /** "+": always a new session. */
    data object OpenTerminalSession : ICodeIntent
    /** The panel needs a session: the open workspace's running one, or a new one only when there is none. */
    data object EnsureTerminalSession : ICodeIntent
    data class SelectTerminalSession(val id: String) : ICodeIntent

    /** Bring a session to the front: its workspace, the Terminal panel, that tab (from its notification). */
    data class ShowTerminalSession(val id: String) : ICodeIntent
    data class ReorderTerminalSession(val movingId: String, val targetId: String) : ICodeIntent
    data class CloseTerminalSession(val id: String) : ICodeIntent
    data class OpenExternalUrl(val url: String) : ICodeIntent

    /** Managed processes (docs/runtime-foundation-plan.md §3.4): each its own job, never behind the mutation lock. */
    sealed interface ProcessIntent : ICodeIntent
    /** Ends a running process: SIGTERM, then SIGKILL if it is still there. */
    data class StopProcess(val id: String) : ProcessIntent
    /** The notification's Cancel: every running process ends. */
    data object StopAllProcesses : ProcessIntent
    /** An ended process leaves the list. */
    data class ForgetProcess(val id: String) : ProcessIntent
    /** A process's output on the Terminal panel, in a tab of its own (docs/runtime-foundation-plan.md §6 P3). */
    data class ShowProcessOutput(val id: String) : ICodeIntent

    /** The assistant (docs/assistant-koog-plan.md): its turns are their own job, never behind the mutation lock. */
    sealed interface AssistantIntent : ICodeIntent
    /**
     * The Assistant page opened, or the workspace under it changed: that workspace's chats are shown;
     * the phone's on the home screen, or while the user keeps them shown (docs/phone-agent-plan.md L1).
     */
    data object OpenAssistant : AssistantIntent
    /** The page's chats are [place]'s: the open workspace's location, or [AssistantPlace.PHONE]. */
    data class ShowAssistantPlace(val place: String) : AssistantIntent
    /** One of the shown place's chats, from its history. */
    data class OpenAssistantSession(val id: String) : AssistantIntent
    data class DeleteAssistantSession(val id: String) : AssistantIntent
    /** One of the shown place's chats, shared as its transcript (§12 R9). */
    data class ExportAssistantSession(val id: String) : AssistantIntent
    /** A new, empty chat; it is kept once something is said in it. */
    data object NewAssistantChat : AssistantIntent
    /** [attachments]: the files sent with it — a workspace file's location, or a phone file's `content://` URI. */
    data class SendAssistantMessage(val text: String, val attachments: List<String> = emptyList()) : AssistantIntent
    /** Stops the turn in hand: the request, and a running command with everything it started. */
    data object StopAssistant : AssistantIntent
    data class AnswerAssistant(val callId: String, val answer: AssistantAnswer) : AssistantIntent
    /** The turn's pause answered: it goes on (Continue, Done). Stop is [StopAssistant]. */
    data object ContinueAssistant : AssistantIntent
    /** Summarizes the shown chat so later turns send less (§12 R4). */
    data object CompactAssistantChat : AssistantIntent
    /** The chat's model: one of [provider]'s. */
    data class ChooseAssistantModel(val provider: String, val model: String) : AssistantIntent
    /** Null: the provider's default. */
    data class ChooseAssistantEffort(val effort: AssistantEffort?) : AssistantIntent
    data class ChooseAssistantMode(val mode: AssistantMode) : AssistantIntent
    /** The mode, saved as the setting and for the chat shown, whose own "Allow in" answer it replaces. */
    data class SetAssistantPermission(val permission: AssistantPermission) : AssistantIntent
    /** The last message failed before any reply: it is sent again, its files as they were. */
    data object RetryAssistantMessage : AssistantIntent
    /** A provider's settings page opens on [id]; "" for one being added, of [kind] where the sheet of kinds chose one. */
    data class EditAssistantProvider(val id: String, val kind: AssistantProviderKind? = null) : AssistantIntent
    /** Saves a provider ([id] "" adds one); [key] null keeps the saved key; [baseUrl] only where the kind asks; [models] in order. */
    data class SaveAssistantProvider(
        val id: String,
        val kind: AssistantProviderKind,
        val name: String,
        val baseUrl: String,
        val key: String?,
        val models: List<AssistantModelSpec>,
    ) : AssistantIntent {
        /** The key is never written out, should an intent ever be. */
        override fun toString(): String =
            "SaveAssistantProvider($id, $kind, $name, $baseUrl, key=${if (key == null) "kept" else "***"}, ${models.map { it.id }})"
    }
    /** Forgets a provider, its key and its models. */
    data class RemoveAssistantProvider(val id: String) : AssistantIntent
    /** The open workspace's staged changes described by the model, for the commit field (docs/commit-message-generator-plan.md). */
    data object WriteCommitMessage : AssistantIntent
    /** The written message is in the commit field, or its failure was read: it is not offered again. */
    data object CommitMessageTaken : AssistantIntent
    /** The model that writes commit messages; null: the assistant's. */
    data class ChooseCommitModel(val choice: AssistantChoice?) : AssistantIntent
    /** Asks a provider's server for its models, with the address and key as typed ([key] null: the saved one of [provider]). */
    data class ListAssistantModels(
        val provider: String,
        val kind: AssistantProviderKind,
        val baseUrl: String,
        val key: String?,
    ) : AssistantIntent {
        override fun toString(): String = "ListAssistantModels($provider, $kind, $baseUrl, key=${if (key == null) "kept" else "***"})"
    }

    data object Refresh : ICodeIntent
    /** iCode came on screen or left it: the open workspace is watched only while it is seen. */
    data class ScreenVisible(val visible: Boolean) : ICodeIntent
}
