package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.run.RunCoordinator
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.search.WorkspaceSearchEngine
import dev.icode.mobile.data.workspace.WorkspaceFileSource

/** Every tool the assistant has (§4.3), made for the workspace and the chat a turn works in. */
internal class AssistantToolbox(
    private val source: WorkspaceFileSource,
    private val search: WorkspaceSearchEngine,
    private val git: CliGitService,
    private val execution: RuntimeExecution,
    private val runs: RunCoordinator,
    private val runtimeInstalled: () -> Boolean,
    /** An open tab holds unsaved edits at this location. */
    private val unsavedEdits: (String) -> Boolean,
    /** Files changed outside the editor: the tree, Git and the index refresh. */
    private val changed: () -> Unit,
) {
    private val shell = AssistantShell(execution.rootfs, execution.guest, execution.environment, execution.processes)
    private val processes = execution.processes

    fun forWorkspace(workspace: AssistantWorkspace, chat: String): List<AssistantTool> {
        val writer = WorkspaceWriter(workspace, source, unsavedEdits, changed)
        return listOf(
            ListFilesTool(workspace, search),
            ReadFileTool(workspace, source),
            SearchTextTool(workspace, search),
            GitReadTool(workspace, git, diff = false),
            GitReadTool(workspace, git, diff = true),
            WriteFileTool(writer),
            EditFileTool(writer),
            RunCommandTool(workspace, chat, shell, runtimeInstalled, changed),
            StartProcessTool(workspace, chat, shell, processes, runtimeInstalled),
            ProcessStatusTool(processes),
            SendProcessInputTool(processes),
            StopProcessTool(processes),
            RunAndroidAppTool(workspace, runs),
            ListRunCommandsTool(workspace, runs),
            SaveRunCommandTool(workspace, runs),
            DeleteRunCommandTool(workspace, runs),
        )
    }
}
