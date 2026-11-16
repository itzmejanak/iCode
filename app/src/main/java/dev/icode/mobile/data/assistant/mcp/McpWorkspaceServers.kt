package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The servers a workspace's `.mcp.json` declares (docs/assistant-and-mcp.md), read as a paste is
 * ([McpRules.import]). One starts only once the user trusts it, and trust is kept with a hash of its
 * configuration: a changed one asks again. `${VAR}` is filled from the user's login environment, a
 * value the user kept in iCode coming first.
 */
internal class McpWorkspaceServers(
    private val store: McpServerStore,
    /** A workspace's location as its path in the runtime: a local server's folder unless it names one. */
    private val guestOf: (String) -> String?,
    /** The user's login environment; empty when the runtime cannot read it. */
    private val variables: suspend () -> Map<String, String>,
) {
    /** [location]'s servers, each keyed apart from [taken], with the user's choices over them. */
    fun of(location: String, taken: List<String>): List<McpServer> {
        val keys = taken.toMutableList()
        return declared(location).map { found ->
            val key = McpRules.key(found.name, keys).also { keys += it }
            store.declared(found.copy(key = key))
        }
    }

    /**
     * [server] as it starts — its command, arguments, folder or address filled — and its values: the
     * user's kept one, else the file's, else the file's `${VAR}` filled; null when none is there.
     * Throws [IllegalArgumentException] when its command or address names a variable not set.
     */
    suspend fun resolve(server: McpServer, revealed: List<McpValue>): Pair<McpServer, List<McpValue>> {
        val location = server.workspace ?: return server to revealed
        val declared = declared(location).firstOrNull { it.id == server.id } ?: return server to revealed
        val variables = variables()
        fun fill(text: String) = McpRules.expand(text, variables)
            ?: throw IllegalArgumentException("${server.name} names a variable your login environment does not set")
        val values = values(declared).map { value ->
            val kept = revealed.firstOrNull { it.name == value.name }?.value
            value.copy(value = kept ?: value.value ?: value.hint?.let { McpRules.expand(it, variables) })
        }
        val transport = when (val shown = server.transport) {
            is McpTransport.Local -> shown.copy(command = fill(shown.command), args = shown.args.map(::fill), folder = shown.folder?.let(::fill) ?: guestOf(location))
            is McpTransport.Remote -> shown.copy(url = fill(shown.url))
        }
        return server.copy(transport = transport) to values
    }

    /** [server] is trusted as its file declares it now. */
    fun trusted(server: McpServer): Boolean = server.workspace == null || store.trusted(server.id) == fingerprint(server)

    /** [server]'s configuration as its file declares it, hashed: what trust is kept with. */
    fun fingerprint(server: McpServer): String? {
        val declared = server.workspace?.let(::declared)?.firstOrNull { it.id == server.id } ?: return null
        val body = when (val transport = declared.transport) {
            is McpTransport.Local -> JSONObject().put("command", transport.command).put("args", JSONArray(transport.args))
                .put("cwd", transport.folder).put("env", encode(transport.env))
            is McpTransport.Remote -> JSONObject().put("url", transport.url).put("headers", encode(transport.headers))
        }
        return Sha256.hex(body.toString())
    }

    /** The file's servers, named for [location]; none when it is missing, too large, or not a configuration. */
    private fun declared(location: String): List<McpServer> {
        val file = File(location, FILE)
        if (!file.isFile || file.length() > MAX_BYTES) return emptyList()
        val imported = runCatching { McpRules.import(file.readText()) }.getOrNull() ?: return emptyList()
        return imported.servers.map { it.copy(id = "$location#${it.name}", workspace = location) }
    }

    private fun values(server: McpServer): List<McpValue> = when (val transport = server.transport) {
        is McpTransport.Local -> transport.env
        is McpTransport.Remote -> transport.headers
    }

    /** Each value as the file writes it: a changed token asks again, as a changed command does. */
    private fun encode(values: List<McpValue>) = JSONObject().apply { values.forEach { put(it.name, it.value ?: it.hint) } }

    private companion object {
        const val FILE = ".mcp.json"
        const val MAX_BYTES = 256 * 1024L
    }
}
