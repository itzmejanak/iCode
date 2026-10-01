package dev.icode.mobile.data.assistant.koog

import ai.koog.prompt.executor.clients.anthropic.AnthropicModels
import ai.koog.prompt.executor.clients.anthropic.AnthropicParams
import ai.koog.prompt.executor.clients.anthropic.models.AnthropicThinking
import ai.koog.prompt.executor.clients.google.GoogleParams
import ai.koog.prompt.executor.clients.google.models.GoogleThinkingLevel
import ai.koog.prompt.executor.clients.openai.OpenAIChatParams
import ai.koog.prompt.executor.clients.openai.OpenAIResponsesParams
import ai.koog.prompt.executor.clients.openai.base.models.ReasoningEffort
import ai.koog.prompt.executor.ollama.client.OllamaParams
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import dev.icode.mobile.domain.AssistantEffort
import dev.icode.mobile.domain.AssistantModel
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Koog's catalogue as iCode offers it, and the parameters each request carries (§12 R2–R4). */
class KoogModelsTest {
    private val catalogue = KoogModels.catalogue

    @Test fun `the catalogue lists models that chat and call tools, with Koog's limits`() {
        assertTrue(catalogue.getValue(AssistantProviderKind.OpenAICompatible).isEmpty())
        AssistantProviderKind.entries.filter { it != AssistantProviderKind.OpenAICompatible }.forEach { kind ->
            val models = catalogue.getValue(kind)
            assertTrue("$kind", models.isNotEmpty())
            assertEquals("$kind", models.size, models.map { it.id }.toSet().size)
            models.forEach { model ->
                val koog = KoogModels.llModel(kind, AssistantModelSpec(model.id))
                assertTrue(model.id, koog.supports(LLMCapability.Completion) && koog.supports(LLMCapability.Tools))
                assertEquals(model.id, koog.contextLength, model.contextWindow)
            }
        }
    }

    @Test fun `a catalogued model is Koog's own instance, so Anthropic's client finds it`() {
        val id = catalogue.getValue(AssistantProviderKind.Anthropic).first().id
        assertSame(AnthropicModels.models.first { it.id == id }, KoogModels.llModel(AssistantProviderKind.Anthropic, AssistantModelSpec(id)))
    }

