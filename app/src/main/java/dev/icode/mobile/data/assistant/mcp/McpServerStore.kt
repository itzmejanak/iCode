package dev.icode.mobile.data.assistant.mcp

import android.content.Context
import android.content.SharedPreferences
import dev.icode.mobile.data.io.KeystoreCipher
import dev.icode.mobile.data.io.commitOrFail
import dev.icode.mobile.domain.McpRules
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpToolChoice
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The MCP servers the user added, and what iCode keeps for every server (docs/mcp-plan.md §5.4): the
 * preferences `icode.mcp`. A secret value, a sign-in's tokens and nothing else are encrypted, by a
 * Keystore key of their own; none of them is ever in what [servers] returns.
 */
internal class McpServerStore(
    private val preferences: SharedPreferences,
    private val encrypt: (String) -> String,
    /** Null when this device can no longer decrypt it: it reads as missing. */
    private val decrypt: (String) -> String?,
) {
    constructor(context: Context, cipher: KeystoreCipher = KeystoreCipher(KEY_ALIAS)) : this(
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE),
        cipher::encrypt,
        cipher::decrypt,
    )

    private val lock = Any()

    /** The servers the user added, each value's secret left out and said saved or not. */
    fun servers(): List<McpServer> = synchronized(lock) { stored().map(::shown) }

    /**
     * Adds [server] ([McpServer.id] "") or replaces it, and returns it as kept: its ID and key given, a
     * secret value written and the rest kept. A workspace's server keeps only its secrets and choices.
     */
    fun save(server: McpServer): McpServer = synchronized(lock) {
        val all = stored()
        val id = server.id.ifEmpty { "m-" + UUID.randomUUID().toString().take(8) }
        val key = server.key.ifEmpty { McpRules.key(server.name, all.filter { it.id != id }.map { it.key }) }
        val kept = server.copy(id = id, key = key)
        preferences.commitOrFail("Unable to save the MCP server") {
            values(kept).forEach { value ->
                when {
                    !value.secret -> remove(secretKey(id, value.name))
                    value.value != null -> putString(secretKey(id, value.name), encrypt(value.value))
                }
            }
            // A value no longer named, or made plain, keeps no secret behind.
            all.firstOrNull { it.id == id }?.let(::values)?.map { it.name }
                ?.filter { name -> values(kept).none { it.name == name && it.secret } }
                ?.forEach { remove(secretKey(id, it)) }
            if (kept.workspace == null) putString(KEY_SERVERS, encode(all.filter { it.id != id } + kept).toString())
            else putString(choicesKey(id), choices(kept).toString())
        }
        shown(kept)
    }

    fun remove(id: String) = synchronized(lock) {
        val gone = stored().firstOrNull { it.id == id }
        preferences.commitOrFail("Unable to remove the MCP server") {
            putString(KEY_SERVERS, encode(stored().filter { it.id != id }).toString())
            gone?.let(::values)?.forEach { remove(secretKey(id, it.name)) }
            listOf(choicesKey(id), signInKey(id), trustKey(id)).forEach { remove(it) }
        }
    }

    /**
     * A workspace's server as its file declares it, with the user's own choices over it — on or off,
     * each tool's — and its secret values said saved or not (M6).
     */
    fun declared(server: McpServer): McpServer = synchronized(lock) {
        val saved = preferences.getString(choicesKey(server.id), null)?.let(::JSONObject)
        shown(saved?.let { server.copy(enabled = it.optBoolean("enabled", server.enabled), tools = tools(it.optJSONObject("tools"))) } ?: server)
    }

    /** [server]'s values with their secrets, to start it or reach it; a secret this device cannot read is null. */
    fun reveal(server: McpServer): List<McpValue> = synchronized(lock) {
        values(server).map { value ->
            if (!value.secret) value else value.copy(value = preferences.getString(secretKey(server.id, value.name), null)?.let(decrypt))
        }
    }

    /** A sign-in's tokens (M5), as [McpSignIn] keeps them; null when signed out. */
    fun signIn(id: String): String? = synchronized(lock) { preferences.getString(signInKey(id), null)?.let(decrypt) }

    fun saveSignIn(id: String, tokens: String?) = synchronized(lock) {
        preferences.commitOrFail("Unable to keep the sign-in") {
            if (tokens == null) remove(signInKey(id)) else putString(signInKey(id), encrypt(tokens))
        }
    }

    /** The sign-in on its way through the browser (M5): one at a time, kept so a process restarted meanwhile still finishes it. */
    fun pendingSignIn(): String? = synchronized(lock) { preferences.getString(KEY_PENDING, null)?.let(decrypt) }

    fun savePendingSignIn(pending: String?) = synchronized(lock) {
        preferences.commitOrFail("Unable to keep the sign-in") {
            if (pending == null) remove(KEY_PENDING) else putString(KEY_PENDING, encrypt(pending))
        }
    }

    /** The configuration a workspace's server was trusted with, as [McpWorkspaceServers.fingerprint] says it (M6). */
    fun trusted(id: String): String? = synchronized(lock) { preferences.getString(trustKey(id), null) }

    fun trust(id: String, fingerprint: String?) = synchronized(lock) {
        preferences.commitOrFail("Unable to keep the trust") {
            if (fingerprint == null) remove(trustKey(id)) else putString(trustKey(id), fingerprint)
        }
    }

    private fun stored(): List<McpServer> =
        runCatching { JSONArray(preferences.getString(KEY_SERVERS, "[]")) }.getOrElse { JSONArray() }.let { array ->
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::decode) }
        }

    private fun shown(server: McpServer): McpServer {
        fun hide(values: List<McpValue>) = values.map { value ->
            if (!value.secret) value else value.copy(value = null, saved = preferences.contains(secretKey(server.id, value.name)))
        }
        return server.copy(
            transport = when (val transport = server.transport) {
                is McpTransport.Local -> transport.copy(env = hide(transport.env))
                is McpTransport.Remote -> transport.copy(headers = hide(transport.headers))
            },
        )
    }

    private fun values(server: McpServer): List<McpValue> = when (val transport = server.transport) {
        is McpTransport.Local -> transport.env
        is McpTransport.Remote -> transport.headers
    }

    private fun encode(servers: List<McpServer>): JSONArray = JSONArray().apply {
        servers.forEach { server ->
            put(choices(server).put("id", server.id).put("name", server.name).put("key", server.key).put("timeout", server.toolTimeoutSeconds).apply {
                when (val transport = server.transport) {
                    is McpTransport.Local -> put("local", JSONObject().put("command", transport.command).put("args", JSONArray(transport.args))
                        .put("folder", transport.folder).put("env", encodeValues(transport.env)))
                    is McpTransport.Remote -> put("remote", JSONObject().put("url", transport.url).put("clientId", transport.clientId)
                        .put("headers", encodeValues(transport.headers)))
                }
            })
        }
    }

    private fun choices(server: McpServer) = JSONObject().put("enabled", server.enabled)
        .put("tools", JSONObject().apply { server.tools.forEach { (tool, choice) -> put(tool, choice.name) } })

    /** A secret's value is never written here: only that it is one. */
    private fun encodeValues(values: List<McpValue>) = JSONArray().apply {
        values.forEach { put(JSONObject().put("name", it.name).put("secret", it.secret).put("value", if (it.secret) null else it.value).put("hint", it.hint)) }
    }

    private fun decode(body: JSONObject): McpServer? {
        val local = body.optJSONObject("local")
        val remote = body.optJSONObject("remote")
        val transport = when {
            local != null -> McpTransport.Local(
                local.optString("command"),
                local.optJSONArray("args")?.let { args -> (0 until args.length()).map(args::optString) }.orEmpty(),
                local.optString("folder").takeIf { local.has("folder") && !local.isNull("folder") },
                decodeValues(local.optJSONArray("env")),
            )
            remote != null -> McpTransport.Remote(
                remote.optString("url"),
                decodeValues(remote.optJSONArray("headers")),
                remote.optString("clientId").takeIf { remote.has("clientId") && !remote.isNull("clientId") },
            )
            else -> return null
        }
        return McpServer(
            id = body.optString("id"), name = body.optString("name"), key = body.optString("key"), transport = transport,
            enabled = body.optBoolean("enabled", true), tools = tools(body.optJSONObject("tools")),
            toolTimeoutSeconds = body.optInt("timeout", McpRules.TOOL_TIMEOUT_SECONDS),
        )
    }

    private fun decodeValues(array: JSONArray?): List<McpValue> = array?.let {
        (0 until it.length()).mapNotNull(it::optJSONObject).map { value ->
            McpValue(
                name = value.optString("name"),
                value = value.optString("value").takeIf { value.has("value") && !value.isNull("value") },
                secret = value.optBoolean("secret", true),
                hint = value.optString("hint").takeIf { value.has("hint") && !value.isNull("hint") },
            )
        }
    }.orEmpty()

    private fun tools(body: JSONObject?): Map<String, McpToolChoice> = body?.keys()?.asSequence()?.mapNotNull { tool ->
        McpToolChoice.entries.firstOrNull { it.name == body.optString(tool) }?.let { tool to it }
    }?.toMap().orEmpty()

    private fun secretKey(id: String, name: String) = "secret.$id.$name"
    private fun choicesKey(id: String) = "choices.$id"
    private fun signInKey(id: String) = "signin.$id"
    private fun trustKey(id: String) = "trust.$id"

    companion object {
        const val NAME = "icode.mcp"
        const val KEY_ALIAS = "icode.mcp.key"
        private const val KEY_SERVERS = "servers"
        private const val KEY_PENDING = "signin.pending"
    }
}
