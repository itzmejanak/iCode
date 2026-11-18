package dev.icode.mobile.data.assistant.acp

import dev.icode.mobile.data.assistant.InMemoryPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What iCode keeps about the agents in the chats, over preferences in memory and a stand-in cipher. */
class AgentPreferencesTest {
    private val stored = InMemoryPreferences()
    private var decryptable = true
    private val preferences = AgentPreferences(stored, { "enc:${it.reversed()}" }, { value -> value.removePrefix("enc:").reversed().takeIf { decryptable } })

    @Test fun `the agent chosen last is kept until a model is`() {
        assertNull(preferences.last)
        preferences.last = "codex-acp"
        assertEquals("codex-acp", preferences.last)
        preferences.last = null
        assertNull(preferences.last)
    }

    @Test fun `the notes already given are kept per agent`() {
        preferences.said("opencode")
        preferences.said("cline")
        assertEquals(setOf("opencode", "cline"), preferences.untestedSaid())
    }

    @Test fun `keys are kept encrypted, merged, and an emptied one is taken out`() {
        preferences.saveKeys("gemini", mapOf("GEMINI_API_KEY" to "secret-1", "GOOGLE_CLOUD_PROJECT" to "p"))
        assertFalse("A key is never kept as typed", stored.all.values.any { it.toString().contains("secret-1") })
        preferences.saveKeys("gemini", mapOf("GOOGLE_CLOUD_PROJECT" to ""))
        assertEquals(mapOf("GEMINI_API_KEY" to "secret-1"), preferences.keys("gemini"))
        assertTrue(preferences.keys("codex-acp").isEmpty())
    }

    @Test fun `keys this device can no longer decrypt read as none`() {
        preferences.saveKeys("gemini", mapOf("GEMINI_API_KEY" to "secret-1"))
        decryptable = false
        assertTrue(preferences.keys("gemini").isEmpty())
    }

    @Test fun `an agent forgotten loses its keys, and others keep theirs`() {
        preferences.saveKeys("gemini", mapOf("GEMINI_API_KEY" to "secret-1"))
        preferences.saveKeys("cline", mapOf("CLINE_KEY" to "secret-2"))
        preferences.forget("gemini")
        assertTrue(preferences.keys("gemini").isEmpty())
        assertEquals(mapOf("CLINE_KEY" to "secret-2"), preferences.keys("cline"))
    }
}
