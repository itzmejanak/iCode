package dev.icode.mobile.domain

import org.json.JSONArray
import org.json.JSONObject

/** MCP's rules that need no server (docs/assistant-and-mcp.md): pasted configurations, checks, names. */
object McpRules {
    /** A tool call's own time, per server by default. */
    const val TOOL_TIMEOUT_SECONDS = 120
    private val TIMEOUTS = 5..1800

    /** Tools a request offers at most: OpenAI's count, the narrowest supported provider limit. */
    const val MAX_TOOLS = 128

    /** A tool's name for the model: starts with a letter, `A–Z a–z 0–9 _ -`, at most 64. */
    const val NAME_LIMIT = 64
    private const val KEY_LIMIT = 16

    /** What a paste gave: the servers, and the fields iCode does not read, named once. */
    data class Imported(val servers: List<McpServer>, val ignored: List<String>)

    /**
     * The servers in [json]: an `mcpServers` map, a `servers` map, a map of names to servers,
     * or one server, named [single]. Throws [IllegalArgumentException] with words the sheet shows.
     */
    fun import(json: String, single: String = "Server"): Imported {
        val top = runCatching { JSONObject(json.trim()) }.getOrElse { throw IllegalArgumentException("This is not JSON an MCP configuration is written in") }
        val map = top.optJSONObject("mcpServers") ?: top.optJSONObject("servers")
        val named: List<Pair<String, JSONObject>> = when {
            map != null -> map.keys().asSequence().mapNotNull { name -> map.optJSONObject(name)?.let { name to it } }.toList()
            isServer(top) -> listOf(single to top)
            else -> top.keys().asSequence().mapNotNull { name -> top.optJSONObject(name)?.takeIf(::isServer)?.let { name to it } }.toList()
        }
        if (named.isEmpty()) throw IllegalArgumentException("No server in it: each needs a command or a url")
        val ignored = sortedSetOf<String>()
        val servers = named.map { (name, body) ->
            ignored += body.keys().asSequence().filterNot { it in READ }
            server(name, body)
        }
        return Imported(servers, ignored.toList())
    }

    private fun isServer(body: JSONObject) = body.has("command") || URLS.any(body::has)

    private fun server(name: String, body: JSONObject): McpServer {
        val url = URLS.firstNotNullOfOrNull { key -> body.optString(key).takeIf { it.isNotBlank() } }
        val transport = if (url != null) {
            McpTransport.Remote(url.trim(), values(body.optJSONObject("headers")))
        } else {
            McpTransport.Local(
                command = body.optString("command").trim(),
                args = body.optJSONArray("args")?.let(::strings).orEmpty(),
                folder = body.optString("cwd").takeIf { it.isNotBlank() },
                env = values(body.optJSONObject("env")),
            )
        }
        return McpServer(id = "", name = name.trim(), key = "", transport = transport, enabled = !body.optBoolean("disabled", false))
    }

    /** Each value is secret unless marked plain; one that names a variable waits for the user's value. */
    private fun values(body: JSONObject?): List<McpValue> = body?.keys()?.asSequence()?.map { name ->
        val value = body.optString(name)
        if (PLACEHOLDER.containsMatchIn(value)) McpValue(name, null, secret = true, hint = value) else McpValue(name, value, secret = true)
    }?.toList().orEmpty()

    private fun strings(array: JSONArray): List<String> = (0 until array.length()).map { array.opt(it)?.toString().orEmpty() }

