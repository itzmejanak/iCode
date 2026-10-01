package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingConfig
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.clients.openai.models.ReasoningConfig
import ai.koog.prompt.executor.ollama.client.OllamaParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.params.LLMParams
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantProviderKind

/**
 * A request's parameters, as each of Koog's clients takes them (§12 R2–R3): the output limit, and
 * the effort where the model offers one ([KoogModels.efforts]).
 */
internal object KoogParams {
    fun of(kind: AssistantProviderKind, model: LLModel, limits: AssistantModel, effort: AssistantEffort?): LLMParams {
        // Anthropic requires a limit (Koog's default is 2 048); elsewhere the provider's own stands
        // unless the user set one for a model Koog does not know.
        val userLimit = limits.maxOutput?.takeIf { model.maxOutputTokens == null }?.toInt()
        return when (kind) {
            AssistantProviderKind.Anthropic -> anthropic(limits, effort)
            AssistantProviderKind.OpenAI -> when {
                effort == null -> LLMParams(maxTokens = userLimit)
                model.supports(LLMCapability.OpenAIEndpoint.Completions) -> OpenAIChatParams(maxTokens = userLimit, reasoningEffort = openAI(effort))
                else -> OpenAIResponsesParams(maxTokens = userLimit, reasoning = ReasoningConfig(effort = openAI(effort)))
            }
            AssistantProviderKind.Google -> GoogleParams(maxTokens = userLimit, thinkingConfig = effort?.let { google(it, model) })
            AssistantProviderKind.Ollama -> OllamaParams(maxTokens = userLimit, think = true.takeIf { effort == AssistantEffort.On })
            AssistantProviderKind.OpenRouter, AssistantProviderKind.OpenAICompatible -> LLMParams(maxTokens = userLimit)
        }
    }

    /**
     * The output limit — the model's, at most [OUTPUT_CAP] since replies are asked for whole — and,
     * with an effort, a thinking budget inside it that leaves [ANSWER_ROOM] for the answer.
     */
    private fun anthropic(limits: AssistantModel, effort: AssistantEffort?): AnthropicParams {
        val output = (limits.maxOutput ?: ANTHROPIC_DEFAULT_OUTPUT).coerceAtMost(OUTPUT_CAP).toInt()
        val room = output - ANSWER_ROOM
        val budget = when (effort) {
            AssistantEffort.Low -> 4_000
            AssistantEffort.Medium -> 12_000
            AssistantEffort.High -> 24_000
            AssistantEffort.Max -> room
            else -> null
        }?.coerceAtMost(room)?.takeIf { it >= ANTHROPIC_MIN_BUDGET }
        return AnthropicParams(maxTokens = output, thinking = budget?.let { AnthropicThinking.Enabled(it) })
    }

    private fun openAI(effort: AssistantEffort): ReasoningEffort = when (effort) {
        AssistantEffort.Minimal -> ReasoningEffort.MINIMAL
        AssistantEffort.Low -> ReasoningEffort.LOW
        AssistantEffort.Medium -> ReasoningEffort.MEDIUM
        AssistantEffort.High, AssistantEffort.Max, AssistantEffort.On -> ReasoningEffort.HIGH
    }

    /** Gemini 2.x: a budget of tokens; Gemini 3: a level. */
    private fun google(effort: AssistantEffort, model: LLModel): GoogleThinkingConfig =
        if (KoogModels.usesThinkingBudget(model)) {
            GoogleThinkingConfig(
                thinkingBudget = when (effort) {
                    AssistantEffort.Minimal, AssistantEffort.Low -> 1_024
                    AssistantEffort.Medium -> 8_192
                    else -> 24_576
                },
            )
        } else {
            GoogleThinkingConfig(thinkingLevel = if (effort == AssistantEffort.Low || effort == AssistantEffort.Minimal) GoogleThinkingLevel.LOW else GoogleThinkingLevel.HIGH)
        }

    /** Every Claude model writes at least this much; one Koog does not know gets it unless the user set more. */
    private const val ANTHROPIC_DEFAULT_OUTPUT = 8_192L

    /** A reply is asked for whole, so a request never waits on more than this. */
    private const val OUTPUT_CAP = 32_000L

    private const val ANSWER_ROOM = 4_096
    private const val ANTHROPIC_MIN_BUDGET = 1_024
}
