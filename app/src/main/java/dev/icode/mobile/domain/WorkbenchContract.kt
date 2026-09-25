package dev.icode.mobile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

data class WorkspaceSummary(
    val name: String,
    val path: String,
    val branch: String?,
    val lastOpened: String,
    val available: Boolean = true,
    /** Stable adapter-owned location. [path] remains presentation-safe. */
    val location: String = path,
)

data class FileEntry(
    val name: String,
    val path: String,
    val kind: FileKind,
    val depth: Int = 0,
    val expanded: Boolean = false,
    val gitStatus: String? = null,
)

enum class FileKind { Folder, TypeScript, Code, Json, Markdown, Other }

enum class TextEncoding { Utf8, Utf16LittleEndian, Utf16BigEndian }

enum class LineEnding { Lf, CrLf, Cr, None }

data class OpenDocument(
    val name: String,
    val location: String,
    val text: String,
    val encoding: TextEncoding,
    val lineEnding: LineEnding,
    val dirty: Boolean,
    val readOnly: Boolean,
)

/** A file opened in the active workspace, including entries that have since gone missing. */
data class RecentDocument(
    val name: String,
    val location: String,
    val path: String,
    val lastOpened: String,
    val available: Boolean = true,
)

enum class FolderBrowserPurpose { OpenWorkspace, CreateFolderDestination }

data class FolderBrowserEntry(
    val name: String,
    val path: String,
    val directoryCount: Int,
    val fileCount: Int,
)

data class FolderBrowserState(
    val purpose: FolderBrowserPurpose,
    val rootPath: String,
    val currentPath: String,
    val displayPath: String,
    val folders: List<FolderBrowserEntry>,
)

data class StorageSummary(
    val availableBytes: Long,
    val totalBytes: Long,
)

data class StorageMeasurement(val bytes: Long?, val truncated: Boolean = false)

data class StorageDetails(
    val device: StorageSummary,
    val runtime: StorageMeasurement,
    val cache: StorageMeasurement,
    val workspaceSizes: Map<String, StorageMeasurement>,
)

/** One indexed workspace file used by Quick Open ranking. */
data class IndexedFile(
    val name: String,
    val path: String,
    val kind: FileKind,
)

/** One matching line inside a workspace content search result. */
data class ContentMatch(
    val lineNumber: Int,
    val column: Int,
    val lineText: String,
)

/** Matches grouped by file for the workspace search surface. */
data class ContentSearchResult(
    val path: String,
    val name: String,
    val matches: List<ContentMatch>,
)

/** Bounded result set of the latest workspace content search. */
data class ContentSearchState(
    val query: String,
    val matchCase: Boolean,
    val wholeWord: Boolean,
    val regex: Boolean,
    val results: List<ContentSearchResult>,
    val searchedFiles: Int,
    val truncated: Boolean,
    val error: String? = null,
) {
    val totalMatches: Int get() = results.sumOf { it.matches.size }

    companion object {
        val Empty = ContentSearchState(
            query = "",
            matchCase = true,
            wholeWord = false,
            regex = false,
            results = emptyList(),
            searchedFiles = 0,
            truncated = false,
        )
    }
}

data class GitChange(
    val status: String,
    val name: String,
    val path: String,
    val staged: Boolean,
)

data class GitCommitSummary(
    val id: String,
    val message: String,
    val author: String,
    val authoredAtEpochMillis: Long,
)

data class GitRepositoryState(
    val workTreePath: String,
    val branch: String,
    val hasCommits: Boolean,
    val remoteName: String?,
    val authorName: String?,
    val authorEmail: String?,
    val branches: List<String>,
    /** Remote-tracking branches as `remotes/origin/main`, for checking one out. */
    val remoteBranches: List<String> = emptyList(),
    /** An operation Git has half-finished, which the user must end before moving on. */
    val inProgress: GitInProgress = GitInProgress.None,
    val lastCommit: GitCommitSummary?,
    /** Upstream branch name when one is configured, otherwise null. */
    val upstream: String? = null,
    /** Commits on the local branch the upstream does not have. */
    val ahead: Int = 0,
    /** Commits on the upstream the local branch does not have. */
    val behind: Int = 0,
) {
    /** Remote synchronization is only meaningful once a remote exists. */
    val hasRemote: Boolean get() = remoteName != null
}

