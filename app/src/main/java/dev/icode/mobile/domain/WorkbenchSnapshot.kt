package dev.icode.mobile.domain

data class WorkbenchSnapshot(
    val workspaces: List<WorkspaceSummary>,
    val files: List<FileEntry>,
    val gitChanges: List<GitChange>,
    val gitRepository: GitRepositoryState? = null,
    /** The repositories in the open workspace (docs/workspace-destinations-plan.md §2.6); [gitRepository] is the chosen one's. */
    val gitRepositories: List<String> = emptyList(),
    /** Why Git could not read the open workspace's repository, in Git's words; its files open all the same. */
    val gitFailure: String? = null,
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
    /** The folder picked for Import, until it is imported or another is picked. */
    val importBrowser: ImportBrowserState? = null,
    val importSource: ImportSource? = null,
    val storage: StorageSummary? = null,
    val storageDetails: StorageDetails? = null,
    /** The Workspaces page's file explorer: where it is and what is there; null until it is opened. */
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
    val terminal: TerminalSnapshot = TerminalSnapshot(),
    /** The Notifications page, newest first. */
    val notices: List<AppNotice> = emptyList(),
    /** Where the next clone or import goes; none, a new workspace (docs/workspace-destinations-plan.md). */
    val projectDestination: ProjectDestination? = null,
    /** What is cut or copied, waiting for Paste — the Explorer's and the Workspaces page's one clipboard. */
    val clipboard: ItemClipboard? = null,
    /** Long operations running, or failed and not yet seen (docs/long-operations-plan.md). */
    val tasks: List<WorkTask> = emptyList(),
    /** The runtime's components: what dpkg has, what each would download, and the apt run in hand. */
    val components: RuntimeComponentsState = RuntimeComponentsState(),
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
)