    /** What keeps [server] from being saved among [others], in the page's words; null when nothing does. */
    fun problem(server: McpServer, others: List<McpServer>): String? {
        val name = server.name.trim()
        if (name.isEmpty()) return "Name the server"
        if (others.any { it.id != server.id && it.workspace == server.workspace && it.name.equals(name, ignoreCase = true) }) return "Another server is called $name"
        if (server.toolTimeoutSeconds !in TIMEOUTS) return "A tool's time is ${TIMEOUTS.first} to ${TIMEOUTS.last} seconds"
        val values = when (val transport = server.transport) {
            is McpTransport.Local -> {
                if (transport.command.isBlank()) return "Enter the command that starts it"
                transport.env
            }
            is McpTransport.Remote -> {
                remoteProblem(transport.url)?.let { return it }
                transport.headers.firstOrNull { !HEADER_NAME.matches(it.name) }?.let { return "${it.name.ifBlank { "A header" }} is not a header name" }
                transport.headers
            }
        }
        values.firstOrNull { it.name.isBlank() }?.let { return "Name each value" }
        values.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }?.let { return "${it.key} is named twice" }
        values.firstOrNull { it.value == null && !it.saved }?.let { return "Enter a value for ${it.name}" }
        return null
    }

    /** Why [url] cannot be a server's address: https anywhere, http only to this phone (the network policy). */
    fun remoteProblem(url: String): String? {
        val address = url.trim()
        return when {
            address.startsWith("https://", ignoreCase = true) && address.length > "https://".length -> null
            LOCAL.matches(address) -> null
            address.startsWith("http://", ignoreCase = true) -> "Remote servers need https:// (http only to this phone)"
            else -> "Enter an https:// address"
        }
    }

    /** [name] as a unique lowercase tool prefix: `a–z 0–9 _`, a letter first, at most 16 characters. */
    fun key(name: String, taken: Collection<String>): String {
        val cleaned = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').ifEmpty { "server" }
        val base = (if (cleaned.first().isLetter()) cleaned else "s$cleaned").take(KEY_LIMIT).trimEnd('_')
        if (base !in taken) return base
        return generateSequence(2) { it + 1 }.map { n -> "${base.take(KEY_LIMIT - "_$n".length).trimEnd('_')}_$n" }.first { it !in taken }
    }

    /**
     * [tool] of the server keyed [key] as the model calls it: `<key>__<tool>`, other characters
     * `_`; a name too long, or one [taken] already, cut and given its own hash. iCode's own tools never
     * hold `__`, so they never clash.
     */
    fun toolName(key: String, tool: String, taken: Set<String>): String {
        val name = "${key}__${tool.replace(Regex("[^A-Za-z0-9_-]"), "_")}"
        if (name.length <= NAME_LIMIT && name !in taken) return name
        val hash = "%08x".format(tool.hashCode())
        return "${name.take(NAME_LIMIT - hash.length - 1)}_$hash"
    }

    /**
     * [value] with each `${VAR}` and `${VAR:-default}` filled from [variables] in a workspace's `.mcp.json`; null when one names a variable that is not there and gives no default.
     */
    fun expand(value: String, variables: Map<String, String>): String? {
        var missing = false
        val filled = PLACEHOLDER.replace(value) { match ->
            variables[match.groupValues[1]] ?: match.groups[2]?.value ?: run { missing = true; "" }
        }
        return filled.takeUnless { missing }
    }

    /** [server]'s tools that chats are offered: on, and readable. */
    fun toolsOn(server: McpServer, state: McpServerState): Int =
        state.tools.count { it.problem == null && server.choice(it.name) != McpToolChoice.Off }

    /** [server]'s state as its row and its page say it, and the agent's list (docs/assistant-and-mcp.md). */
    fun status(server: McpServer, state: McpServerState): String = when {
        !server.enabled -> "Off"
        else -> when (state.status) {
            McpStatus.Off -> "Starts when a chat needs it"
            McpStatus.Starting -> "Starting…"
            McpStatus.Ready -> toolsOn(server, state).let { if (it == 1) "1 tool" else "$it tools" }
            McpStatus.SignIn -> "Sign in needed"
            McpStatus.Trust -> "Not trusted yet"
            McpStatus.Failed -> "Failed"
        }
    }

    private val PLACEHOLDER = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\}""")
    private val LOCAL = Regex("""(?i)http://(localhost|127\.0\.0\.1)(:\d{1,5})?(/.*)?""")
    private val HEADER_NAME = Regex("""[!#$%&'*+.^_`|~0-9A-Za-z-]+""")
    private val URLS = listOf("url", "serverUrl", "httpUrl")
    private val READ = setOf("command", "args", "env", "cwd", "headers", "disabled", "type") + URLS
}
