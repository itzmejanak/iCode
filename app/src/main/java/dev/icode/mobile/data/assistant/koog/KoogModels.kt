package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * A provider and a typed model ID as Koog's `LLModel` (§2): Koog's own when its catalogue has it, so
 * its capabilities are Koog's; otherwise one that can chat and call tools, which every client
 * checks before it sends a tool.
 */
internal object KoogModels {
    /** Koog's catalogue per provider, the models that chat and call tools: suggestions, never a limit. */
    fun suggestions(): Map<AssistantProviderKind, List<String>> =
        AssistantProviderKind.entries.associateWith { kind ->
            catalogue(kind).filter { it.supports(LLMCapability.Completion) && it.supports(LLMCapability.Tools) }.map { it.id }.distinct()
        }

    fun resolve(kind: AssistantProviderKind, id: String): LLModel =
        catalogue(kind).firstOrNull { it.id == id } ?: LLModel(
            provider = provider(kind),
            id = id,
            capabilities = TYPED + listOfNotNull(
                // OpenAI's client asks which endpoint a model speaks; a typed one gets Chat Completions,
                // the one every OpenAI-compatible server has.
                LLMCapability.OpenAIEndpoint.Completions.takeIf { provider(kind) == LLMProvider.OpenAI },
            ),
        )

    fun provider(kind: AssistantProviderKind): LLMProvider = when (kind) {
        AssistantProviderKind.OpenAI, AssistantProviderKind.OpenAICompatible -> LLMProvider.OpenAI
        AssistantProviderKind.Anthropic -> LLMProvider.Anthropic
        AssistantProviderKind.Google -> LLMProvider.Google
        AssistantProviderKind.OpenRouter -> LLMProvider.OpenRouter
        AssistantProviderKind.Ollama -> LLMProvider.Ollama
    }

    private fun catalogue(kind: AssistantProviderKind): List<LLModel> = when (kind) {
        AssistantProviderKind.OpenAI -> OpenAIModels.models
        AssistantProviderKind.Anthropic -> AnthropicModels.models
        AssistantProviderKind.Google -> GoogleModels.models
        AssistantProviderKind.OpenRouter -> OpenRouterModels.models
        AssistantProviderKind.Ollama -> OllamaModels.models
        // A server of the user's own: Koog knows nothing of what it serves.
        AssistantProviderKind.OpenAICompatible -> emptyList()
    }

    private val TYPED = listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Temperature)
}
