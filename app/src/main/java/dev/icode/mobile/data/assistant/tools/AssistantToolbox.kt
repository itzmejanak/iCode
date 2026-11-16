package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.git.CliGitService
import dev.icode.mobile.data.language.LanguageServices
import dev.icode.mobile.data.language.languageServiceTools
import dev.icode.mobile.data.phone.PhoneControl
import dev.icode.mobile.data.run.RunCoordinator
import dev.icode.mobile.data.runtime.RuntimeExecution
import dev.icode.mobile.data.runtime.TerminalRuns
import dev.icode.mobile.data.runtime.TerminalSessions
import dev.icode.mobile.data.search.WorkspaceSearchEngine
import dev.icode.mobile.data.voice.speech.SpeechProviders
import dev.icode.mobile.data.voice.speech.speechProviderTools
import dev.icode.mobile.data.workspace.WorkspaceFileSource
import java.io.File

/** Every tool the assistant has, made for the workspace and the chat a turn works in. */
internal class AssistantToolbox(
    private val source: WorkspaceFileSource,
    private val search: WorkspaceSearchEngine,
    private val git: CliGitService,
    /** The runtime the tools run in: also where the assistant's local MCP servers start (docs/assistant-and-mcp.md). */
    val execution: RuntimeExecution,
    private val runs: RunCoordinator,
    val runtimeInstalled: () -> Boolean,
    /** An open tab holds unsaved edits at this location. */
    private val unsavedEdits: (String) -> Boolean,
    /** Files changed outside the editor: the tree, Git and the index refresh. */
    private val changed: () -> Unit,
    /** The phone, for every chat while phone control is on (docs/voice-and-phone-control.md). */
    private val phone: PhoneControl,
    /** The Terminal panel's own sessions, where the assistant's commands run (docs/assistant-and-mcp.md). */
    terminals: TerminalSessions,
    /** What hears the microphone, which the assistant sets up as the user does (docs/voice-and-phone-control.md). */
    private val speech: SpeechProviders,
    /** The language servers, which the assistant sets up and asks as the user does (docs/editor-and-language-services.md). */
    private val languages: LanguageServices,
) {
    private val terminal = TerminalRuns(terminals)
    private val shell = AssistantShell(execution.environment, execution.processes, terminal)
    private val processes = execution.processes

    /** What a deleted chat leaves in the runtime goes: its terminal tabs, in the workspace at [location] or in none. */
    suspend fun forget(location: String?, chat: String) = terminal.close(chatOf(location, chat))

    /** [chat]'s own tab in the workspace at [location], or in none, opened if it has none. Throws [TerminalRuns.Refused]. */
    suspend fun openTab(location: String?, chat: String): String = terminal.tabOf(location, chatOf(location, chat), fresh = false)

    private fun chatOf(location: String?, chat: String) = AssistantShell.chatOf(AssistantWorkspace(location?.let(::File), execution.layout.paths), chat)

    /**
     * The tools of a chat in [workspace], on [step]: the phone's while phone control is on; the Run
     * tab's for a workspace only, none for a chat in no workspace (docs/design-and-navigation.md).
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
            RunCommandTool(workspace, chat, shell, terminal, step, runtimeInstalled, changed),
            ListTerminalsTool(workspace, chat, terminal),
            ReadTerminalTool(workspace, chat, terminal),
            SendTerminalInputTool(workspace, chat, terminal),
            StartProcessTool(workspace, chat, shell, processes, runtimeInstalled),
            ProcessStatusTool(processes, execution.layout.paths),
            SendProcessInputTool(processes, execution.layout.paths),
            StopProcessTool(processes, execution.layout.paths),
        ) + (if (phone.on) PhoneTools(phone, step).all() else emptyList())
        val run = if (workspace.workspace == null) emptyList() else listOf(
            RunAndroidAppTool(workspace, runs),
            ListRunCommandsTool(workspace, runs),
            SaveRunCommandTool(workspace, runs),
            DeleteRunCommandTool(workspace, runs),
        )
        return files + run + speechProviderTools(speech) + languageServiceTools(languages, workspace) + AskUserTool(step)
    }
}