/** Git status codes shared by Source Control, Explorer decorations and diffs. */
object GitStatusCodes {
    const val ADDED = "A"
    const val MODIFIED = "M"
    const val DELETED = "D"
    const val UNTRACKED = "?"
    const val CONFLICTED = "U"
}

data class Diagnostic(
    val severity: Severity,
    val file: String,
    val message: String,
    val location: String,
    val source: String = "TS",
)

enum class Severity { Error, Warning, Info }

data class LanguageService(
    val initials: String,
    val name: String,
    val patterns: String,
    val running: Boolean,
    val id: String = name,
    val command: String = "",
    val rootMarkers: String = "",
    val initializationOptions: String = "{}",
    val status: LanguageServiceStatus = if (running) LanguageServiceStatus.Running else LanguageServiceStatus.Stopped,
    val processId: Int? = null,
    val statusMessage: String? = null,
)

enum class LanguageServiceStatus { Stopped, Starting, Running, Stopping, Failed }

data class WorkbenchSnapshot(
    val workspaces: List<WorkspaceSummary>,
    val files: List<FileEntry>,
    val gitChanges: List<GitChange>,
    val gitRepository: GitRepositoryState? = null,
    val diagnostics: List<Diagnostic>,
    val languageServices: List<LanguageService>,
    val languageServicesLoadError: String? = null,
    val selectedLanguageServiceId: String? = null,
    val activeWorkspace: String,
    val activePath: String,
    /** Canonical root location, unlike [activePath] which is presentation text. */
    val activeWorkspaceLocation: String? = null,
    val activeBranch: String,
    val runtimeHealthy: Boolean,
    val activeDocument: OpenDocument? = null,
    /** Ordered editor tabs. [activeDocument] must be one of these when non-null. */
    val openDocuments: List<OpenDocument> = emptyList(),
    val recentDocuments: List<RecentDocument> = emptyList(),
    /** Optional initial selection for a captured Explorer frame; real selections remain local UI state. */
    val initialExplorerSelection: String? = null,
    val folderBrowser: FolderBrowserState? = null,
    val createFolderDestination: String? = null,
    /**
     * Clone form fields.
     *
     * Held here, not in the screen, because choosing a destination navigates
     * away and back — composition state would be lost and the user would have
     * to retype the URL.
     */
    val cloneUrl: String = "",
    val cloneBranch: String = "",
    val createFolderDestinationDisplay: String? = null,
    val storage: StorageSummary? = null,
    val storageDetails: StorageDetails? = null,
    val fileIndex: List<IndexedFile> = emptyList(),
    val fileIndexTruncated: Boolean = false,
    val contentSearch: ContentSearchState = ContentSearchState.Empty,
    val runtime: RuntimeState = RuntimeState.Unknown,
    val runtimeInstallReason: RuntimeInstallReason = RuntimeInstallReason.Initial,
    /** Measured facts about the installed runtime, for the environment screen. */
    val runtimeDetails: RuntimeDetails = RuntimeDetails(),
    /** What Android allows iCode (storage, notifications, battery); null until checked. */
    val systemAccess: SystemAccess? = null,
    val terminal: TerminalSnapshot = TerminalSnapshot(),
    /** The Notifications page, newest first. */
    val notices: List<AppNotice> = emptyList(),
    /**
     * Whether the `git` binary exists in the runtime; null until checked.
     *
     * Distinguishes "this folder is not a repository" from "Git cannot run
     * here yet", which otherwise look identical to every Git surface.
     */
    val gitAvailable: Boolean? = null,
    /** The diff currently being viewed, or null when no file is open in the diff surface. */
    val activeDiff: GitFileDiff? = null,
    /** The conflicted file currently open for resolution. */
    val activeConflict: GitConflictFile? = null,
    /**
     * The change a discard has been requested for, pending confirmation.
     *
     * Discarding cannot be undone, so the change is held here until the user
     * confirms it on the confirmation surface.
     */
    val pendingDiscard: GitChange? = null,
    /** Progress of a running Git operation such as clone, fetch, pull or push. */
    val gitOperation: GitOperationState = GitOperationState.Idle,
    /** Remote authentication state; never carries the secret itself. */
    val gitAuth: GitAuthState = GitAuthState(),
    /** Git's guest-wide defaults and this repository's explicit overrides. */
    val globalGitAuthorName: String? = null,
    val globalGitAuthorEmail: String? = null,
    val localGitAuthorName: String? = null,
    val localGitAuthorEmail: String? = null,
    val gitIdentityLoaded: Boolean = false,
)

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
    data class StartFolderBrowser(val purpose: FolderBrowserPurpose) : WorkbenchIntent
    data class BrowseFolder(val path: String) : WorkbenchIntent
    data object BrowseParentFolder : WorkbenchIntent
    data object ConfirmFolderSelection : WorkbenchIntent
    data class RequestCreateFolder(val name: String) : WorkbenchIntent
    data class OpenWorkspace(val path: String) : WorkbenchIntent
    data class OpenWorkspaceTerminal(val path: String) : WorkbenchIntent
    data class ResolveWorkspaceSwitch(val path: String, val openTerminal: Boolean, val saveChanges: Boolean) : WorkbenchIntent
    data class ShowWorkspaceLocation(val path: String) : WorkbenchIntent
    data class RemoveRecentWorkspace(val path: String) : WorkbenchIntent
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
    data class UpdateDocumentText(val location: String, val text: String) : WorkbenchIntent
    /** Automatic requests carry the revision they observed; a later edit must start its own timer. */
    data class SaveDocument(
        val location: String,
        val automatic: Boolean = false,
        val expectedText: String? = null,
        val expectedWorkspace: String? = null,
    ) : WorkbenchIntent
    data class RestoreDraft(val location: String) : WorkbenchIntent
    data class DiscardDraft(val location: String) : WorkbenchIntent
    data class ConfirmRecoveredOverwrite(val location: String) : WorkbenchIntent
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

    /** Installs Git into the runtime when it is not present yet. */
    data object ProvisionGit : WorkbenchIntent
    data object RefreshGit : WorkbenchIntent
    /** [staged] selects the index-vs-HEAD comparison instead of worktree-vs-index. */
    data class OpenDiff(val path: String, val staged: Boolean) : WorkbenchIntent
    data object CloseDiff : WorkbenchIntent
    /** Irreversible; the caller must have confirmed the exact path first. */
    data class RequestDiscardChanges(val path: String) : WorkbenchIntent
    data object CancelDiscardChanges : WorkbenchIntent
    /** [status] is the porcelain code, because each one discards differently. */
    data class DiscardChanges(val path: String, val status: String) : WorkbenchIntent
    /** Ends a half-finished merge, rebase, cherry-pick or revert. */
    data object AbortInProgress : WorkbenchIntent
    data object FetchRemote : WorkbenchIntent
    data object PullRemote : WorkbenchIntent
    data object PushRemote : WorkbenchIntent
    data class OpenConflict(val path: String) : WorkbenchIntent
    data object CloseConflict : WorkbenchIntent
    data class ResolveConflict(val path: String, val choice: GitConflictChoice) : WorkbenchIntent
    data class MarkConflictResolved(val path: String) : WorkbenchIntent
    data class UpdateCloneForm(val url: String, val branch: String) : WorkbenchIntent
    data class CloneRepository(val url: String, val destination: String, val directoryName: String) : WorkbenchIntent

    data class SelectGitProvider(val provider: GitProvider) : WorkbenchIntent
    data class SelectGitAuthMethod(val method: GitAuthMethod) : WorkbenchIntent
    /** The token is consumed straight into encrypted storage and never echoed back. */
    data class SaveGitToken(val host: String, val username: String, val token: String) : WorkbenchIntent
    data object ClearGitToken : WorkbenchIntent
    data object GenerateSshKey : WorkbenchIntent
    data class TestGitConnection(val url: String) : WorkbenchIntent
    data object RefreshGitAuth : WorkbenchIntent
    data class SetLanguageServiceRunning(val name: String, val running: Boolean) : WorkbenchIntent
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
    data object RemoveStaleRecents : WorkbenchIntent
    /** Rebuilds the base system, keeping the guest home directory. */
    data object RepairRuntime : WorkbenchIntent
    /** Rebuilds the base system and discards the guest home directory. */
    data object ResetRuntime : WorkbenchIntent
    data object OpenTerminalSession : WorkbenchIntent
    data class SelectTerminalSession(val id: String) : WorkbenchIntent

    /** Bring a session to the front: its workspace, the Terminal panel, that tab (from its notification). */
    data class ShowTerminalSession(val id: String) : WorkbenchIntent
    data class ReorderTerminalSession(val movingId: String, val targetId: String) : WorkbenchIntent
    data class CloseTerminalSession(val id: String) : WorkbenchIntent
    data class OpenExternalUrl(val url: String) : WorkbenchIntent

    data object Refresh : WorkbenchIntent
}

