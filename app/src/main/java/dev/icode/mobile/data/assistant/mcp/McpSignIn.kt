package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.BuildConfig
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.http.parseQueryString
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import java.net.URI
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A remote server's sign-in (docs/mcp-plan.md §8.2), as MCP's authorization asks: the server's
 * protected-resource metadata, its authorization server's, iCode registered in the spec's order, PKCE
 * S256, `state` and `iss` checked on the way back, the tokens kept encrypted with their issuer and
 * renewed when they expire. Nothing opens a browser but the user's Sign in.
 */
internal class McpSignIn(
    private val store: McpServerStore,
    private val http: HttpClient,
    /** Where the browser comes back to: iCode's own scheme, its application ID (RFC 8252 §7.1). */
    private val redirect: String,
    /** icode-web's address, whose client metadata document names iCode where a server takes one; empty: none. */
    private val website: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Why a sign-in could not start or finish, in the page's words. */
    class Refused(message: String) : Exception(message)

    private val lock = Mutex()
    private val random = SecureRandom()

    /** [server]'s authorization page, found and iCode registered with it. Throws [Refused]. */
    suspend fun begin(server: McpServer): String {
        val remote = server.transport as? McpTransport.Remote ?: throw Refused("Only a remote server signs in")
        val challenge = challenge(remote.url)
        val protected = protectedResource(remote.url, challenge["resource_metadata"])
        val resource = protected?.optString("resource")?.takeIf(String::isNotBlank)?.let(::canonical) ?: canonical(remote.url)
        val address = canonical(remote.url)
        if (address != resource && !address.startsWith("$resource/")) throw Refused("Its sign-in is for another address: $resource")
        val issuer = protected?.optJSONArray("authorization_servers")?.optString(0)?.takeIf(String::isNotBlank) ?: origin(remote.url)
        val metadata = authorizationServer(issuer)
        if ("S256" !in strings(metadata.optJSONArray("code_challenge_methods_supported"))) throw Refused("Its sign-in does not take PKCE, which MCP requires")
        val (client, secret) = register(remote.clientId, metadata)
        val verifier = random(VERIFIER_BYTES)
        val state = random(STATE_BYTES)
        val scope = challenge["scope"] ?: strings(protected?.optJSONArray("scopes_supported")).joinToString(" ").takeIf(String::isNotBlank)
        store.savePendingSignIn(
            JSONObject().put(SERVER, server.id).put(ADDRESS, remote.url).put(STATE, state).put(VERIFIER, verifier).put(ISSUER, issuer)
                .put(ISS_REQUIRED, metadata.optBoolean("authorization_response_iss_parameter_supported"))
                .put(TOKEN, metadata.getString("token_endpoint")).put(CLIENT, client).put(SECRET, secret).put(RESOURCE, resource).toString(),
        )
        return URLBuilder(metadata.getString("authorization_endpoint")).apply {
            parameters.append("response_type", "code")
            parameters.append("client_id", client)
            parameters.append("redirect_uri", redirect)
            parameters.append("code_challenge", encode(Sha256.digest(verifier.toByteArray(Charsets.US_ASCII))))
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
            parameters.append("resource", resource)
            scope?.let { parameters.append("scope", it) }
        }.buildString()
    }

    /** The browser's answer [uri]: its code exchanged, the tokens kept. The server's ID. Throws [Refused]. */
    suspend fun finish(uri: String): String {
        val answer = parseQueryString(URI(uri).rawQuery.orEmpty())
        val pending = store.pendingSignIn()?.let(::JSONObject)?.takeIf { it.optString(STATE) == answer["state"] }
            ?: throw Refused("This sign-in is not the one iCode asked for: sign in again")
        store.savePendingSignIn(null)
        answer["error"]?.let { throw Refused("The sign-in was refused: ${answer["error_description"] ?: it}") }
        val iss = answer["iss"]
        if ((iss != null || pending.optBoolean(ISS_REQUIRED)) && iss?.trimEnd('/') != pending.getString(ISSUER).trimEnd('/')) {
            throw Refused("The sign-in came back from another server")
        }
        val code = answer["code"] ?: throw Refused("The sign-in came back without a code")
        val tokens = tokens(pending, "grant_type" to "authorization_code", "code" to code, "redirect_uri" to redirect, "code_verifier" to pending.getString(VERIFIER))
        val id = pending.getString(SERVER)
        lock.withLock { keep(id, pending, tokens, null) }
        return id
    }

    /** [server]'s `Authorization` value, its token renewed when it has expired; null when it is not signed in. */
    suspend fun authorization(server: McpServer): String? = lock.withLock {
        val saved = saved(server) ?: return null
        val expires = saved.optLong(EXPIRES)
        val current = if (expires != 0L && now() >= expires - MARGIN_MILLIS) renewed(server.id, saved) ?: saved else saved
        "Bearer ${current.getString(ACCESS)}"
    }

    /** [server]'s token renewed after a 401: false when it cannot be, and the user signs in again. */
    suspend fun renew(server: McpServer): Boolean = lock.withLock { saved(server)?.let { renewed(server.id, it) } != null }

    /** The tokens kept for [server]'s address now; a changed address signs in again. */
    private fun saved(server: McpServer): JSONObject? {
        val remote = server.transport as? McpTransport.Remote ?: return null
        return store.signIn(server.id)?.let { runCatching { JSONObject(it) }.getOrNull() }?.takeIf { it.optString(ADDRESS) == remote.url }
    }

    /** Renewed with its refresh token; one the server refuses is forgotten. */
    private suspend fun renewed(id: String, saved: JSONObject): JSONObject? {
        val refresh = saved.optString(REFRESH).takeIf(String::isNotBlank) ?: return null
        return try {
            keep(id, saved, tokens(saved, "grant_type" to "refresh_token", "refresh_token" to refresh), refresh)
        } catch (refused: Refused) {
            store.saveSignIn(id, null)
            null
        }
    }

    private fun keep(id: String, base: JSONObject, tokens: JSONObject, refresh: String?): JSONObject {
        val access = tokens.optString("access_token").takeIf(String::isNotBlank) ?: throw Refused("The sign-in gave no token")
        val kept = JSONObject().apply { listOf(ADDRESS, ISSUER, TOKEN, CLIENT, SECRET, RESOURCE).forEach { put(it, base.opt(it)) } }
            .put(ACCESS, access)
            .put(REFRESH, tokens.optString("refresh_token").takeIf(String::isNotBlank) ?: refresh)
            .put(EXPIRES, if (tokens.has("expires_in")) now() + tokens.optLong("expires_in") * 1_000 else 0L)
        store.saveSignIn(id, kept.toString())
        return kept
    }

    private suspend fun tokens(base: JSONObject, vararg fields: Pair<String, String>): JSONObject {
        val response = http.submitForm(
            base.getString(TOKEN),
            parameters {
                fields.forEach { (name, value) -> append(name, value) }
                append("client_id", base.getString(CLIENT))
                base.optString(SECRET).takeIf(String::isNotEmpty)?.let { append("client_secret", it) }
                append("resource", base.getString(RESOURCE))
            },
        ) { accept(ContentType.Application.Json) }
        return answered(response) ?: throw Refused("The sign-in was refused: ${said(response)}")
    }

    /** The `WWW-Authenticate` challenge an unsigned request meets: its `resource_metadata` and `scope`. */
    private suspend fun challenge(url: String): Map<String, String> =
        http.prepareRequest(url) {
            method = HttpMethod.Post
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "application/json, text/event-stream")
            setBody("""{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"$LATEST_PROTOCOL_VERSION","capabilities":{},"clientInfo":{"name":"iCode","version":"${BuildConfig.VERSION_NAME}"}}}""")
        }.execute { response ->
            response.headers.getAll(HttpHeaders.WWWAuthenticate).orEmpty().flatMap { CHALLENGE.findAll(it) }
                .associate { it.groupValues[1] to (it.groups[2]?.value ?: it.groupValues[3]) }
        }

    /** RFC 9728: the address the challenge names, else the well-known one with the server's path, then without. */
    private suspend fun protectedResource(url: String, named: String?): JSONObject? {
        val path = URI(url).rawPath.orEmpty().trimEnd('/')
        val origin = origin(url)
        return listOfNotNull(named, "$origin/.well-known/oauth-protected-resource$path".takeIf { path.isNotEmpty() }, "$origin/.well-known/oauth-protected-resource")
            .firstNotNullOfOrNull { json(it) }
    }

    /** RFC 8414 and OpenID Connect discovery, in MCP's order; its issuer must be the one asked for. */
    private suspend fun authorizationServer(issuer: String): JSONObject {
        val path = URI(issuer).rawPath.orEmpty().trimEnd('/')
        val origin = origin(issuer)
        val addresses = if (path.isEmpty()) listOf("$origin/.well-known/oauth-authorization-server", "$origin/.well-known/openid-configuration")
        else listOf("$origin/.well-known/oauth-authorization-server$path", "$origin/.well-known/openid-configuration$path", "$origin$path/.well-known/openid-configuration")
        val metadata = addresses.firstNotNullOfOrNull { json(it) } ?: throw Refused("Its sign-in server could not be found")
        if (metadata.optString("issuer").trimEnd('/') != issuer.trimEnd('/')) throw Refused("Its sign-in server names itself differently")
        if (metadata.optString("authorization_endpoint").isBlank() || metadata.optString("token_endpoint").isBlank()) throw Refused("Its sign-in server lists no sign-in page")
        return metadata
    }

    /** iCode as the server's client, in the spec's order: the ID the user entered, iCode's metadata document, registration. */
    private suspend fun register(entered: String?, metadata: JSONObject): Pair<String, String?> {
        entered?.trim()?.takeIf(String::isNotEmpty)?.let { return it to null }
        if (website.isNotEmpty() && metadata.optBoolean("client_id_metadata_document_supported")) {
            val document = "${website.trimEnd('/')}/$CLIENT_DOCUMENT"
            val body = json(document)
            if (body?.optString("client_id") == document && redirect in strings(body.optJSONArray("redirect_uris"))) return document to null
        }
        val endpoint = metadata.optString("registration_endpoint").takeIf(String::isNotBlank) ?: throw Refused(NEEDS_CLIENT)
        val response = http.post(endpoint) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(
                JSONObject().put("client_name", "iCode").put("redirect_uris", JSONArray(listOf(redirect)))
                    .put("grant_types", JSONArray(listOf("authorization_code", "refresh_token"))).put("response_types", JSONArray(listOf("code")))
                    .put("token_endpoint_auth_method", "none").put("application_type", "native").toString(),
            )
        }
        val body = answered(response)
        val client = body?.optString("client_id")?.takeIf(String::isNotBlank) ?: throw Refused(NEEDS_CLIENT)
        return client to body.optString("client_secret").takeIf(String::isNotBlank)
    }

    /** A JSON document at [url]; null when there is none there. */
    private suspend fun json(url: String): JSONObject? = answered(http.get(url) { accept(ContentType.Application.Json) })

    private suspend fun answered(response: HttpResponse): JSONObject? =
        if (!response.status.isSuccess()) null else runCatching { JSONObject(response.bodyAsText()) }.getOrNull()

    private suspend fun said(response: HttpResponse): String {
        val body = runCatching { JSONObject(response.bodyAsText()) }.getOrNull()
        return body?.optString("error_description")?.takeIf(String::isNotBlank) ?: body?.optString("error")?.takeIf(String::isNotBlank)
            ?: "HTTP ${response.status.value}"
    }

    private fun random(bytes: Int): String = encode(ByteArray(bytes).also(random::nextBytes))

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun strings(array: JSONArray?): List<String> = array?.let { (0 until it.length()).map(it::optString) }.orEmpty()

    internal companion object {
        /** iCode's client metadata document on icode-web, naming [redirect] among its `redirect_uris` (D-M1). */
        const val CLIENT_DOCUMENT = "oauth/mcp-client.json"
        private const val NEEDS_CLIENT = "Its sign-in does not register iCode by itself: enter a client ID on its page"
        private const val VERIFIER_BYTES = 32
        private const val STATE_BYTES = 16
        private const val MARGIN_MILLIS = 60_000L
        private const val SERVER = "server"
        private const val ADDRESS = "address"
        private const val STATE = "state"
        private const val VERIFIER = "verifier"
        private const val ISSUER = "issuer"
        private const val ISS_REQUIRED = "issRequired"
        private const val TOKEN = "token"
        private const val CLIENT = "client"
        private const val SECRET = "secret"
        private const val RESOURCE = "resource"
        private const val ACCESS = "access"
        private const val REFRESH = "refresh"
        private const val EXPIRES = "expires"
        private val CHALLENGE = Regex("""([A-Za-z_]+)=(?:"([^"]*)"|([^\s,"]+))""")

        /** Where the browser comes back to for [applicationId]: its own scheme, which the manifest takes. */
        fun redirect(applicationId: String) = "$applicationId:/mcp-sign-in"

        /** `https://host:port` of [url]. */
        fun origin(url: String): String = URI(url).let { "${it.scheme}://${it.rawAuthority}" }

        /** The server's canonical URI (RFC 8707), as MCP sends it: lower-case scheme and host, no fragment, no trailing slash. */
        fun canonical(url: String): String = URI(url.trim()).let { "${it.scheme.lowercase()}://${it.rawAuthority.lowercase()}${it.rawPath.orEmpty().trimEnd('/')}" }
    }
}
