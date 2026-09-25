package dev.icode.mobile.domain

/** Commands exposed by the palette. IDs, rather than displayed titles, select behavior. */
enum class PaletteCommandId {
    PushBranch,
    PullBranch,
    CommitStaged,
    SaveCurrent,
    QuickOpen,
    SearchWorkspace,
    OpenTerminal,
    OpenSourceControl,
}

data class PaletteCommand(
    val id: PaletteCommandId,
    val group: String,
    val title: String,
    val keywords: String,
    /** A disabled command stays discoverable and explains its missing prerequisite. */
    val unavailableReason: String? = null,
) {
    val available: Boolean get() = unavailableReason == null
}

/** Pure query and capability policy; no navigation or platform work belongs here. */
object PaletteCommands {
    fun search(snapshot: WorkbenchSnapshot, query: String): List<PaletteCommand> {
        val workspace = snapshot.activeWorkspaceLocation != null
        val repository = snapshot.gitRepository
        val gitReason = when {
            !workspace -> "Open a workspace first"
            snapshot.gitAvailable == null -> "Checking Git availability"
            snapshot.gitAvailable == false -> "Git is not installed"
            repository == null -> "Initialize Git first"
            snapshot.gitOperation is GitOperationState.Running -> "A Git operation is running"
            else -> null
        }
        val remoteReason = gitReason ?: if (repository?.hasRemote == true) null else "Add a Git remote first"
        val commands = listOf(
            PaletteCommand(PaletteCommandId.PushBranch, "Git commands", "Git: Push Branch", "upload remote sync", remoteReason),
            PaletteCommand(PaletteCommandId.PullBranch, "Git commands", "Git: Pull (from remote)", "download remote sync", remoteReason),
            PaletteCommand(
                PaletteCommandId.CommitStaged,
                "Git commands",
                "Git: Commit Staged…",
                "commit message changes",
                gitReason ?: if (snapshot.gitChanges.any { it.staged }) null else "Stage changes first",
            ),
            PaletteCommand(
                PaletteCommandId.SaveCurrent,
                "File commands",
                "File: Save Current",
                "save document",
                when {
                    snapshot.activeDocument == null -> "Open a file first"
                    snapshot.activeDocument.readOnly -> "This file is read-only"
                    !snapshot.activeDocument.dirty -> "No unsaved changes"
                    else -> null
                },
            ),
            PaletteCommand(PaletteCommandId.QuickOpen, "File commands", "File: Quick Open", "find open file", if (workspace) null else "Open a workspace first"),
            PaletteCommand(PaletteCommandId.SearchWorkspace, "File commands", "Search: Workspace", "find text content", if (workspace) null else "Open a workspace first"),
            PaletteCommand(PaletteCommandId.OpenTerminal, "Tools", "Terminal: Open", "shell command line"),
            PaletteCommand(PaletteCommandId.OpenSourceControl, "Tools", "Git: Source Control", "status changes", if (workspace) null else "Open a workspace first"),
        )
        val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return commands
        return commands.filter { command ->
            val searchable = "${command.title} ${command.keywords} ${command.group}"
            terms.all { searchable.contains(it, ignoreCase = true) }
        }
    }
}
