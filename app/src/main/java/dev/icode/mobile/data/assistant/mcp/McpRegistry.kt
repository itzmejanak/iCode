package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.domain.McpRegistryEntry
import dev.icode.mobile.domain.McpRegistrySearch
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import dev.icode.mobile.domain.McpValue
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * The MCP Registry (docs/assistant-and-mcp.md): its v0.1 API, frozen, searched by name for the latest
 * version of each server. A server found is shown as it would be added — a remote address first, as a
 * phone runs nothing for it, else an npm package by `npx` or a PyPI one by `uvx` — for the user to
 * check and fill before saving. One that needs Docker, .NET or a download is not offered.
 */
internal class McpRegistry(private val http: HttpClient, private val address: String = ADDRESS) {
    fun searching(query: String) = McpRegistrySearch(query.trim())

    suspend fun search(query: String): McpRegistrySearch {
        val asked = query.trim()
        if (asked.length < MIN_QUERY) return McpRegistrySearch(asked, loading = false, failure = "Type at least $MIN_QUERY letters")
        return try {
            val response = http.get("$address/v0.1/servers") {
                accept(ContentType.Application.Json)
                parameter("search", asked)
                parameter("version", "latest")
                parameter("limit", LIMIT)
            }
            if (!response.status.isSuccess()) McpRegistrySearch(asked, loading = false, failure = "The MCP Registry answered HTTP ${response.status.value}")
            else McpRegistrySearch(asked, loading = false, entries = entries(JSONObject(response.bodyAsText())))
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failure: Exception) {
            McpRegistrySearch(asked, loading = false, failure = McpFailures.of(failure, "The MCP Registry", address))
        }
    }

    internal companion object {
        const val ADDRESS = "https://registry.modelcontextprotocol.io"
        private const val MIN_QUERY = 3
        private const val LIMIT = 30

        /** A listing's servers that are active and that iCode can run. */
        fun entries(listing: JSONObject): List<McpRegistryEntry> = objects(listing.optJSONArray("servers")).mapNotNull { record ->
            val status = record.optJSONObject("_meta")?.optJSONObject("io.modelcontextprotocol.registry/official")?.optString("status")
            record.optJSONObject("server")?.takeIf { status.isNullOrEmpty() || status == "active" }?.let(::entry)
        }

        /** One `server.json` as iCode would add it; null when it offers nothing iCode runs. */
        fun entry(detail: JSONObject): McpRegistryEntry? {
            val name = detail.optString("name").takeIf(String::isNotBlank) ?: return null
            val title = detail.optString("title").takeIf(String::isNotBlank)
            val transport = objects(detail.optJSONArray("remotes")).firstNotNullOfOrNull(::remote)
                ?: objects(detail.optJSONArray("packages")).firstNotNullOfOrNull(::local)
                ?: return null
            val server = McpServer(id = "", name = title ?: name.substringAfterLast('/'), key = "", transport = transport)
            return McpRegistryEntry(name, title, detail.optString("description"), detail.optString("version"), server)
        }

        private fun remote(remote: JSONObject): McpTransport? {
            if (remote.optString("type") !in REMOTE_TYPES) return null
            val url = filled(remote.optString("url"), remote.optJSONObject("variables")).takeIf(String::isNotBlank) ?: return null
            return McpTransport.Remote(url, values(remote.optJSONArray("headers")))
        }

        /** npm by `npx -y`, PyPI by `uvx`, over stdio: what the Linux runtime has. */
        private fun local(found: JSONObject): McpTransport? {
            if (found.optJSONObject("transport")?.optString("type") != "stdio") return null
            val identifier = found.optString("identifier").takeIf(String::isNotBlank) ?: return null
            val version = found.optString("version").takeIf(String::isNotBlank)
            val runtime = arguments(found.optJSONArray("runtimeArguments"))
            val (command, start, named) = when (found.optString("registryType")) {
                "npm" -> Triple("npx", runtime.ifEmpty { listOf("-y") }, version?.let { "$identifier@$it" } ?: identifier)
                "pypi" -> Triple("uvx", runtime, version?.let { "$identifier==$it" } ?: identifier)
                else -> return null
            }
            return McpTransport.Local(command, start + named + arguments(found.optJSONArray("packageArguments")), env = values(found.optJSONArray("environmentVariables")))
        }

        /**
         * Arguments as the command line takes them: a positional one's value, a named one as
         * `--flag=value`. One the user must give stands as `<hint>` for them to replace; an optional one
         * with no value is left out.
         */
        private fun arguments(array: JSONArray?): List<String> = objects(array).mapNotNull { argument ->
            val value = (argument.optString("value").takeIf(String::isNotEmpty) ?: argument.optString("default").takeIf(String::isNotEmpty))
                ?.let { filled(it, argument.optJSONObject("variables")) }
                ?: if (argument.optBoolean("isRequired")) "<${argument.optString("valueHint").ifBlank { argument.optString("name").trimStart('-') }}>" else null
            when (argument.optString("type")) {
                "named" -> argument.optString("name").takeIf(String::isNotBlank)?.let { flag -> value?.let { "$flag=$it" } }
                else -> value
            }
        }

        /** Environment variables or headers: a value given or defaulted; one the user must give, empty, its form or description its hint. */
        private fun values(array: JSONArray?): List<McpValue> = objects(array).mapNotNull { input ->
            val name = input.optString("name").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val secret = input.optBoolean("isSecret")
            val written = input.optString("value").takeIf(String::isNotEmpty)
            val value = (written ?: input.optString("default").takeIf(String::isNotEmpty))?.let { filled(it, input.optJSONObject("variables")) }
                ?.takeUnless { TEMPLATE.containsMatchIn(it) }
            when {
                value != null -> McpValue(name, value, secret = secret)
                input.optBoolean("isRequired") || written != null -> McpValue(name, null, secret = secret, hint = written ?: input.optString("description").takeIf(String::isNotBlank))
                else -> null
            }
        }

        /** [text] with each `{variable}` its value or default; one with neither stays for the user. */
        private fun filled(text: String, variables: JSONObject?): String = TEMPLATE.replace(text) { match ->
            variables?.optJSONObject(match.groupValues[1])?.let { it.optString("value").ifEmpty { it.optString("default") } }?.takeIf(String::isNotEmpty) ?: match.value
        }

        private fun objects(array: JSONArray?): List<JSONObject> = array?.let { (0 until it.length()).mapNotNull(it::optJSONObject) }.orEmpty()

        private val REMOTE_TYPES = setOf("streamable-http", "sse")
        private val TEMPLATE = Regex("""\{([A-Za-z_][A-Za-z0-9_]*)\}""")
    }
}
