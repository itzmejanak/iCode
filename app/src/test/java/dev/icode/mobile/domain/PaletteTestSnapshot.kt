package dev.icode.mobile.domain

private const val ROOT = "/storage/emulated/0/projects/app"

/** A workspace where every palette command can run: a branch tracking its remote, a staged change, an unsaved file. */
internal val paletteReadySnapshot = WorkbenchSnapshot(
    workspaces = emptyList(),
    files = emptyList(),
    gitChanges = listOf(GitChange(GitStatusCodes.ADDED, "a.kt", "src/a.kt", staged = true)),
    gitRepository = GitRepositoryState(
        workTreePath = ROOT,
        branch = "main",
        hasCommits = true,
        remoteName = "origin",
        authorName = null,
        authorEmail = null,
        branches = listOf("main"),
        lastCommit = null,
        upstream = "origin/main",
    ),
    diagnostics = emptyList(),
    languageServices = emptyList(),
    activeWorkspace = "app",
    activePath = ROOT,
    activeWorkspaceLocation = ROOT,
    activeBranch = "main",
    runtimeHealthy = true,
    gitAvailable = true,
    activeDocument = OpenDocument(
        name = "a.kt",
        location = "$ROOT/src/a.kt",
        text = "fun main() {}\n",
        encoding = TextEncoding.Utf8,
        lineEnding = LineEnding.Lf,
        dirty = true,
        readOnly = false,
    ),
)
