package dev.icode.mobile.data.assistant.mcp

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.assistant.koog.LocalHttpServer
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.domain.McpServer
import dev.icode.mobile.domain.McpTransport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.parseQueryString
import java.net.URI
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * docs/assistant-and-mcp.md against an authorization server on this machine: discovery from the 401's
 * challenge, registration in the spec's order, PKCE, `state` and `iss` on the way back, the tokens kept
 * and renewed.
 */
class McpSignInTest {
    private var metadata = """"code_challenge_methods_supported": ["S256"], "authorization_response_iss_parameter_supported": true"""
    private var token = """{"access_token": "first", "refresh_token": "again", "expires_in": 3600}"""
    private var tokenStatus = 200
    private val http: LocalHttpServer = LocalHttpServer { request ->
        val origin = origin()
        when {
            request.method == "POST" && request.path == "/mcp" -> LocalHttpServer.Response(
                401, JSON, """{"error": "unauthorized"}""",
                mapOf("WWW-Authenticate" to """Bearer resource_metadata="$origin/.well-known/oauth-protected-resource/mcp", scope="repo""""),
            )
            request.path == "/.well-known/oauth-protected-resource/mcp" ->
                LocalHttpServer.Response(200, JSON, """{"resource": "$origin/mcp", "authorization_servers": ["$origin/auth"], "scopes_supported": ["repo", "user"]}""")
            request.path == "/.well-known/oauth-authorization-server/auth" -> LocalHttpServer.Response(
                200, JSON,
                """{"issuer": "$origin/auth", "authorization_endpoint": "$origin/auth/authorize", "token_endpoint": "$origin/auth/token", "registration_endpoint": "$origin/auth/register", $metadata}""",
            )
            request.path == "/auth/register" -> LocalHttpServer.Response(201, JSON, """{"client_id": "registered"}""")
            request.path == "/auth/token" -> LocalHttpServer.Response(tokenStatus, JSON, token)
            else -> LocalHttpServer.Response(404, JSON, "{}")
        }
    }
    private val store = McpServerStore(InMemoryPreferences(), { "sealed:$it" }, { it.removePrefix("sealed:") })
    private var now = 0L
    private val signIn = McpSignIn(store, HttpClient(OkHttp), "dev.icode.mobile:/mcp-sign-in", website = "", now = { now })
    private val server = McpServer("m-1", "GitHub", "github", McpTransport.Remote(http.address("/mcp")))

    private fun origin(): String = http.address("")

    @After
    fun close() = http.close()

    @Test
    fun `it signs in, from the challenge to the tokens`() = runBlocking {
        val page = URI(signIn.begin(server))
        assertEquals("/auth/authorize", page.path)
        val asked = parseQueryString(page.rawQuery)
        assertEquals("code", asked["response_type"])
        assertEquals("registered", asked["client_id"])
        assertEquals("dev.icode.mobile:/mcp-sign-in", asked["redirect_uri"])
        assertEquals("S256", asked["code_challenge_method"])
        assertEquals("${origin()}/mcp", asked["resource"])
        // The challenge's scope, not every scope the server lists.
        assertEquals("repo", asked["scope"])
        // iCode registered itself as a native app, with its own scheme.
        val registration = http.requests.single { it.path == "/auth/register" }.body
        assertTrue(registration.contains("\"application_type\":\"native\"") && registration.contains("dev.icode.mobile:/mcp-sign-in"))

        assertEquals("m-1", signIn.finish("dev.icode.mobile:/mcp-sign-in?code=c0de&state=${asked["state"]}&iss=${origin()}/auth"))
        val exchange = parseQueryString(http.requests.last { it.path == "/auth/token" }.body)
        assertEquals("authorization_code", exchange["grant_type"])
        assertEquals("c0de", exchange["code"])
        assertEquals("${origin()}/mcp", exchange["resource"])
        // The verifier sent is the one the challenge was made from.
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(Sha256.digest(exchange["code_verifier"]!!.toByteArray()))
        assertEquals(asked["code_challenge"], challenge)
        assertEquals("Bearer first", signIn.authorization(server))
        assertTrue(store.signIn("m-1")!!.contains("\"issuer\""))
    }

    @Test
    fun `an expired token is renewed with its refresh token, which is kept`() = runBlocking {
        signedIn()
        token = """{"access_token": "second", "expires_in": 3600}"""
        now = 3_600_000L
        assertEquals("Bearer second", signIn.authorization(server))
        assertEquals("refresh_token", parseQueryString(http.requests.last { it.path == "/auth/token" }.body)["grant_type"])
        assertTrue(store.signIn("m-1")!!.contains("\"refresh\":\"again\""))
    }

    @Test
    fun `a refresh the server refuses signs out`() = runBlocking {
        signedIn()
        token = """{"error": "invalid_grant"}"""
        tokenStatus = 400
        assertFalse(signIn.renew(server))
        assertNull(store.signIn("m-1"))
    }

    @Test
    fun `a changed address is signed in again`() = runBlocking {
        signedIn()
        assertNull(signIn.authorization(server.copy(transport = McpTransport.Remote("https://elsewhere.example/mcp"))))
    }

    @Test
    fun `the answer must be this sign-in's, from this server`() = runBlocking {
        val state = parseQueryString(URI(signIn.begin(server)).rawQuery)["state"]
        refused("This sign-in is not the one iCode asked for: sign in again") { signIn.finish("dev.icode.mobile:/mcp-sign-in?code=c&state=forged") }
        refused("The sign-in came back from another server") { signIn.finish("dev.icode.mobile:/mcp-sign-in?code=c&state=$state&iss=https://evil.example") }
        signIn.begin(server).let { page ->
            val again = parseQueryString(URI(page).rawQuery)["state"]
            refused("The sign-in was refused: access_denied") { signIn.finish("dev.icode.mobile:/mcp-sign-in?error=access_denied&state=$again") }
        }
    }

    @Test
    fun `a client ID the user entered comes first, and no PKCE is refused`() = runBlocking {
        val entered = server.copy(transport = McpTransport.Remote(http.address("/mcp"), clientId = "mine"))
        assertEquals("mine", parseQueryString(URI(signIn.begin(entered)).rawQuery)["client_id"])
        assertTrue(http.requests.none { it.path == "/auth/register" })
        metadata = """"code_challenge_methods_supported": ["plain"]"""
        refused("Its sign-in does not take PKCE, which MCP requires") { signIn.begin(server) }
    }

    private suspend fun signedIn() {
        val state = parseQueryString(URI(signIn.begin(server)).rawQuery)["state"]
        signIn.finish("dev.icode.mobile:/mcp-sign-in?code=c0de&state=$state&iss=${origin()}/auth")
    }

    private suspend fun refused(words: String, step: suspend () -> Unit) {
        try {
            step()
            fail("Refused: $words")
        } catch (refused: McpSignIn.Refused) {
            assertEquals(words, refused.message)
        }
    }

    private companion object {
        const val JSON = "application/json"
    }
}
