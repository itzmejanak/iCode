package dev.icode.mobile.data.assistant.acp

import android.content.Context
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** How an agent is fetched and started on iCode's runtime, as its registry entry says (ACP registry `distribution`). */
internal sealed interface AgentDistribution {
    val args: List<String>
    val env: Map<String, String>

    /** An ARM64 Linux archive, checked by its SHA-256: [cmd] in it, relative to where it is unpacked. */
    data class Binary(val archive: String, val sha256: String, val cmd: String, override val args: List<String>, override val env: Map<String, String>) : AgentDistribution

    /** An npm package at one version: [name]@[version], run on Node.js. */
    data class Npm(val name: String, val version: String, override val args: List<String>, override val env: Map<String, String>) : AgentDistribution {
        val spec: String get() = "$name@$version"
    }
}

/** One agent as its registry entry describes it: what the user sees, and how it is fetched and started. */
internal data class AgentEntry(val agent: CodingAgent, val distribution: AgentDistribution)

/**
 * The one parser of the ACP registry's format (`{"version", "agents": [agent.json…]}`; docs/assistant-and-mcp.md).
 * iCode's verified list is pinned in `assets/agents/registry.json` in that format; the live registry, and
 * agents the user adds, are read by this same parser. An agent is offered on iCode's runtime only with a
 * `linux-aarch64` binary or an npm package at an exact version.
 */
internal object AgentRegistry {
    /** The registry's platform for iCode's runtime: Ubuntu, glibc, ARM64. */
    const val PLATFORM = "linux-aarch64"
    const val ASSET = "agents/registry.json"

    private val json = Json { ignoreUnknownKeys = true }

    /** The agents iCode lists, as pinned in its assets, in [CodingAgents]' order. */
    fun pinned(context: Context): List<AgentEntry> = listed(parse(context.assets.open(ASSET).use { it.readBytes().decodeToString() }))

    /** [entries] limited to the agents [CodingAgents] lists, each at the version it pins, in its order. */
    fun listed(entries: List<AgentEntry>): List<AgentEntry> = CodingAgents.listed.mapNotNull { listed ->
        entries.firstOrNull { it.agent.id == listed.id && it.agent.version == listed.version }
    }

    /** Every agent in [text] that can run on iCode's runtime; one iCode cannot run is left out. */
    fun parse(text: String): List<AgentEntry> {
        val root = json.parseToJsonElement(text).jsonObject
        return root["agents"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonObject)?.let(::entry) }
    }

    /** One `agent.json`; null when it has no distribution iCode's runtime can use. */
    fun entry(agent: JsonObject): AgentEntry? {
        val id = agent.string("id") ?: return null
        val version = agent.string("version") ?: return null
        val distribution = distribution(agent["distribution"] as? JsonObject ?: return null) ?: return null
        val listed = CodingAgents.byId(id)
        return AgentEntry(
            CodingAgent(
                id = id,
                name = agent.string("name") ?: id,
                version = version,
                description = agent.string("description").orEmpty(),
                licence = agent.string("license") ?: "Unknown",
                terms = agent.string("license_url"),
                website = agent.string("website") ?: agent.string("repository"),
                tool = listed?.tool ?: (CodingAgents.TOOL_PREFIX + id),
                npm = distribution is AgentDistribution.Npm,
            ),
            distribution,
        )
    }

    /** A binary for [PLATFORM] first — it needs nothing else installed — else an npm package pinned to a version. */
    private fun distribution(distribution: JsonObject): AgentDistribution? {
        (distribution["binary"] as? JsonObject)?.get(PLATFORM)?.let { it as? JsonObject }?.let { binary ->
            val archive = binary.string("archive") ?: return@let
            val sha256 = binary.string("sha256")?.lowercase()?.takeIf(SHA256::matches) ?: return@let
            val cmd = binary.string("cmd") ?: return@let
            return AgentDistribution.Binary(archive, sha256, cmd, binary.strings("args"), binary.map("env"))
        }
        (distribution["npx"] as? JsonObject)?.let { npx ->
            val spec = npx.string("package") ?: return null
            val at = spec.lastIndexOf('@')
            if (at <= 0) return null // Not pinned to a version: what would run could change under the user.
            val version = spec.substring(at + 1).takeIf { it.isNotEmpty() && it.first().isDigit() } ?: return null
            return AgentDistribution.Npm(spec.substring(0, at), version, npx.strings("args"), npx.map("env"))
        }
        return null
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content }

    private fun JsonObject.map(key: String): Map<String, String> =
        (this[key] as? JsonObject).orEmpty().mapNotNull { (name, value: JsonElement) -> (value as? JsonPrimitive)?.let { name to it.jsonPrimitive.content } }.toMap()

    private val SHA256 = Regex("^[0-9a-f]{64}$")
}
