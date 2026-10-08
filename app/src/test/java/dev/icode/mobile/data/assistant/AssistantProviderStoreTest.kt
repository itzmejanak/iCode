package dev.icode.mobile.data.assistant

import dev.icode.mobile.domain.AssistantChoice
import dev.icode.mobile.domain.AssistantMode
import dev.icode.mobile.domain.AssistantModelSpec
import dev.icode.mobile.domain.AssistantProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantProviderStoreTest {
    private val preferences = InMemoryPreferences()
    private val store = SharedPreferencesAssistantProviderStore(preferences, { "enc:$it" }, { it.removePrefix("enc:").takeIf { _ -> it.startsWith("enc:") } })

    @Test fun `the mode is kept, and an "Agent" saved before the rename reads as Code`() {
        assertEquals(AssistantMode.Code, store.settings().mode)
        store.chooseMode(AssistantMode.Plan)
        assertEquals(AssistantMode.Plan, store.settings().mode)
        // "Agent" was renamed to Code; a settings saved under the old name keeps the user's choice.
        preferences.edit().putString("mode", "Agent").commit()
        assertEquals(AssistantMode.Code, store.settings().mode)
    }

    @Test fun `providers keep their own keys, models in order, and the first model added is the chat's`() {
        assertNull(store.settings().choice)
        val openAI = store.saveProvider("", AssistantProviderKind.OpenAI, " OpenAI ", null, "sk-1", listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("o3")))
        val local = store.saveProvider("", AssistantProviderKind.OpenAICompatible, "LM Studio", "http://127.0.0.1:1234/v1", null, listOf(AssistantModelSpec("qwen", 32_000, images = true)))
        val settings = store.settings()
        assertEquals(listOf("OpenAI", "LM Studio"), settings.providers.map { it.name })
        assertEquals(listOf("gpt-5", "o3"), settings.providers[0].models.map { it.id })
        assertEquals(AssistantModelSpec("qwen", 32_000, images = true), settings.providers[1].models.single())
        assertEquals(AssistantChoice(openAI, "gpt-5"), settings.choice)
        assertEquals(AssistantConnection(AssistantProviderKind.OpenAI, null, "sk-1"), store.connection(openAI))
        assertEquals(AssistantConnection(AssistantProviderKind.OpenAICompatible, "http://127.0.0.1:1234/v1", null), store.connection(local))
        assertTrue(settings.providers[0].hasKey)
        assertFalse(settings.providers[1].hasKey)
        assertTrue("a key is never kept in the clear", preferences.all.values.none { it == "sk-1" })
    }

    @Test fun `saving without a key keeps it, unless the kind changed`() {
        val id = store.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk-1", listOf(AssistantModelSpec("gpt-5")))
        store.saveProvider(id, AssistantProviderKind.OpenAI, "Work", null, null, listOf(AssistantModelSpec("gpt-5")))
        assertEquals("sk-1", store.connection(id)?.apiKey)
        store.saveProvider(id, AssistantProviderKind.Anthropic, "Work", null, null, listOf(AssistantModelSpec("claude-sonnet-4-5")))
        assertNull(store.connection(id)?.apiKey)
    }

    @Test fun `the choice stays on a model that is listed, moving to the first left when one goes`() {
        val openAI = store.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk", listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("o3")))
        val anthropic = store.saveProvider("", AssistantProviderKind.Anthropic, "Anthropic", null, "sk", listOf(AssistantModelSpec("claude-sonnet-4-5")))
        store.choose(AssistantChoice(openAI, "o3"))
        assertEquals(AssistantChoice(openAI, "o3"), store.settings().choice)
        store.choose(AssistantChoice(openAI, "not-listed"))
        assertEquals(AssistantChoice(openAI, "o3"), store.settings().choice)
        // o3 taken out: its provider's first.
        store.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5")))
        assertEquals(AssistantChoice(openAI, "gpt-5"), store.settings().choice)
        store.removeProvider(openAI)
        assertEquals(AssistantChoice(anthropic, "claude-sonnet-4-5"), store.settings().choice)
        assertNull(store.connection(openAI))
        store.removeProvider(anthropic)
        assertNull(store.settings().choice)
    }

    @Test fun `the commit messages' model is kept while it is listed, and none is the chat's`() {
        val openAI = store.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk", listOf(AssistantModelSpec("gpt-5"), AssistantModelSpec("gpt-5-mini")))
        assertNull(store.settings().commitChoice)
        store.chooseCommitModel(AssistantChoice(openAI, "gpt-5-mini"))
        assertEquals(AssistantChoice(openAI, "gpt-5-mini"), store.settings().commitChoice)
        // Not listed: refused, the one before kept.
        store.chooseCommitModel(AssistantChoice(openAI, "not-listed"))
        assertEquals(AssistantChoice(openAI, "gpt-5-mini"), store.settings().commitChoice)
        // Taken out of its provider: the chat's model writes them again.
        store.saveProvider(openAI, AssistantProviderKind.OpenAI, "OpenAI", null, null, listOf(AssistantModelSpec("gpt-5")))
        assertNull(store.settings().commitChoice)
        store.chooseCommitModel(AssistantChoice(openAI, "gpt-5"))
        store.chooseCommitModel(null)
        assertNull(store.settings().commitChoice)
        // The chat's choice is its own.
        assertEquals(AssistantChoice(openAI, "gpt-5"), store.settings().choice)
    }

    @Test fun `a key this device can no longer decrypt reads as missing, and is removed`() {
        val id = store.saveProvider("", AssistantProviderKind.OpenAI, "OpenAI", null, "sk-1", listOf(AssistantModelSpec("gpt-5")))
        preferences.edit().putString("key.$id", "from another device").commit()
        assertNull(store.connection(id)?.apiKey)
        assertFalse(store.settings().providers.single().hasKey)
    }
}
