package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.tools.textOrNull
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpServerState
import dev.icode.mobile.domain.McpStatus
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import org.json.JSONArray
import org.json.JSONObject

// The agent's hold on the MCP servers (docs/agent-control-plan.md §5): what the MCP pages show and do,
// through McpServers' own operations. A value is given, never read back; a workspace's server stays
// its .mcp.json's, and trusting one stays the user's. Every change is asked about, in every mode.

/** The tools a chat in [workspace] has over [servers]. */
internal fun mcpServerTools(workspace: String?, servers: McpServers): List<AssistantTool> =
    listOf(ListMcpServersTool(workspace, servers), SaveMcpServerTool(workspace, servers), RemoveMcpServerTool(workspace, servers))

/** The servers this chat's tools come from, and how each is: read-only. */
internal class ListMcpServersTool(private val workspace: String?, private val servers: McpServers) : AssistantTool {
    override val name = AssistantToolName.ListMcpServers.id
    override val description = "Lists the MCP servers whose tools chats are offered — the user's own, and this workspace's .mcp.json's: " +
        "each one's id, name, whether it is on and how it is, how it is reached (a command in the Linux runtime, or an " +
        "address), its values (environment variables or headers: whether each is set, never what it is), and its tools " +
        "with the user's choice for each. save_mcp_server and remove_mcp_server change them."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "List the MCP servers"

    override suspend fun run(arguments: JSONObject): String {
        val listed = servers.listed(workspace)
        if (listed.isEmpty()) return "No MCP server is added. save_mcp_server adds one."
        return listed.joinToString("\n\n") { (server, state) -> said(server, state) }
    }

    private fun said(server: McpServer, state: McpServerState): String = buildString {
        append(server.id).append(" · ").append(server.name).append(" · ").append(if (server.enabled) "on" else "off")
        if (server.enabled) append(" · ").append(McpRules.status(server, state))
        state.failure?.takeIf { state.status != McpStatus.Ready }?.let { append(": ").append(it) }
        server.workspace?.let { append("\nfrom this workspace's .mcp.json") }
        val values = when (val transport = server.transport) {
            is McpTransport.Local -> {
                append("\nlocal: ").append((listOf(transport.command) + transport.args).joinToString(" "))
                transport.folder?.let { append(" (in ").append(it).append(")") }
                "env" to transport.env
            }
            is McpTransport.Remote -> {
                append("\nremote: ").append(transport.url)
                "headers" to transport.headers
            }
        }
        if (values.second.isNotEmpty()) {
            append("\n").append(values.first).append(": ")
            append(values.second.joinToString { "${it.name} (${if (it.saved || it.value != null) "set" else "not set"})" })
        }
        if (state.tools.isNotEmpty()) {
            append("\ntools: ").append(state.tools.joinToString { tool -> tool.name + choice(server.choice(tool.name)) })
        } else if (server.tools.isNotEmpty()) {
            append("\ntools, as chosen: ").append(server.tools.entries.joinToString { (tool, choice) -> tool + choice(choice) })
        }
    }

    private fun choice(choice: McpToolChoice): String = if (choice == McpToolChoice.AsksFirst) "" else " (${CHOICES.getValue(choice)})"
}

/** A server added — from fields, or a pasted configuration — or one changed; asked about each time. */
internal class SaveMcpServerTool(private val workspace: String?, private val servers: McpServers) : AssistantTool {
    override val name = AssistantToolName.SaveMcpServer.id
    override val description = "Adds MCP servers or changes one, as the MCP pages do; the user is asked first, every time. To add, give " +
        "config — the JSON a server's README gives ({\"mcpServers\": {…}} or one server's) — or the fields: name, then command " +
        "and args for a program in the Linux runtime, or url for a remote server. To change one, give its id " +
        "(list_mcp_servers) and only what changes. A server from the workspace's .mcp.json keeps the file's name, command " +
        "and address: only enabled, tools and values change there. A value the user gave in the chat is saved and never " +
        "shown again. Its tools are offered from the next turn."
    override val parameters = listOf(
        AssistantParameter("id", "A server's id, to change it; absent to add", required = false),
        AssistantParameter("config", "A configuration to add the servers it names, as a README gives it", required = false),
        AssistantParameter("name", "Its name: short, such as GitHub", required = false),
        AssistantParameter("command", "A local server's program, as typed in a terminal: npx, uvx, docker…", required = false),
        AssistantParameter("args", "The program's arguments, in order", AssistantParameterType.Items(AssistantParameterType.Text), required = false),
        AssistantParameter("url", "A remote server's https:// address", required = false),
        AssistantParameter("env", "A local server's environment values, by name", AssistantParameterType.Fields(emptyList(), more = AssistantParameterType.Text), required = false),
        AssistantParameter("headers", "A remote server's headers, by name", AssistantParameterType.Fields(emptyList(), more = AssistantParameterType.Text), required = false),
        AssistantParameter("unset", "Names of values or headers to remove", AssistantParameterType.Items(AssistantParameterType.Text), required = false),
        AssistantParameter("enabled", "On or off: on by default", AssistantParameterType.Boolean, required = false),
        AssistantParameter(
            "tools", "Each tool's choice, by its name: ${CHOICES.values.joinToString()}",
            AssistantParameterType.Fields(emptyList(), more = AssistantParameterType.Choice(CHOICES.values.toList())), required = false,
        ),
    )
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String {
        val existing = existing(arguments)
        return when {
            existing != null -> "Change the MCP server ${existing.name}"
            arguments.textOrNull("config") != null -> "Add MCP servers from a configuration"
            else -> "Add the MCP server ${arguments.textOrNull("name").orEmpty()}"
        }
    }