sealed interface WorkbenchEffect {
    data class RequestSystemPermission(val permission: SystemPermission) : WorkbenchEffect
    data class SystemPermissionResolved(val permission: SystemPermission, val granted: Boolean) : WorkbenchEffect
    data class OpenSystemSettings(val permission: SystemPermission) : WorkbenchEffect
    data object FolderBrowserOpened : WorkbenchEffect
    data class FolderDestinationSelected(val path: String) : WorkbenchEffect

    data class WorkspaceOpened(val location: String) : WorkbenchEffect
    data class WorkspaceTerminalOpened(val location: String) : WorkbenchEffect
    data class WorkspaceItemCreated(val requestId: String, val location: String) : WorkbenchEffect
    data class WorkspaceItemCreationFailed(val requestId: String, val message: String) : WorkbenchEffect
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
    data object RuntimeRebuildStarted : WorkbenchEffect
    data class Message(val text: String) : WorkbenchEffect

    /** Hand a URL to Android's browser; the app never renders it (PR-TERM-09). */
    data class OpenExternalUrl(val url: String) : WorkbenchEffect
}

/**
 * Finds http(s) URLs in terminal and output text so they can be activated.
 *
 * Trailing punctuation is trimmed because a URL printed at the end of a
 * sentence would otherwise carry the full stop into the browser.
 */
