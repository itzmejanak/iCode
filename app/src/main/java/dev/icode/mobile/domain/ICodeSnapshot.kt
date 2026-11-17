package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ExplorerState
import dev.icode.mobile.domain.files.FilesBrowserState
import dev.icode.mobile.domain.notices.Notice
import dev.icode.mobile.domain.phone.PhoneAccess
import dev.icode.mobile.domain.voice.VoiceState

data class ICodeSnapshot(
    val workspaces: List<WorkspaceSummary>,
    /** [workspaces] has been read once: before, an empty list is "not read yet", not "none". */
    val workspacesLoaded: Boolean = false,
    /** What the user asked that runs now (docs/design-and-navigation.md): published on the tap, gone when done. */
    val busy: Set<Busy> = emptySet(),
    val files: List<FileEntry>,
    val gitChanges: List<GitChange>,
    val gitRepository: GitRepositoryState? = null,
    /** The repositories in the open workspace (docs/workspaces-and-files.md); [gitRepository] is the chosen one's. */
    val gitRepositories: List<String> = emptyList(),
    /** Why Git could not read the open workspace's repository, in Git's words; its files open all the same. */
    val gitFailure: String? = null,
    /** A build's problems (Build & Run); [problems] is these and the language servers' together. */
    val diagnostics: List<Diagnostic>,
    val languageServices: List<LanguageService>,
    val languageServicesLoadError: String? = null,
    val selectedLanguageServiceId: String? = null,
    /** The language servers' problems in the open workspace (docs/editor-and-language-services.md), errors first. */
    val languageDiagnostics: List<Diagnostic> = emptyList(),
    /** The selected language service's log: its stderr's last lines and what iCode saw of it. */
    val languageServiceLog: List<String> = emptyList(),
    /** A language server's answer shown over the editor: definitions, references, hover, code actions. */
    val languageResult: LanguageResult? = null,
    val activeWorkspace: String,
    val activePath: String,
    /** Canonical root location, unlike [activePath] which is presentation text. */
    val activeWorkspaceLocation: String? = null,
    val activeBranch: String,
    val runtimeHealthy: Boolean,
    val activeDocument: OpenDocument? = null,
    /** Ordered editor tabs. [activeDocument] must be one of these when non-null. */
    val openDocuments: List<OpenDocument> = emptyList(),
    /** Drafts found when iCode started, until each is restored or discarded; newest first. */
    val recoverableDrafts: List<RecoverableDraft> = emptyList(),
    /** Open files changed on disk under unsaved edits, oldest first; the screen answers the first. */
    val externalChanges: List<ExternalChange> = emptyList(),
    /** Optional initial selection for a captured Explorer frame; real selections remain local UI state. */
    val initialExplorerSelection: String? = null,
    /**
     * Clone form fields.
     *
     * Held here, not in the screen, because the credentials page is visited
     * from the form and back — composition state would be lost and the user
     * would have to retype the URL.
     */
    val cloneUrl: String = "",
    val cloneName: String = "",
    val cloneBranch: String = "",
    val storage: StorageSummary? = null,
    val storageDetails: StorageDetails? = null,
    /** The file explorer, of iCode's storage or phone storage: where it is and what is there; null until it is opened. */
    val filesBrowser: FilesBrowserState? = null,
    /** Each workspace's size by location, once measured: measured on its own, apart from the runtime. */
    val workspaceSizes: Map<String, StorageMeasurement> = emptyMap(),
    val fileIndex: List<IndexedFile> = emptyList(),
    val fileIndexTruncated: Boolean = false,
    val searchEngine: SearchEngineState = SearchEngineState.Unknown,
    val contentSearch: ContentSearchState = ContentSearchState.Empty,
    val runtime: RuntimeState = RuntimeState.Unknown,
    val runtimeInstallReason: RuntimeInstallReason = RuntimeInstallReason.Initial,
    /** Measured facts about the installed runtime, for the environment screen. */
    val runtimeDetails: RuntimeDetails = RuntimeDetails(),
    /** What Android allows iCode (storage, notifications, battery); null until checked. */
    val systemAccess: SystemAccess? = null,
    /** What iCode may do on the phone (docs/voice-and-phone-control.md). */
    val phone: PhoneAccess = PhoneAccess(),
    /** Voice in and out: its settings, models, the microphone and what is heard (docs/voice-and-phone-control.md). */
    val voice: VoiceState = VoiceState(),
    val terminal: TerminalSnapshot = TerminalSnapshot(),
    /** The Notifications page, newest first. */
    val notices: List<Notice> = emptyList(),
    /** Where the next clone goes; none, a new workspace (docs/workspaces-and-files.md). */
    val projectDestination: ProjectDestination? = null,
    /** Every explorer's clipboard, question, undo and redo, and Trash (docs/workspaces-and-files.md). */
    val explorer: ExplorerState = ExplorerState(),
    /** Long operations running, or failed and not yet seen (docs/runtime-and-terminal.md). */
    val tasks: List<WorkTask> = emptyList(),
    /** The runtime's tools: what dpkg has, what each would download, and the apt run in hand. */
    val runtimeTools: RuntimeToolsState = RuntimeToolsState(),
    /** The iCode website: Settings › Application's links, update check, device-info consent. */
    val web: WebState = WebState(),
    /** The Run tab: what the open workspace can start, and how each run went. */
    val run: RunState = RunState(),
    /** What gpg-agent asks through iCode's pinentry, oldest first: the sheet shows the first. */
    val gpgPrompts: List<GpgPrompt> = emptyList(),
    /**
     * Whether the `git` binary exists in the runtime; null until checked.
     *
     * Distinguishes "this folder is not a repository" from "Git cannot run
     * here yet", which otherwise look identical to every Git surface.
     */
    val gitAvailable: Boolean? = null,
    /** Each open file's Git changes, by location, for those Git's index holds (docs/editor-and-language-services.md). */
    val lineChanges: Map<String, DocumentLineChanges> = emptyMap(),
    /** The diff currently being viewed, or null when no file is open in the diff surface. */
    val activeDiff: GitFileDiff? = null,
    /** HEAD's history as the History sheet last read it (docs/source-control.md); null before it first opens. */
    val gitHistory: GitHistory? = null,
    /** The conflicted file currently open for resolution. */
    val activeConflict: GitConflictFile? = null,
    /**
     * The change a discard has been requested for, pending confirmation.
     *
     * Discarding cannot be undone, so the change is held here until the user
     * confirms it on the confirmation surface.
     */
    val pendingDiscard: GitDiscard? = null,
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
    /** Managed processes, of every workspace, running and ended, oldest first. */
    val processes: List<ManagedProcess> = emptyList(),
    /** iCode's assistant: its settings, the shown workspace's chat and the turn in hand. */
    val assistant: AssistantState = AssistantState(),
    /** The assistant's MCP servers (docs/assistant-and-mcp.md): their settings, connections and questions. */
    val mcp: McpState = McpState(),
    /** The coding agents chats can be answered by (docs/assistant-and-mcp.md): their processes, and the shown chat's. */
    val agents: AgentsState = AgentsState(),
) {
    /** [location], in the open workspace, as the runtime names it: what Copy path gives. */
    fun runtimePathOf(location: String): String = activeWorkspaceLocation?.let { WorkspaceRuntimePath.within(it, activePath, location) } ?: location

    /** Every problem the Problems panel, the editor and the counts show: a build's, then the language servers'. */
    val problems: List<Diagnostic> by lazy { diagnostics + languageDiagnostics }
}
