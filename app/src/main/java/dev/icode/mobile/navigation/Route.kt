package dev.icode.mobile.navigation

import dev.icode.mobile.domain.WorkspaceTool

enum class RouteGroup(val label: String) {
    ENTRY("Entry & launcher"),
    WORKSPACE("Workspace"),
    TOOLS("Tools & Git"),
    SETTINGS("Settings"),
    RECOVERY("Recovery & sheets"),
}

enum class Route(
    /** The design frame this route draws; null for a page the design does not have. */
    val figmaNode: String?,
    val title: String,
    val group: RouteGroup,
    /** Drawn as a sheet over the live screen below it, not as a page of its own. */
    val sheet: Boolean = false,
    /**
     * The tool whose main screen this is (docs/dock-navigation-plan.md D1): asked for, that tool is
     * switched to rather than a page pushed. Null for a page, which opens in the tool it is opened from.
     */
    val tool: WorkspaceTool? = null,
    /** Asked for from inside the workspace, the app's own pages show again: the Launcher. */
    val leavesWorkspace: Boolean = false,
    /** The page that makes a workspace: once it opens, it takes this page's place, so Back does not show it again. */
    val makesWorkspace: Boolean = false,
) {
    Splash("3:11", "Splash", RouteGroup.ENTRY),
    RuntimeInstalling("3:27", "Runtime installing", RouteGroup.ENTRY),
    PermissionExplanation("3:90", "Permission explanation", RouteGroup.ENTRY),
    // Owner's request (2026-10-29): ask once whether to share anonymous device info (docs/device-telemetry-plan.md).
    DeviceInfoConsent(null, "Device info", RouteGroup.ENTRY),
    LauncherHome("3:124", "Launcher home", RouteGroup.ENTRY, leavesWorkspace = true),
    LauncherEmpty("3:202", "Launcher empty", RouteGroup.ENTRY, leavesWorkspace = true),
    // Owner's request (2026-09-25): notices move off the launcher onto their own page.
    Notifications(null, "Notifications", RouteGroup.ENTRY),
    // Owner's request (2026-09-30): choose the runtime's tools, as Android Studio's setup does
    // (docs/runtime-tools-plan.md §3.5).
    RuntimeTools(null, "Runtime tools", RouteGroup.ENTRY),
    // Owner's request (2026-10-01, 2026-10-14): every workspace, managed in one place, and phone storage
    // beside it — the file explorer of both places, drawing Open Folder's frame (docs/storage-explorer-plan.md).
    Workspaces("2003:117", "Workspaces", RouteGroup.ENTRY),
    // The same file explorer, choosing where a clone goes (workspace-destinations-plan.md §2.2).
    ChooseDestination(null, "Choose where", RouteGroup.ENTRY),
    CloneRepository("3:239", "Clone repository", RouteGroup.ENTRY),
    CloneProgress("3:278", "Clone progress", RouteGroup.ENTRY, makesWorkspace = true),
    WorkspaceExplorer("3:456", "Workspace explorer", RouteGroup.WORKSPACE, tool = WorkspaceTool.Explorer),
    WorkspaceEditor("3:519", "Workspace editor", RouteGroup.WORKSPACE, tool = WorkspaceTool.Editor),
    WorkspaceSearch("3:586", "Workspace search", RouteGroup.WORKSPACE, tool = WorkspaceTool.Search),
    SourceControl("3:641", "Source control", RouteGroup.TOOLS, tool = WorkspaceTool.SourceControl),
    DiffView("3:706", "Diff view", RouteGroup.TOOLS),
    // Search's other face: the same screen, finding files by name.
    QuickOpen("3:760", "Quick open", RouteGroup.TOOLS, tool = WorkspaceTool.Search),
    BranchSelector("3:796", "Branch selector", RouteGroup.TOOLS, sheet = true),
    // Owner's request (2026-10-01): the repository is chosen as the branch is, from Source Control's
    // bottom bar (workspace-destinations-plan.md D7).
    RepositorySelector(null, "Repository selector", RouteGroup.TOOLS, sheet = true),
    // Owner's request (2026-10-22): HEAD's history from Last Commit's corner (docs/git-history-plan.md).
    GitHistory(null, "Commit history", RouteGroup.TOOLS, sheet = true),
    EditorFind("3:835", "Editor find", RouteGroup.TOOLS),
    TerminalExpanded("3:1070", "Terminal expanded", RouteGroup.TOOLS, tool = WorkspaceTool.Terminal),
    // The design's frames of Terminal's other tabs: asked for, Terminal on that tab (ToolPanelTab).
    ProblemsPanel("3:1127", "Problems panel", RouteGroup.TOOLS, tool = WorkspaceTool.Terminal),
    SettingsRoot("3:1196", "Settings", RouteGroup.SETTINGS),
    SettingsEditor("3:1277", "Editor settings", RouteGroup.SETTINGS),
    SettingsLanguages("3:1344", "Language services", RouteGroup.SETTINGS),
    SettingsLanguageDetail("3:1393", "Language detail", RouteGroup.SETTINGS),
    SettingsRuntime("3:1455", "Runtime environment", RouteGroup.SETTINGS),
    ConflictResolution("3:1509", "Conflict resolution", RouteGroup.TOOLS),
    // The Editor's face with no file open, drawn as its own frame for the design's captures.
    EditorEmpty("2003:19", "Editor empty state", RouteGroup.WORKSPACE, tool = WorkspaceTool.Editor),
    CommandPalette("2003:166", "Command palette", RouteGroup.TOOLS),
    OutputPanel("2003:208", "Output panel", RouteGroup.TOOLS, tool = WorkspaceTool.Terminal),
    CommitPush("2003:246", "Commit and push", RouteGroup.TOOLS),
    PullFetchProgress("2003:290", "Pull progress", RouteGroup.TOOLS),
    GitAuth("2003:321", "Git credentials", RouteGroup.TOOLS),
    AppearanceSettings("2003:438", "Appearance", RouteGroup.SETTINGS),
    TerminalSettings("2003:500", "Terminal settings", RouteGroup.SETTINGS),
    StorageSettings("2003:575", "Storage settings", RouteGroup.SETTINGS),
    // Owner's request (2026-10-27): what storage holds, kind by kind, and the explorer's views of it
    // (docs/explorer-gaps-plan.md §3.10).
    ManageStorage(null, "Manage storage", RouteGroup.SETTINGS),
    About("2003:656", "About iCode", RouteGroup.SETTINGS),
    ExternalFileChange("2003:770", "External file change", RouteGroup.RECOVERY),
    DocumentRecovery("2003:813", "Document recovery", RouteGroup.RECOVERY),
    WorkspaceSettings("2003:886", "Workspace settings", RouteGroup.SETTINGS),
    // Owner's request (2026-10-01): iCode's own assistant, on Koog (docs/assistant-koog-plan.md).
    Assistant(null, "Assistant", RouteGroup.TOOLS, tool = WorkspaceTool.Assistant),
    AssistantSettings(null, "Assistant settings", RouteGroup.SETTINGS),
    AssistantProvider(null, "Assistant provider", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): Assistant settings lead to their own pages, providers among them.
    AssistantProviders(null, "Providers", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): speak to the assistant, hear its answers (phone-agent-plan.md §5.8).
    VoiceSettings(null, "Voice", RouteGroup.SETTINGS),
    // Owner's request (2026-11-06): speech recognition with any provider (speech-plan.md §5).
    SpeechProviders(null, "Speech providers", RouteGroup.SETTINGS),
    SpeechProvider(null, "Speech provider", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): the assistant on the whole phone (phone-agent-plan.md §5.9, P7).
    PhoneControl(null, "Phone control", RouteGroup.SETTINGS),
    ShizukuSetup(null, "Shizuku", RouteGroup.SETTINGS),
    // Owner's request (2026-11-05): MCP servers' tools in the assistant's chats (docs/mcp-plan.md §4).
    McpServers(null, "MCP servers", RouteGroup.SETTINGS),
    McpServer(null, "MCP server", RouteGroup.SETTINGS),
    // Owner's request (2026-10-08): Git's credentials and the commit messages' model (commit-message-generator-plan.md M8).
    SourceControlSettings(null, "Source control settings", RouteGroup.SETTINGS),
}

/**
 * Asked for while the app's pages are on screen, it opens there, outside the workspace: Terminal's own
 * tab, its tabs then starting in `$HOME`, and the Assistant, on the chats in no workspace (docs/home-plan.md §2).
 */
val Route.opensOutside: Boolean get() = this == Route.TerminalExpanded || this == Route.Assistant

/** The route each tool opens on, the first time and when its main screen is asked for by its tool. */
val WorkspaceTool.home: Route
    get() = when (this) {
        WorkspaceTool.Explorer -> Route.WorkspaceExplorer
        WorkspaceTool.Editor -> Route.WorkspaceEditor
        WorkspaceTool.Search -> Route.WorkspaceSearch
        WorkspaceTool.SourceControl -> Route.SourceControl
        WorkspaceTool.Assistant -> Route.Assistant
        WorkspaceTool.Terminal -> Route.TerminalExpanded
    }