object UrlDetector {
    private val pattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+")
    private val trailing = charArrayOf('.', ',', ')', ']', '"', '\'', ';', ':')

    fun find(text: String): List<String> =
        pattern.findAll(text)
            .map { it.value.trimEnd(*trailing) }
            .filter { it.length > "https://".length }
            .distinct()
            .toList()

    /** The URL covering character [index] of [line], if any: what a long press on it opens. */
    fun at(line: String, index: Int): String? =
        pattern.findAll(line)
            .firstOrNull { index in it.range }
            ?.value
            ?.trimEnd(*trailing)
            ?.takeIf { it.length > "https://".length }
}

object WorkspaceNameRules {
    fun validationError(rawName: String): String? = nameError(rawName, "folder")

    fun fileValidationError(rawName: String): String? = nameError(rawName, "file")

    fun workspaceFolderValidationError(rawName: String): String? =
        validationError(rawName) ?: if (rawName.trim() == ".git") "The .git path is reserved for Git metadata" else null

    private fun nameError(rawName: String, kind: String): String? {
        val name = rawName.trim()
        val label = if (kind == "file") "File" else "Folder"
        return when {
            name.isEmpty() -> "Enter a $kind name"
            name == "." || name == ".." -> "Use a specific $kind name"
            kind == "file" && name == ".git" -> "The .git path is reserved for Git metadata"
            name.any { it == '/' || it == '\\' || it == '\u0000' } -> "$label names cannot contain / or \\"
            name.toByteArray(Charsets.UTF_8).size > MAX_UTF8_BYTES -> "$label name is too long"
            else -> null
        }
    }

    private const val MAX_UTF8_BYTES = 255
}

/**
 * Stable UI boundary for the later runtime/storage/Git implementation.
 *
 * Production adapters will implement this interface. Compose never shells out or reads files directly.
 */
interface WorkbenchGateway {
    val snapshot: StateFlow<WorkbenchSnapshot>
    val effects: Flow<WorkbenchEffect>
        get() = emptyFlow()

    fun dispatch(intent: WorkbenchIntent)

    fun close() = Unit
}
