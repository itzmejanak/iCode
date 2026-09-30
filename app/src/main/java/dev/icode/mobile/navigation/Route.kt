package dev.icode.mobile.navigation

enum class RouteGroup(val label: String) {
    ENTRY("Entry & launcher"),
    WORKSPACE("Workspace"),
    TOOLS("Tools & Git"),
    SETTINGS("Settings"),
    RECOVERY("Recovery & dialogs"),
}

enum class Route(
    /** The Figma frame this route draws; null for a page the design does not have. */
    val figmaNode: String?,
    val title: String,
    val group: RouteGroup,
) {
    Splash("3:11", "Splash", RouteGroup.ENTRY),
    RuntimeInstalling("3:27", "Runtime installing", RouteGroup.ENTRY),
    PermissionExplanation("3:90", "Permission explanation", RouteGroup.ENTRY),
    LauncherHome("3:124", "Launcher home", RouteGroup.ENTRY),
    LauncherEmpty("3:202", "Launcher empty", RouteGroup.ENTRY),
    // Owner's request (2026-09-25): notices move off the launcher onto their own page.
    Notifications(null, "Notifications", RouteGroup.ENTRY),
    // Workspaces live in iCode's storage (2026-09-30): Import brings a folder in from phone storage.
    ImportWorkspace(null, "Import", RouteGroup.ENTRY),
    CloneRepository("3:239", "Clone repository", RouteGroup.ENTRY),
    CloneProgress("3:278", "Clone progress", RouteGroup.ENTRY),
    WorkspaceExplorer("3:456", "Workspace explorer", RouteGroup.WORKSPACE),
    WorkspaceEditor("3:519", "Workspace editor", RouteGroup.WORKSPACE),
    WorkspaceSearch("3:586", "Workspace search", RouteGroup.WORKSPACE),
    SourceControl("3:641", "Source control", RouteGroup.TOOLS),
    DiffView("3:706", "Diff view", RouteGroup.TOOLS),
    QuickOpen("3:760", "Quick open", RouteGroup.TOOLS),
    BranchSelector("3:796", "Branch selector", RouteGroup.TOOLS),
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
    CreateFolder("2003:77", "Create folder", RouteGroup.ENTRY),
    CommandPalette("2003:166", "Command palette", RouteGroup.TOOLS),
    OutputPanel("2003:208", "Output panel", RouteGroup.TOOLS),
    CommitPush("2003:246", "Commit and push", RouteGroup.TOOLS),
    PullFetchProgress("2003:290", "Pull progress", RouteGroup.TOOLS),
    GitAuth("2003:321", "Git credentials", RouteGroup.TOOLS),
    AppearanceSettings("2003:438", "Appearance", RouteGroup.SETTINGS),
    TerminalSettings("2003:500", "Terminal settings", RouteGroup.SETTINGS),
    StorageSettings("2003:575", "Storage settings", RouteGroup.SETTINGS),
    About("2003:656", "About iCode", RouteGroup.SETTINGS),
    DeleteConfirmation("2003:731", "Delete confirmation", RouteGroup.RECOVERY),
    ExternalFileChange("2003:770", "External file change", RouteGroup.RECOVERY),
    DocumentRecovery("2003:813", "Document recovery", RouteGroup.RECOVERY),
    WorkspaceSettings("2003:886", "Workspace settings", RouteGroup.SETTINGS),
}
