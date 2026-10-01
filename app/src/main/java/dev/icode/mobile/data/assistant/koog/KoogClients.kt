package dev.icode.mobile.data.assistant.koog

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openrouter.OpenRouterLLMClient
import ai.koog.prompt.executor.ollama.client.OllamaClient
import dev.icode.mobile.data.assistant.AssistantConnection
import dev.icode.mobile.data.assistant.AssistantTurnException
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * One of Koog's clients for a connection (§4.2), on the HTTP stack iCode hands it: never one Koog
 * would find by `ServiceLoader`, which R8 may not keep.
 */
internal object KoogClients {
    fun create(connection: AssistantConnection, http: KoogHttpClient.Factory): LLMClient {
        val key = connection.apiKey
        fun required(): String = key ?: throw AssistantTurnException("Enter the API key in Assistant settings")
        return when (connection.kind) {
            AssistantProviderKind.OpenAI -> OpenAILLMClient(required(), OpenAIClientSettings(), http)
            // The address the user gave ends where the API's paths begin (…/v1).
            AssistantProviderKind.OpenAICompatible -> OpenAILLMClient(
                key.orEmpty(),
                OpenAIClientSettings(baseUrl = address(connection), chatCompletionsPath = "chat/completions"),
                http,
            )
            AssistantProviderKind.Anthropic -> {
                // Anthropic's client refuses a model missing from its map; a typed one maps to itself.
                val model = KoogModels.llModel(connection.kind, connection.model)
                val versions = AnthropicClientSettings().modelVersionsMap
                AnthropicLLMClient(required(), AnthropicClientSettings(modelVersionsMap = versions + (model to (versions[model] ?: model.id))), http)
            }
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

    private fun address(connection: AssistantConnection): String =
        connection.baseUrl ?: connection.kind.defaultBaseUrl ?: throw AssistantTurnException("Enter the server's address in Assistant settings")
}
