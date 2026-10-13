package dev.icode.mobile.data.assistant.koog

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionStreamResponse
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantProviderKind
import org.json.JSONObject

/**
 * One of Koog's clients for a connection (§4.2), on the HTTP stack iCode hands it: never one Koog
 * would find by `ServiceLoader`, which R8 may not keep.
 */
internal object KoogClients {
    fun create(connection: AssistantConnection, http: KoogHttpClient.Factory): LLMClient {
        val key = connection.apiKey
        fun required(): String = key ?: throw AssistantTurnException("Enter the API key in Assistant settings")
        return when (connection.kind) {
            AssistantProviderKind.OpenAI -> ReadingOpenAIClient(required(), OpenAIClientSettings(), http)
            AssistantProviderKind.OpenAICompatible -> CompatibleClient(connection, key.orEmpty(), http)
            AssistantProviderKind.Anthropic -> AnthropicLLMClient(
                required(),
                AnthropicClientSettings(modelVersionsMap = VersionsOrId(AnthropicClientSettings().modelVersionsMap)),
                http,
            )
            AssistantProviderKind.Google -> GoogleLLMClient(required(), httpClientFactory = http)
            AssistantProviderKind.OpenRouter -> OpenRouterLLMClient(required(), httpClientFactory = http)
            AssistantProviderKind.Ollama -> OllamaClient(
                httpClientFactory = http,
                baseUrl = address(connection),
                // An Ollama behind a proxy that wants a token gets it; one on this phone needs none.
                headers = key?.let { mapOf("Authorization" to "Bearer $it") }.orEmpty(),
            )
        }
    }

    /**
     * An OpenAI-compatible server's client. The address the user gave ends where the API's paths
     * begin (…/v1), so every path is under it: Koog's defaults each start with `v1/`, which asked
     * `…/v1/v1/models` — a path such servers answer with their website's page, and 200. Its models
     * are read by their IDs alone: Koog's reading wants each to carry `object`, `created` and
     * `owned_by`, which a server that is not OpenAI need not send. Its whole replies are read as
     * OpenAI's are ([ReadingOpenAIClient]).
     */
    private class CompatibleClient(private val connection: AssistantConnection, key: String, http: KoogHttpClient.Factory) : ReadingOpenAIClient(
        key,
        OpenAIClientSettings(
            baseUrl = address(connection),
            chatCompletionsPath = "chat/completions",
            responsesAPIPath = "responses",
            embeddingsPath = "embeddings",
            moderationsPath = "moderations",
            modelsPath = MODELS,
        ),
        http,
    ) {
        override suspend fun models(): List<LLModel> {
            val answer = httpClient.get(MODELS, String::class)
            // `{"data": [{"id": …}, …]}`; anything else — a page, another service's JSON — is not this API.
            val listed = runCatching { JSONObject(answer).getJSONArray("data") }.getOrNull()
                ?: throw AssistantTurnException(KoogFailures.notTheApi(connection))
            // As Koog's own listing: a model of OpenAI's that it knows comes with what it can do.
            val known = OpenAIModels.models.associateBy { it.id }
            return (0 until listed.length())
                .mapNotNull { listed.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                .map { id -> known[id] ?: LLModel(LLMProvider.OpenAI, id) }
        }
    }

    private const val MODELS = "models"

    /**
     * Anthropic's client refuses a model missing from its map of versions; here a model Koog does
     * not know is named by its own ID, so one client serves every model of the provider.
     */
    private class VersionsOrId(private val versions: Map<LLModel, String>) : Map<LLModel, String> by versions {
        override fun get(key: LLModel): String = versions[key] ?: key.id
    }

    private fun address(connection: AssistantConnection): String =
        connection.baseUrl ?: connection.kind.defaultBaseUrl ?: throw AssistantTurnException("Enter the server's address in Assistant settings")
}

/**
 * Koog's OpenAI client, each whole reply's call arguments made [KoogArguments.readable] and each
 * streamed chunk [KoogChunks.readable] before Koog reads them: Koog's reader throws on what it does
 * not expect, and the reply is lost.
 */
private open class ReadingOpenAIClient(apiKey: String, settings: OpenAIClientSettings, http: KoogHttpClient.Factory) :
    OpenAILLMClient(apiKey, settings, http) {
    override fun decodeResponse(data: String): OpenAIChatCompletionResponse = super.decodeResponse(KoogArguments.readableResponse(data))

    override fun decodeStreamingResponse(data: String): OpenAIChatCompletionStreamResponse = super.decodeStreamingResponse(KoogChunks.readable(data))
}
