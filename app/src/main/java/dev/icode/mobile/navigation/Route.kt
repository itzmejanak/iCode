package dev.icode.mobile.navigation

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
) {
    Splash("3:11", "Splash", RouteGroup.ENTRY),
    RuntimeInstalling("3:27", "Runtime installing", RouteGroup.ENTRY),
    PermissionExplanation("3:90", "Permission explanation", RouteGroup.ENTRY),
    // Owner's request (2026-10-29): ask once whether to share anonymous device info (docs/device-telemetry-plan.md).
    DeviceInfoConsent(null, "Device info", RouteGroup.ENTRY),
    LauncherHome("3:124", "Launcher home", RouteGroup.ENTRY),
    LauncherEmpty("3:202", "Launcher empty", RouteGroup.ENTRY),
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
    CloneProgress("3:278", "Clone progress", RouteGroup.ENTRY),
    WorkspaceExplorer("3:456", "Workspace explorer", RouteGroup.WORKSPACE),
    WorkspaceEditor("3:519", "Workspace editor", RouteGroup.WORKSPACE),
    WorkspaceSearch("3:586", "Workspace search", RouteGroup.WORKSPACE),
    SourceControl("3:641", "Source control", RouteGroup.TOOLS),
    DiffView("3:706", "Diff view", RouteGroup.TOOLS),
    QuickOpen("3:760", "Quick open", RouteGroup.TOOLS),
    BranchSelector("3:796", "Branch selector", RouteGroup.TOOLS, sheet = true),
    // Owner's request (2026-10-01): the repository is chosen as the branch is, from Source Control's
    // bottom bar (workspace-destinations-plan.md D7).
    RepositorySelector(null, "Repository selector", RouteGroup.TOOLS, sheet = true),
    // Owner's request (2026-10-22): HEAD's history from Last Commit's corner (docs/git-history-plan.md).
    GitHistory(null, "Commit history", RouteGroup.TOOLS, sheet = true),
    EditorFind("3:835", "Editor find", RouteGroup.TOOLS),
    TerminalExpanded("3:1070", "Terminal expanded", RouteGroup.TOOLS),
    ProblemsPanel("3:1127", "Problems panel", RouteGroup.TOOLS),
    SettingsRoot("3:1196", "Settings", RouteGroup.SETTINGS),
    SettingsEditor("3:1277", "Editor settings", RouteGroup.SETTINGS),
    SettingsLanguages("3:1344", "Language services", RouteGroup.SETTINGS),
    SettingsLanguageDetail("3:1393", "Language detail", RouteGroup.SETTINGS),
    SettingsRuntime("3:1455", "Runtime environment", RouteGroup.SETTINGS),
    ConflictResolution("3:1509", "Conflict resolution", RouteGroup.TOOLS),
    EditorEmpty("2003:19", "Editor empty state", RouteGroup.WORKSPACE),
    CommandPalette("2003:166", "Command palette", RouteGroup.TOOLS),
    OutputPanel("2003:208", "Output panel", RouteGroup.TOOLS),
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
    Assistant(null, "Assistant", RouteGroup.TOOLS),
    AssistantSettings(null, "Assistant settings", RouteGroup.SETTINGS),
    AssistantProvider(null, "Assistant provider", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): Assistant settings lead to their own pages, providers among them.
    AssistantProviders(null, "Providers", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): speak to the assistant, hear its answers (phone-agent-plan.md §5.8).
    VoiceSettings(null, "Voice", RouteGroup.SETTINGS),
    // Owner's request (2026-11-03): the assistant on the whole phone (phone-agent-plan.md §5.9, P7).
    PhoneControl(null, "Phone control", RouteGroup.SETTINGS),
    ShizukuSetup(null, "Shizuku", RouteGroup.SETTINGS),
    // Owner's request (2026-10-08): Git's credentials and the commit messages' model (commit-message-generator-plan.md M8).
    SourceControlSettings(null, "Source control settings", RouteGroup.SETTINGS),
}