    /** What the user is asked about, values named and never shown. */
    override fun detail(arguments: JSONObject): String {
        val config = arguments.textOrNull("config")
        val server = runCatching {
            if (config != null) null else existing(arguments)?.let { changed(it, arguments) } ?: added(arguments)
        }.getOrNull()
        val shown = when {
            config != null -> runCatching { McpRules.import(config).servers }.getOrDefault(emptyList())
            else -> listOfNotNull(server)
        }
        val what = shown.joinToString("\n") { "“${it.name}” · ${where(it)}" + values(it) + (if (it.enabled) "" else " · off") }
        val choices = runCatching { tools(arguments) }.getOrDefault(emptyMap()).entries.joinToString { (tool, choice) -> "$tool ${CHOICES.getValue(choice)}" }
        return listOfNotNull(
            "MCP: ${if (existing(arguments) != null) "change" else "add"}",
            what.ifEmpty { null },
            choices.ifEmpty { null }?.let { "tools: $it" },
        ).joinToString("\n")
    }

    override suspend fun run(arguments: JSONObject): String {
        val config = arguments.textOrNull("config")
        val existing = arguments.textOrNull("id")?.let { id -> existing(arguments) ?: throw AssistantToolException("There is no MCP server $id: list_mcp_servers lists them") }
        if (config != null) {
            if (existing != null) throw AssistantToolException("A configuration adds servers: to change one, give its id and what changes")
            val imported = servers.import(config, arguments.textOrNull("name") ?: "Server").getOrElse { throw AssistantToolException(it.message ?: "Not added") }
            val added = imported.servers.joinToString { "${it.name} (${it.id}${if (it.enabled) "" else ", off"})" }
            val ignored = imported.ignored.takeIf { it.isNotEmpty() }?.let { "\nNot read: ${it.joinToString()}" }.orEmpty()
            return "Added $added. Its tools are offered from the next turn; list_mcp_servers says how each is.$ignored"
        }
        val server = try {
            existing?.let { changed(it, arguments) } ?: added(arguments)
        } catch (refused: IllegalArgumentException) {
            throw AssistantToolException(refused.message ?: "Not saved")
        }
        val kept = servers.save(workspace, server).getOrElse { throw AssistantToolException(it.message ?: "Not saved") }
        val state = if (kept.enabled) "on: list_mcp_servers says how it is, and its tools are offered from the next turn" else "off"
        return "${if (existing == null) "Added" else "Changed"} ${kept.name} (${kept.id}), $state."
    }

    private fun existing(arguments: JSONObject): McpServer? =
        arguments.textOrNull("id")?.let { id -> servers.listed(workspace).firstOrNull { it.first.id == id }?.first }

    /** A new server from the fields. Throws [IllegalArgumentException] with the model's words. */
    private fun added(arguments: JSONObject): McpServer {
        val url = arguments.textOrNull("url")
        val command = arguments.textOrNull("command")
        val transport = when {
            url != null && command != null -> throw IllegalArgumentException("Give command or url, not both")
            url != null -> McpTransport.Remote(url, values(emptyList(), arguments, "headers"))
            command != null -> McpTransport.Local(command, strings(arguments, "args").orEmpty(), env = values(emptyList(), arguments, "env"))
            else -> throw IllegalArgumentException("Give config, or command or url")
        }
        val name = arguments.textOrNull("name") ?: throw IllegalArgumentException("Name the server")
        return McpServer("", name, "", transport, arguments.optBoolean("enabled", true), tools(arguments))
    }

