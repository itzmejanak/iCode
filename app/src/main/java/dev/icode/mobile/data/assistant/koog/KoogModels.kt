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
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * Koog's models in iCode's words: Koog's own when its catalogue has one,
 * so its capabilities and limits are Koog's; otherwise a model that chats and calls tools, with
 * what the user — or its server — says of it.
 */
internal object KoogModels {
    /** Per provider, the catalogue's models that chat and call tools, with their limits and efforts. */
    val catalogue: Map<AssistantProviderKind, List<AssistantModel>> by lazy {
        AssistantProviderKind.entries.associateWith { kind ->
            models(kind).filter(::chats).distinctBy { it.id }.map { assistantModel(kind, it, known = true) }
        }
    }

    /** [spec] with [kind]: the catalogue's model, or one with what the user said of it. */
    fun model(kind: AssistantProviderKind, spec: AssistantModelSpec): AssistantModel {
        known(kind, spec.id)?.let { return assistantModel(kind, it, known = true) }
        // A typed model's limits are the user's: its LLModel carries none.
        return assistantModel(kind, llModel(kind, spec), known = false).copy(contextWindow = spec.contextWindow, maxOutput = spec.maxOutput)
    }

    /**
     * A model its server listed: the catalogue's when Koog knows it; else what the server said — an
     * Ollama's card names its context and whether it sees images — as a model iCode can use. Null
     * for one Koog knows cannot chat and call tools, or one the server says calls none.
     */
    fun listed(kind: AssistantProviderKind, server: LLModel): AssistantModel? {
        val id = server.id.removePrefix(GOOGLE_PREFIX)
        known(kind, id)?.let { return if (chats(it)) assistantModel(kind, it, known = true) else null }
        val capabilities = server.capabilities.orEmpty()
        // Only Ollama's cards say what a model does; a list of IDs says nothing either way.
        if (kind == AssistantProviderKind.Ollama && LLMCapability.Tools !in capabilities) return null
        val spec = AssistantModelSpec(
            id = id,
            contextWindow = server.contextLength.takeIf { kind == AssistantProviderKind.Ollama },
            images = LLMCapability.Vision.Image in capabilities,
        )
        return model(kind, spec)
    }

    /**
     * Koog's `LLModel` for [spec]: the catalogue's instance itself, or a typed one with the
     * capabilities the user gave it. Its limits stay out of it — they go in each request's
     * parameters ([KoogParams]) — so it is the same model wherever it is made.
     */
    fun llModel(kind: AssistantProviderKind, spec: AssistantModelSpec): LLModel =
        known(kind, spec.id) ?: LLModel(
            provider = provider(kind),
            id = spec.id,
            capabilities = TYPED + listOfNotNull(
                // OpenAI's client asks which endpoint a model speaks; a typed one gets Chat Completions,
                // the one every OpenAI-compatible server has.
                LLMCapability.OpenAIEndpoint.Completions.takeIf { provider(kind) == LLMProvider.OpenAI },
                LLMCapability.Vision.Image.takeIf { spec.images },
                LLMCapability.Thinking.takeIf { spec.reasoning },
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
     * The efforts iCode can send for [model] with [kind]: only for a model that reasons —
     * Koog's word for it, or the user's for one it does not know. Koog's own fields where its client
     * has one; for OpenRouter and an OpenAI-compatible server, the field their APIs read
     * ([KoogParams]), so a model there that reasons has its effort too (owner's request, 2026-11-04).
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
            AssistantProviderKind.OpenRouter, AssistantProviderKind.OpenAICompatible -> listOf(AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High)
        }
    }

    /** Gemini 2.x takes a thinking budget, Gemini 3 a thinking level; Koog refuses both at once. */
    fun usesThinkingBudget(model: LLModel): Boolean = model.id.startsWith("gemini-2")

    private fun known(kind: AssistantProviderKind, id: String): LLModel? = models(kind).firstOrNull { it.id == id }

    private fun assistantModel(kind: AssistantProviderKind, model: LLModel, known: Boolean) = AssistantModel(
        id = model.id,
        contextWindow = model.contextLength,
        maxOutput = model.maxOutputTokens,
        efforts = efforts(kind, model),
        reasons = model.supports(LLMCapability.Thinking),
        images = model.supports(LLMCapability.Vision.Image),
        documents = model.supports(LLMCapability.Document),
        known = known,
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

    /** Google lists its models as `models/<id>`; requests name them by the ID alone. */
    private const val GOOGLE_PREFIX = "models/"

    private val TYPED = listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.ToolChoice, LLMCapability.Temperature)
}
