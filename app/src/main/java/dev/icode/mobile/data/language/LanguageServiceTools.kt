package dev.icode.mobile.data.language

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.tools.AssistantWorkspace
import dev.icode.mobile.data.assistant.tools.textOrNull
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.LanguageServiceConfiguration
import dev.icode.mobile.domain.LanguageServicePresets
import org.json.JSONObject

// The agent's hold on the language services (docs/lsp-plan.md §6): what Settings › Languages shows and
// does, through LanguageServices' own operations. A configuration outlasts the chat and its command runs
// in the runtime later, so every change is asked about, in every mode, as MCP servers and speech
// providers are; starting and stopping a server is a command, asked as the chat's mode says.

/** The tools a chat in [workspace] has over [services]. */
internal fun languageServiceTools(services: LanguageServices, workspace: AssistantWorkspace): List<AssistantTool> = listOf(
    ListLanguageServicesTool(services),
    SaveLanguageServiceTool(services),
    RemoveLanguageServiceTool(services),
    ControlLanguageServiceTool(services),
    // Its servers run for the workspace open in the editor: a chat in no workspace has no code of theirs to ask about.
) + if (workspace.workspace == null) emptyList() else languageCodeTools(services, workspace)

internal class ListLanguageServicesTool(private val services: LanguageServices) : AssistantTool {
    override val name = AssistantToolName.ListLanguageServices.id
    override val description = "Lists the language servers configured in iCode (Settings › Languages): each one's id, name, command, the " +
        "languages and file patterns it serves, root markers, its state now (Stopped, Starting, Running, Failed) and why, what it said it " +
        "can do, and its log's last lines. Then the known servers save_language_service can add by preset, with how each installs. " +
        "A server runs for the workspace open in the editor and starts when a file it serves opens."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "List the language servers"

    override suspend fun run(arguments: JSONObject): String {
        val rows = services.rows()
        val configured = if (rows.isEmpty()) "No language servers are configured." else rows.joinToString("\n\n") { row ->
            buildString {
                append(row.id).append(" · ").append(row.name).append(" · ").append(row.status.name)
                row.statusMessage?.let { append(" (").append(it).append(')') }
                append("\ncommand: ").append(row.command)
                append("\nserves: ").append(row.servesLabel)
                if (row.rootMarkers.isNotBlank()) append("\nroot markers: ").append(row.rootMarkers)
                if (row.capabilities.isNotEmpty()) append("\noffers: ").append(row.capabilities.joinToString(", "))
                services.log(row.id).takeLast(LOG_LINES).takeIf { it.isNotEmpty() }?.let { append("\nlog:\n").append(it.joinToString("\n")) }
            }
        }
        val presets = LanguageServicePresets.all.joinToString("\n") { preset ->
            "${preset.id}: ${preset.configuration.name} — ${preset.configuration.command}; install: ${preset.install}" + (preset.needs?.let { " (needs $it)" } ?: "")
        }
        return "$configured\n\nPresets:\n$presets"
    }

    private companion object {
        const val LOG_LINES = 12
    }
}

internal class SaveLanguageServiceTool(private val services: LanguageServices) : AssistantTool {
    override val name = AssistantToolName.SaveLanguageService.id
    override val description = "Adds a language server or changes one, as Settings › Languages does; the user is asked first, every time. " +
        "To add one, give preset (list_language_services lists them) or name and command; to change one, its id and only what changes. " +
        "Install the server first with run_command (a preset's install command). A running server starts again with the change. " +
        "languages are LSP language ids (python, typescriptreact, cpp, shellscript); patterns file-name globs (*.py, *.{ts,tsx}); " +
        "environment NAME=value lines; initialization_options and settings JSON objects."
    override val parameters = listOf(
        AssistantParameter("id", "A configured server's id, to change it; absent to add one", required = false),
        AssistantParameter("preset", "A known server to add", AssistantParameterType.Choice(LanguageServicePresets.all.map { it.id }), required = false),
        AssistantParameter("name", "Its name", required = false),
        AssistantParameter("command", "The program and its arguments, as words (no shell)", required = false),
        AssistantParameter("languages", "LSP language ids it serves, separated by spaces", required = false),
        AssistantParameter("patterns", "File-name patterns it serves, separated by spaces", required = false),
        AssistantParameter("root_markers", "Files that mark a project's top, separated by spaces", required = false),
        AssistantParameter("environment", "Variables, one NAME=value a line", required = false),
        AssistantParameter("initialization_options", "The JSON object sent as initializationOptions", required = false),
        AssistantParameter("settings", "The JSON object workspace/configuration is answered from", required = false),
    )
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String =
        existing(arguments)?.let { "Change the language server ${it.name}" } ?: "Add the language server ${draft(arguments)?.name ?: "…"}"