    /** [server] with what the fields change. Throws [IllegalArgumentException] with the model's words. */
    private fun changed(server: McpServer, arguments: JSONObject): McpServer {
        val url = arguments.textOrNull("url")
        val command = arguments.textOrNull("command")
        val args = strings(arguments, "args")
        val renamed = arguments.textOrNull("name")?.takeIf { it != server.name }
        if (server.workspace != null && (renamed != null || url != null || command != null || args != null)) {
            throw IllegalArgumentException("${server.name} comes from this workspace's .mcp.json: its name, command and address are the file's")
        }
        val transport = when (val transport = server.transport) {
            is McpTransport.Local -> if (url != null) throw IllegalArgumentException("${server.name} is a local server: remove it and add a remote one")
            else transport.copy(command = command ?: transport.command, args = args ?: transport.args, env = values(transport.env, arguments, "env"))
            is McpTransport.Remote -> if (command != null) throw IllegalArgumentException("${server.name} is a remote server: remove it and add a local one")
            else transport.copy(url = url ?: transport.url, headers = values(transport.headers, arguments, "headers"))
        }
        return server.copy(
            name = renamed ?: server.name,
            transport = transport,
            enabled = if (arguments.has("enabled")) arguments.optBoolean("enabled") else server.enabled,
            tools = server.tools + tools(arguments),
        )
    }

    /** [kept] with the values [field] gives and without those `unset` names: each given one secret, as the pages' are. */
    private fun values(kept: List<McpValue>, arguments: JSONObject, field: String): List<McpValue> {
        val given = arguments.optJSONObject(field)
        val unset = strings(arguments, "unset").orEmpty().toSet()
        val names = given?.keys()?.asSequence()?.toList().orEmpty()
        return kept.filter { it.name !in unset && it.name !in names } + names.map { McpValue(it, given!!.optString(it), secret = true) }
    }

    private fun tools(arguments: JSONObject): Map<String, McpToolChoice> {
        val given = arguments.optJSONObject("tools") ?: return emptyMap()
        return given.keys().asSequence().associateWith { tool ->
            val word = given.optString(tool)
            CHOICES.entries.firstOrNull { it.value == word }?.key ?: throw AssistantToolException("$word is not a choice: ${CHOICES.values.joinToString()}")
        }
    }

    private fun strings(arguments: JSONObject, field: String): List<String>? =
        (arguments.opt(field) as? JSONArray)?.let { array -> (0 until array.length()).map { array.optString(it) } }

    private fun where(server: McpServer): String = when (val transport = server.transport) {
        is McpTransport.Local -> (listOf(transport.command) + transport.args).joinToString(" ")
        is McpTransport.Remote -> transport.url
    }

    private fun values(server: McpServer): String {
        val values = when (val transport = server.transport) {
            is McpTransport.Local -> transport.env
            is McpTransport.Remote -> transport.headers
        }
        return if (values.isEmpty()) "" else " · values: " + values.joinToString { it.name }
    }
}

/** One of the user's servers forgotten, its values and sign-in with it; asked about each time. */
internal class RemoveMcpServerTool(private val workspace: String?, private val servers: McpServers) : AssistantTool {
    override val name = AssistantToolName.RemoveMcpServer.id
    override val description = "Removes one of the user's MCP servers, by its id (list_mcp_servers): its values, its sign-in and its tools' " +
        "choices are forgotten. The user is asked first, every time. A server from the workspace's .mcp.json is removed from that file."
    override val parameters = listOf(AssistantParameter("id", "The server's id"))
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String = "Remove the MCP server ${named(arguments)}"

    override fun detail(arguments: JSONObject): String = "MCP: remove “${named(arguments)}” · its values, its sign-in and its tools' choices are forgotten"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id") ?: throw AssistantToolException("Name the server: its id, from list_mcp_servers")
        val removed = servers.remove(workspace, id).getOrElse { throw AssistantToolException(it.message ?: "Not removed") }
        return "Removed ${removed.name}."
    }

    private fun named(arguments: JSONObject): String {
        val id = arguments.textOrNull("id").orEmpty()
        return servers.listed(workspace).firstOrNull { it.first.id == id }?.first?.name ?: id
    }
}

/** Each choice as the model writes it. */
private val CHOICES = mapOf(McpToolChoice.Off to "off", McpToolChoice.AsksFirst to "asks_first", McpToolChoice.Runs to "runs")
