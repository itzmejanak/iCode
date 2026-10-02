package dev.icode.mobile.data.assistant.koog

import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantProviderKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The real stack — Koog's client on Ktor and OkHttp, as on the phone — against a server on this
 * machine that answers as OpenAI-compatible ones do: its API under `/v1`, and for any other path
 * its website's page with 200 (found on the phone, 2026-10-02: `…/v1/v1/models` was asked).
 */
class KoogServerTest {
    /** What `/v1/models` answers, as JSON; null for a server with no API at all. */
    private var models: String? = null
    private var status = 200

    /** The API under `/v1`; any other path, the website's page with 200. */
    private val server = LocalHttpServer { request ->
        val listed = models
        if (listed != null && request.path == "/v1/models") {
            LocalHttpServer.Response(status, "application/json", listed)
        } else {
            LocalHttpServer.Response(200, "text/html; charset=utf-8", "<!DOCTYPE html><html><title>Inference</title></html>")
        }
    }

    @After fun stop() = server.close()

    private val asked get() = server.requests.map { it.path }

    private fun serve(models: String?, status: Int = 200) {
        this.models = models
        this.status = status
    }

    private fun connection(path: String = "/v1") = AssistantConnection(AssistantProviderKind.OpenAICompatible, server.address(path), apiKey = "key")

    private fun available(connection: AssistantConnection = connection()) = runBlocking { KoogAssistantEngine.create().available(connection) }

    @Test fun `an OpenAI-compatible server's models are asked for under the address given`() {
        serve(
            """{"data":[{"id":"MiniMaxAI/MiniMax-M2.7","object":"model","created":1677610602,"owned_by":"gonka"},""" +
                """{"id":"zai-org/GLM-5.3-Flash","object":"model","created":1677610602,"owned_by":"gonka"},""" +
                """{"id":"deepseek-ai/DeepSeek-V4-Flash-0731","object":"model","created":1677610602,"owned_by":"gonka"}],"object":"list"}""",
        )

        val models = available()

        assertEquals("never under a second v1", listOf("/v1/models"), asked)
        assertEquals(listOf("deepseek-ai/DeepSeek-V4-Flash-0731", "MiniMaxAI/MiniMax-M2.7", "zai-org/GLM-5.3-Flash"), models.map { it.id })
    }

    @Test fun `an address ending in a slash asks the same place`() {
        serve("""{"data":[{"id":"a"}]}""")
        available(connection("/v1/"))
        assertEquals(listOf("/v1/models"), asked)
    }

    @Test fun `a list that says only each model's ID is enough`() {
        // As servers send that are not OpenAI: no object, no created, no owned_by.
        serve("""{"data":[{"id":"qwen3-coder"},{"id":"llama-4"},{"name":"no id, skipped"}]}""")
        assertEquals(listOf("llama-4", "qwen3-coder"), available().map { it.id })
    }

    @Test fun `a server that answers with its web page is said to be the wrong address`() {
        serve(models = null)
        assertEquals("127.0.0.1 did not answer as this API does — check the server address in the provider's settings", refusal())
    }

    @Test fun `an answer that is JSON but no list of models is the wrong address too`() {
        serve("""{"status":"ok"}""")
        assertEquals("127.0.0.1 did not answer as this API does — check the server address in the provider's settings", refusal())
    }

    @Test fun `a refused key is still said as a refused key`() {
        serve("""{"error":{"message":"Invalid API key"}}""", status = 401)
        assertEquals("The provider refused the API key — check it in the provider's settings", refusal())
    }

    private fun refusal(): String? = try {
        available()
        fail("expected a refusal")
        null
    } catch (refused: AssistantTurnException) {
        refused.message
    }
}