    override fun detail(arguments: JSONObject): String {
        val config = runCatching { existing(arguments)?.let { changed(it, arguments) } ?: draft(arguments) }.getOrNull()
        return listOfNotNull(
            "Language server: ${if (existing(arguments) != null) "change" else "add"}",
            config?.let { "“${it.name}” runs: ${it.command}" },
            config?.let { listOf(it.languages, it.patterns).filter(String::isNotBlank).joinToString(" ").ifEmpty { null }?.let { serves -> "serves: $serves" } },
        ).joinToString("\n")
    }

    override suspend fun run(arguments: JSONObject): String {
        val existing = arguments.textOrNull("id")?.let { id -> existing(arguments) ?: throw AssistantToolException("There is no language server $id: list_language_services lists them") }
        val config = existing?.let { changed(it, arguments) } ?: draft(arguments) ?: throw AssistantToolException("Give preset, or name and command, to add one")
        val saved = services.save(config).getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return "Saved ${saved.name} (${saved.id}). It starts when a file it serves opens in the editor, or with control_language_service."
    }

    private fun existing(arguments: JSONObject): LanguageServiceConfiguration? = arguments.textOrNull("id")?.let(services::configuration)

    private fun draft(arguments: JSONObject): LanguageServiceConfiguration? {
        val base = arguments.textOrNull("preset")?.let { id ->
            LanguageServicePresets.of(id)?.configuration ?: throw AssistantToolException("$id is not a preset: ${LanguageServicePresets.all.joinToString { it.id }}")
        } ?: arguments.textOrNull("command")?.let { LanguageServiceConfiguration("", arguments.textOrNull("name") ?: it.substringBefore(' '), it) } ?: return null
        return changed(base, arguments)
    }

    private fun changed(config: LanguageServiceConfiguration, arguments: JSONObject) = config.copy(
        name = arguments.textOrNull("name") ?: config.name,
        command = arguments.textOrNull("command") ?: config.command,
        languages = arguments.textOrNull("languages") ?: config.languages,
        patterns = arguments.textOrNull("patterns") ?: config.patterns,
        rootMarkers = arguments.textOrNull("root_markers") ?: config.rootMarkers,
        environment = arguments.textOrNull("environment") ?: config.environment,
        initializationOptions = arguments.textOrNull("initialization_options") ?: config.initializationOptions,
        settings = arguments.textOrNull("settings") ?: config.settings,
    )
}

internal class RemoveLanguageServiceTool(private val services: LanguageServices) : AssistantTool {
    override val name = AssistantToolName.RemoveLanguageService.id
    override val description = "Removes a configured language server, by its id (list_language_services), and stops it. It does not " +
        "uninstall the program. The user is asked first, every time."
    override val parameters = listOf(AssistantParameter("id", "The server's id"))
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String = "Remove the language server ${named(arguments)}"

    override fun detail(arguments: JSONObject): String = "Language server: remove “${named(arguments)}” · its server stops"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id") ?: throw AssistantToolException("Name the server: its id, from list_language_services")
        val removed = services.remove(id).getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        return "Removed ${removed.name}."
    }

    private fun named(arguments: JSONObject): String = arguments.textOrNull("id")?.let { services.configuration(it)?.name ?: it }.orEmpty()
}

internal class ControlLanguageServiceTool(private val services: LanguageServices) : AssistantTool {
    override val name = AssistantToolName.ControlLanguageService.id
    override val description = "Starts, stops or restarts a configured language server (by id) in the workspace open in the editor, as " +
        "its page's buttons do. Starting one held after failing tries again. list_language_services shows its state and log after."
    override val parameters = listOf(
        AssistantParameter("id", "The server's id"),
        AssistantParameter("action", "What to do", AssistantParameterType.Choice(listOf("start", "stop", "restart"))),
    )
    override val kind = AssistantToolKind.Command

    override fun summary(arguments: JSONObject): String =
        "${arguments.textOrNull("action")?.replaceFirstChar { it.uppercase() } ?: "Control"} the language server ${arguments.textOrNull("id")?.let { services.configuration(it)?.name ?: it }.orEmpty()}"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id") ?: throw AssistantToolException("Name the server: its id, from list_language_services")
        val result = when (val action = arguments.textOrNull("action")) {
            "start" -> services.start(id)
            "stop" -> services.stop(id)
            "restart" -> services.restart(id)
            else -> throw AssistantToolException("action is start, stop or restart, not $action")
        }
        result.getOrElse { throw AssistantToolException(LanguageFailure.wordsOf(it)) }
        val ready = if (arguments.textOrNull("action") == "stop") null else services.pool.awaitReady(id, START_WAIT_MILLIS)
        val row = services.rows().firstOrNull { it.id == id }
        return when {
            arguments.textOrNull("action") == "stop" -> "Stopped ${row?.name ?: id}."
            ready != null -> "${row?.name ?: id} is running" + (row?.capabilities?.takeIf { it.isNotEmpty() }?.let { "; it offers ${it.joinToString(", ")}" } ?: "") + "."
            else -> "${row?.name ?: id}: ${row?.status?.name ?: "not running"}" + (row?.statusMessage?.let { " — $it" } ?: "") + "."
        }
    }

    private companion object {
        const val START_WAIT_MILLIS = 30_000L
    }
}