    @Test fun `a typed model chats and calls tools, with only the limits the user set`() {
        val typed = KoogModels.llModel(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("deepseek-chat"))
        assertEquals(LLMProvider.OpenAI, typed.provider)
        assertTrue(typed.supports(LLMCapability.Tools))
        assertTrue(typed.supports(LLMCapability.OpenAIEndpoint.Completions))
        // Made twice, it is the same model: no limits of the user's inside it.
        assertEquals(typed, KoogModels.llModel(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("deepseek-chat")))
        assertEquals(AssistantModel("deepseek-chat"), KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("deepseek-chat")))
        assertEquals(
            AssistantModel("deepseek-chat", 128_000, 8_000),
            KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("deepseek-chat", 128_000, 8_000)),
        )
    }

    @Test fun `efforts are offered only where the model reasons and Koog can send them`() {
        catalogue.getValue(AssistantProviderKind.Anthropic).forEach { model ->
            val reasons = KoogModels.llModel(AssistantProviderKind.Anthropic, AssistantModelSpec(model.id)).supports(LLMCapability.Thinking)
            val expected = if (reasons) listOf(AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High, AssistantEffort.Max) else emptyList()
            assertEquals(model.id, expected, model.efforts)
        }
        catalogue.getValue(AssistantProviderKind.Google).filter { it.efforts.isNotEmpty() }.forEach { model ->
            val expected = if (model.id.startsWith("gemini-2")) 3 else 2
            assertEquals(model.id, expected, model.efforts.size)
        }
        assertTrue(catalogue.getValue(AssistantProviderKind.OpenRouter).all { it.efforts.isEmpty() })
        assertTrue(KoogModels.model(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("o3")).efforts.isEmpty())
    }

    @Test fun `Anthropic always gets an output limit above Koog's 2 048, and a thinking budget inside it`() {
        val model = catalogue.getValue(AssistantProviderKind.Anthropic).first { it.efforts.isNotEmpty() }
        val koog = KoogModels.llModel(AssistantProviderKind.Anthropic, AssistantModelSpec(model.id))
        val plain = KoogParams.of(AssistantProviderKind.Anthropic, koog, model, null) as AnthropicParams
        assertTrue("${plain.maxTokens}", (plain.maxTokens ?: 0) >= 8_192)
        assertNull(plain.thinking)
        val max = KoogParams.of(AssistantProviderKind.Anthropic, koog, model, AssistantEffort.Max) as AnthropicParams
        val budget = (max.thinking as AnthropicThinking.Enabled).budgetTokens
        assertTrue("$budget < ${max.maxTokens}", budget in 1_024 until (max.maxTokens ?: 0))
        // A typed Claude model gets 8 192 unless the user set its limit.
        val typed = KoogModels.llModel(AssistantProviderKind.Anthropic, AssistantModelSpec("claude-next"))
        assertEquals(8_192, (KoogParams.of(AssistantProviderKind.Anthropic, typed, AssistantModel("claude-next"), null) as AnthropicParams).maxTokens)
        assertEquals(16_000, (KoogParams.of(AssistantProviderKind.Anthropic, typed, AssistantModel("claude-next", maxOutput = 16_000), null) as AnthropicParams).maxTokens)
    }

    @Test fun `OpenAI's effort goes in the field its endpoint reads`() {
        val openAI = catalogue.getValue(AssistantProviderKind.OpenAI)
        val chat = openAI.first { it.efforts.isNotEmpty() && KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec(it.id)).supports(LLMCapability.OpenAIEndpoint.Completions) }
        val chatParams = KoogParams.of(AssistantProviderKind.OpenAI, KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec(chat.id)), chat, AssistantEffort.Low)
        assertEquals(ReasoningEffort.LOW, (chatParams as OpenAIChatParams).reasoningEffort)
        val responsesOnly = openAI.firstOrNull { it.efforts.isNotEmpty() && !KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec(it.id)).supports(LLMCapability.OpenAIEndpoint.Completions) }
        if (responsesOnly != null) {
            val params = KoogParams.of(AssistantProviderKind.OpenAI, KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec(responsesOnly.id)), responsesOnly, AssistantEffort.High)
            assertEquals(ReasoningEffort.HIGH, (params as OpenAIResponsesParams).reasoning?.effort)
        }
        // A typed model's limit is the user's; a catalogued one keeps the provider's own.
        val typed = KoogModels.llModel(AssistantProviderKind.OpenAICompatible, AssistantModelSpec("local"))
        assertEquals(4_000, KoogParams.of(AssistantProviderKind.OpenAICompatible, typed, AssistantModel("local", maxOutput = 4_000), null).maxTokens)
        assertNull(KoogParams.of(AssistantProviderKind.OpenAI, KoogModels.llModel(AssistantProviderKind.OpenAI, AssistantModelSpec(chat.id)), chat, null).maxTokens)
    }

    @Test fun `Gemini and Ollama get their own thinking settings`() {
        val gemini3 = catalogue.getValue(AssistantProviderKind.Google).firstOrNull { it.efforts.isNotEmpty() && !it.id.startsWith("gemini-2") }
        if (gemini3 != null) {
            val params = KoogParams.of(AssistantProviderKind.Google, KoogModels.llModel(AssistantProviderKind.Google, AssistantModelSpec(gemini3.id)), gemini3, AssistantEffort.Low) as GoogleParams
            assertEquals(GoogleThinkingLevel.LOW, params.thinkingConfig?.thinkingLevel)
            assertNull(params.thinkingConfig?.thinkingBudget)
        }
        val gemini2 = catalogue.getValue(AssistantProviderKind.Google).first { it.efforts.isNotEmpty() && it.id.startsWith("gemini-2") }
        val budget = KoogParams.of(AssistantProviderKind.Google, KoogModels.llModel(AssistantProviderKind.Google, AssistantModelSpec(gemini2.id)), gemini2, AssistantEffort.Medium) as GoogleParams
        assertEquals(8_192, budget.thinkingConfig?.thinkingBudget)
        val ollama = KoogModels.llModel(AssistantProviderKind.Ollama, AssistantModelSpec("qwen3"))
        assertEquals(true, (KoogParams.of(AssistantProviderKind.Ollama, ollama, AssistantModel("qwen3"), AssistantEffort.On) as OllamaParams).think)
        assertNull((KoogParams.of(AssistantProviderKind.Ollama, ollama, AssistantModel("qwen3"), null) as OllamaParams).think)
    }

    @Test fun `a typed model reads images and reasons when the user says so, and is the same model wherever made`() {
        val spec = AssistantModelSpec("claude-next", 200_000, 64_000, images = true, reasoning = true)
        val model = KoogModels.model(AssistantProviderKind.Anthropic, spec)
        assertEquals(AssistantModel("claude-next", 200_000, 64_000, listOf(AssistantEffort.Low, AssistantEffort.Medium, AssistantEffort.High, AssistantEffort.Max), images = true), model)
        assertEquals(spec, model.spec)
        assertEquals(KoogModels.llModel(AssistantProviderKind.Anthropic, spec), KoogModels.llModel(AssistantProviderKind.Anthropic, model.spec))
        // A catalogued model's facts are Koog's, whatever was typed; it keeps only its ID.
        val known = catalogue.getValue(AssistantProviderKind.Anthropic).first()
        assertEquals(AssistantModelSpec(known.id), KoogModels.model(AssistantProviderKind.Anthropic, AssistantModelSpec(known.id, 1, 1, reasoning = false)).spec)
    }

    @Test fun `a server's listing is kept to models that chat and call tools, an Ollama's by its card`() {
        val withTools = LLModel(LLMProvider.Ollama, "llava:13b", listOf(LLMCapability.Completion, LLMCapability.Tools, LLMCapability.Vision.Image), 32_768)
        assertEquals(
            AssistantModel("llava:13b", contextWindow = 32_768, images = true),
            KoogModels.listed(AssistantProviderKind.Ollama, withTools),
        )
        assertNull(KoogModels.listed(AssistantProviderKind.Ollama, LLModel(LLMProvider.Ollama, "nomic-embed", listOf(LLMCapability.Embed))))
        // Google names its models models/<id>.
        val gemini = catalogue.getValue(AssistantProviderKind.Google).first()
        assertEquals(gemini, KoogModels.listed(AssistantProviderKind.Google, LLModel(LLMProvider.Google, "models/${gemini.id}")))
        // A bare ID from elsewhere says nothing either way: offered, unknown.
        assertEquals(AssistantModel("my-model"), KoogModels.listed(AssistantProviderKind.OpenAICompatible, LLModel(LLMProvider.OpenAI, "my-model")))
    }
}
