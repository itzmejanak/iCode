package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.phone.PhoneControl
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
    /** The phone, for every chat while phone control is on (docs/phone-agent-plan.md §5.3). */
    private val phone: PhoneControl,
) {
    private val shell = AssistantShell(execution.rootfs, execution.guest, execution.environment, execution.processes)
    private val processes = execution.processes

    /**
     * The tools of a chat in [workspace], on [step]: the phone's while phone control is on; the Run
     * tab's for a workspace only.
     */
    fun forWorkspace(workspace: AssistantWorkspace, chat: String, step: AssistantStep): List<AssistantTool> {
        val writer = WorkspaceWriter(workspace, source, unsavedEdits, changed)
        val files = listOf(
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
        ) + (if (phone.on) PhoneTools(phone, step).all() else emptyList())
        if (workspace.phone) return files
        return files + listOf(
            RunAndroidAppTool(workspace, runs),
            ListRunCommandsTool(workspace, runs),
            SaveRunCommandTool(workspace, runs),
            DeleteRunCommandTool(workspace, runs),
        )
    }
}
