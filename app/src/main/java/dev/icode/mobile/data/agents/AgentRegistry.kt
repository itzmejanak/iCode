package dev.icode.mobile.data.agents

import dev.icode.mobile.data.io.readAtMost
import dev.icode.mobile.domain.AgentSource
import dev.icode.mobile.domain.CodingAgent
import dev.icode.mobile.domain.CodingAgents
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

/**
 * The ACP registry (docs/assistant-and-mcp.md): its published list, read in its own format — the one
 * parser of it — and the last one read kept in iCode's storage, which the page shows when the
 * registry cannot be reached.
 */
internal class AgentRegistry(
    private val kept: File,
    private val download: (String) -> String = ::read,
    private val measure: (String) -> Long? = ::length,
) {
    /** The agents iCode offers, as the list last read says them; none before the first. */
    fun kept(): List<CodingAgent> = runCatching { parse(kept.readText()) }.getOrDefault(emptyList())

    /** The agents iCode offers, as the registry says them now; the list is kept. */
    fun read(): List<CodingAgent> {
        val text = download(URL_LIST)
        val agents = parse(text)
        kept.parentFile?.mkdirs()
        val written = File(kept.parentFile, "${kept.name}.tmp").apply { writeText(text) }
        Files.move(written.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return agents
    }

    /** What [agent]'s release downloads, where its server says before: a build's archive. Null when unknown. */
    fun size(agent: CodingAgent): Long? = (agent.source as? AgentSource.Binary)?.let { runCatching { measure(it.archive) }.getOrNull() }

    companion object {
        /** The registry's published list (github.com/agentclientprotocol/registry, README). */
        const val URL_LIST = "https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json"

        /** The runtime's build target: Ubuntu, glibc, ARM64 (FORMAT.md's platform targets). */
        private const val PLATFORM = "linux-aarch64"

        private val SHA256 = Regex("[0-9a-f]{64}")

        /** The offered agents in [text], the registry's `registry.json`, in [CodingAgents.OFFERED]'s order; one missing from it is left out. */
        fun parse(text: String): List<CodingAgent> {
            val agents = JSONObject(text).getJSONArray("agents")
            val byId = (0 until agents.length()).map(agents::getJSONObject).associateBy { it.getString("id") }
            return CodingAgents.OFFERED.mapNotNull { id -> byId[id]?.let(::agent) }
        }

        private fun agent(json: JSONObject): CodingAgent {
            val distribution = json.getJSONObject("distribution")
            return CodingAgent(
                id = json.getString("id"),
                name = json.getString("name"),
                version = json.getString("version"),
                description = json.getString("description"),
                website = json.text("website") ?: json.text("repository"),
                license = json.text("license") ?: "",
                licenseUrl = json.getString("license_url"),
                // A verified build first: it needs no Node.js.
                source = distribution.optJSONObject("binary")?.optJSONObject(PLATFORM)?.let(::binary) ?: distribution.optJSONObject("npx")?.let(::npm),
            )
        }

        /** A build only with its digest: what is not verified is never installed. */
        private fun binary(target: JSONObject): AgentSource.Binary? {
            val sha256 = target.optString("sha256").lowercase().takeIf(SHA256::matches) ?: return null
            return AgentSource.Binary(target.getString("archive"), sha256, target.getString("cmd"), target.strings("args"), target.variables())
        }

        /** A package only at an exact version: `@scope/name@1.2.3`. */
        private fun npm(spec: JSONObject): AgentSource.Npm? {
            val pinned = spec.getString("package")
            val at = pinned.lastIndexOf('@').takeIf { it > 0 } ?: return null
            return AgentSource.Npm(pinned.substring(0, at), pinned.substring(at + 1), spec.strings("args"), spec.variables())
        }

        private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).takeIf(String::isNotBlank)

        private fun JSONObject.strings(key: String): List<String> = optJSONArray(key)?.let { array -> List(array.length(), array::getString) }.orEmpty()

        private fun JSONObject.variables(): Map<String, String> = optJSONObject("env")?.let { env -> env.keys().asSequence().associateWith(env::getString) }.orEmpty()

        private fun read(url: String): String = connect(url, "GET") { connection ->
            connection.inputStream.use { it.readAtMost(MAX_LIST, "The registry's list is larger than iCode reads").toString(Charsets.UTF_8) }
        }

        private fun length(url: String): Long? = connect(url, "HEAD") { it.contentLengthLong.takeIf { length -> length > 0 } }

        /** Blocking: called on the IO dispatcher, as every download is. */
        private fun <T> connect(url: String, method: String, use: (HttpURLConnection) -> T): T {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("The server answered HTTP ${connection.responseCode}")
                return use(connection)
            } finally {
                connection.disconnect()
            }
        }

        private const val TIMEOUT = 30_000
        private const val MAX_LIST = 8 * 1024 * 1024
    }
}
