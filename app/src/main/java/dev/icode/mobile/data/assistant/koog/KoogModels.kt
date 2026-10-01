package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.openrouter.OpenRouterModels
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * Koog's models in iCode's words (§2, §12 R3–R4): Koog's own when its catalogue has one, so its
 * capabilities and limits are Koog's; otherwise a model that chats and calls tools — every client
 * checks that before it sends a tool — with the limits the user set.
 */
internal object KoogModels {
    /** Per provider, the catalogue's models that chat and call tools, with their limits and efforts. */
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>> by lazy {
        AssistantProviderKind.entries.associateWith { kind ->
            models(kind).filter(::chats).distinctBy { it.id }.map { assistantModel(kind, it, null, null) }
        }
    }

    /** [id] with [kind]: the catalogue's model, the user's limits over its own where set. */
    fun model(kind: AssistantProviderKind, id: String, contextWindow: Long?, maxOutput: Long?): AssistantModel =
        assistantModel(kind, llModel(kind, id), contextWindow, maxOutput)

    /**
     * Koog's `LLModel` for [id]: the catalogue's instance itself, or a typed one. It carries no
     * limits of the user's — no client reads them, they go in each request's parameters — so it is
     * equal wherever it is made: the Anthropic client finds its model by equality.
     */
    fun llModel(kind: AssistantProviderKind, id: String): LLModel =
        models(kind).firstOrNull { it.id == id } ?: LLModel(
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

    /**
     * The efforts Koog 1.3.0 can send for [model] with [kind] (§12 R3): only for a model that
     * reasons, and only where the client has a field for it — none for OpenRouter or an
     * OpenAI-compatible server.
     */
    fun efforts(kind: AssistantProviderKind, model: LLModel): List<AssistantEffort> {
        if (!model.supports(LLMCapability.Thinking)) return emptyList()
        return when (kind) {
            AssistantProviderKind.OpenAI -> listOf(AssistantEffort.Minimal, AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High)
            AssistantProviderKind.Anthropic -> listOf(AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High, AssistantEffort.Max)
            AssistantProviderKind.Google ->
                if (usesThinkingBudget(model)) listOf(AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High)
                else listOf(AssistantEffort.Low, AssistantEffort.High)
            AssistantProviderKind.Ollama -> listOf(AssistantEffort.On)
            AssistantProviderKind.OpenRouter, AssistantProviderKind.OpenAICompatible -> emptyList()
        }
    }

    /** Gemini 2.x takes a thinking budget, Gemini 3 a thinking level; Koog refuses both at once. */
    fun usesThinkingBudget(model: LLModel): Boolean = model.id.startsWith("gemini-2")

    private fun assistantModel(kind: AssistantProviderKind, model: LLModel, contextWindow: Long?, maxOutput: Long?) = AssistantModel(
        id = model.id,
        contextWindow = contextWindow ?: model.contextLength,
        maxOutput = maxOutput ?: model.maxOutputTokens,
        efforts = efforts(kind, model),
    )

    private fun chats(model: LLModel): Boolean = model.supports(LLMCapability.Completion) && model.supports(LLMCapability.Tools)

    private fun models(kind: AssistantProviderKind): List<LLModel> = when (kind) {
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
