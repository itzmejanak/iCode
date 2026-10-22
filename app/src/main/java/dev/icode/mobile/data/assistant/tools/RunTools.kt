package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.run.RunCoordinator
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.RunConfiguration
import org.json.JSONObject

// The Run tab for the assistant (docs/run-tab-plan.md §2.5): Build & Run, and the commands saved
// there — listed, added, changed and deleted through the RunCoordinator the tab's +, Edit and
// Delete use, so the open tab shows each at once.

/** Build & Run, as the Run tab's ▶ starts it (docs/run-tab-plan.md); asked about first. */
internal class RunAndroidAppTool(private val workspace: AssistantWorkspace, private val runs: RunCoordinator) : AssistantTool {
    override val name = "run_android_app"
    override val description = "Builds this workspace's Android app with the project's own Gradle wrapper " +
        "(`./gradlew :app:assembleDebug`) as a process, and when the build succeeds installs the APK on this phone and opens " +
        "it; Android asks the user to confirm the install. Needs the Android SDK, installed from Runtime Tools. Answers the " +
        "build's process ID at once: follow it with process_status. The user sees it on the Run tab, and its errors in Problems."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String = "Build & Run the Android app"

    override suspend fun run(arguments: JSONObject): String {
        val process = runs.startAndroidApp(workspace.root.path).getOrElse { throw AssistantToolException(it.message ?: "Build & Run did not start") }
        return "Started ${process.id} · ${process.name}: `${process.command}` in ${process.directory}. " +
            "When it ends with exit code 0 the app is installed and opened."
    }
}

/** What the Run tab lists: read, so never asked about. */
internal class ListRunCommandsTool(private val workspace: AssistantWorkspace, private val runs: RunCoordinator) : AssistantTool {
    override val name = "list_run_commands"
    override val description = "Lists this workspace's Run tab, where the user starts its apps and commands with ▶: each " +
        "Android app iCode finds in the project (Build & Run; found, so never changed or deleted), then each saved command " +
        "with its id, name and command line."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "List the Run tab"

    override suspend fun run(arguments: JSONObject): String {
        val configurations = runs.configurationsOf(workspace.root.path)
        if (configurations.isEmpty()) return "The Run tab is empty: no Android app found, no command saved."
        return configurations.joinToString("\n") { configuration ->
            when (configuration) {
                is RunConfiguration.AndroidApp -> "- ${configuration.name} · Android app, found in the project: `${configuration.command}`"
                is RunConfiguration.Command -> "- ${configuration.name} · id ${configuration.id}: `${configuration.command}`"
            }
        }
    }
}

/** A command saved on the Run tab, new or changed; asked about first, as a change. */
internal class SaveRunCommandTool(private val workspace: AssistantWorkspace, private val runs: RunCoordinator) : AssistantTool {
    override val name = "save_run_command"
    override val description = "Saves a command on this workspace's Run tab, for the user to start with ▶ whenever they want: " +
        "it runs in bash at the workspace's top with the user's login environment, as a process whose output is its own " +
        "Terminal tab. Without id it adds one; with a saved command's id (list_run_commands) it changes that one, keeping " +
        "its name or command line where not given. Saving does not run it."
    override val parameters = listOf(
        AssistantParameter("command", "The command line, as typed in a terminal; required for a new command", required = false),
        AssistantParameter("name", "Its name on the Run tab: short, such as dev server; the command's first word when absent", required = false),
        AssistantParameter("id", "A saved command's id, to change that one; absent to add one", required = false),
    )
    override val kind = AssistantToolKind.Change

    override fun summary(arguments: JSONObject): String {
        val id = arguments.textOrNull("id")
        val name = arguments.textOrNull("name") ?: id?.let { runCatching { runs.savedCommand(workspace, it).name }.getOrNull() }
            ?: arguments.textOrNull("command")?.lineSequence()?.first().orEmpty()
        return "${if (id == null) "Add" else "Change"} the Run command $name"
    }

    override fun detail(arguments: JSONObject): String {
        val existing = arguments.textOrNull("id")?.let { runCatching { runs.savedCommand(workspace, it) }.getOrNull() }
        val name = arguments.textOrNull("name") ?: existing?.name
        val command = arguments.textOrNull("command") ?: existing?.command.orEmpty()
        return when {
            existing == null -> "Run tab: add${name?.let { " “$it”" }.orEmpty()} · $command"
            name != existing.name -> "Run tab: change “${existing.name}” to “$name” · $command"
            else -> "Run tab: change “${existing.name}” · $command"
        }
    }

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id")
        val existing = id?.let { runs.savedCommand(workspace, it) }
        val command = arguments.textOrNull("command") ?: existing?.command ?: throw AssistantToolException("Missing the “command” argument")
        val name = arguments.textOrNull("name") ?: existing?.name.orEmpty()
        val saved = runs.saveCommand(workspace.root.path, id, name, command)
        return "${if (existing == null) "Added" else "Changed"} “${saved.name}” on the Run tab, id ${saved.id}: `${saved.command}`. " +
            "It runs when the user taps ▶."
    }

}

/** A command saved on the Run tab, deleted; asked about first, as a change. */
internal class DeleteRunCommandTool(private val workspace: AssistantWorkspace, private val runs: RunCoordinator) : AssistantTool {
    override val name = "delete_run_command"
    override val description = "Deletes a saved command from this workspace's Run tab, by its id (list_run_commands). " +
        "A run of it still going keeps running, in its Terminal tab."
    override val parameters = listOf(AssistantParameter("id", "The saved command's id"))
    override val kind = AssistantToolKind.Change

    override fun summary(arguments: JSONObject): String {
        val id = arguments.textOrNull("id").orEmpty()
        return "Delete the Run command ${runCatching { runs.savedCommand(workspace, id).name }.getOrDefault(id)}"
    }

    override fun detail(arguments: JSONObject): String {
        val id = arguments.textOrNull("id").orEmpty()
        val command = runCatching { runs.savedCommand(workspace, id) }.getOrNull() ?: return "Run tab: delete $id"
        return "Run tab: delete “${command.name}” · ${command.command}"
    }

    override suspend fun run(arguments: JSONObject): String {
        val command = runs.savedCommand(workspace, arguments.text("id"))
        runs.deleteCommand(workspace.root.path, command.id)
        return "Deleted “${command.name}” from the Run tab."
    }
}

/** The command saved on [workspace]'s Run tab by [id]; the model is told when there is none, or it is an app found there. */
private fun RunCoordinator.savedCommand(workspace: AssistantWorkspace, id: String): RunConfiguration.Command =
    when (val found = configurationsOf(workspace.root.path).firstOrNull { it.id == id }) {
        is RunConfiguration.Command -> found
        is RunConfiguration.AndroidApp -> throw AssistantToolException("${found.name} is an Android app found in the project: it is not saved, so it is not changed or deleted")
        null -> throw AssistantToolException("No command on this workspace's Run tab has the id $id: list_run_commands lists them")
    }
