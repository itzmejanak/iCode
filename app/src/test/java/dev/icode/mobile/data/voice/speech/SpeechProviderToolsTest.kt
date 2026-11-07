package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.voice.SpeechProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The agent's speech provider tools (docs/speech-plan.md §6) over the pages' own operations: what it
 * adds, changes and removes is what the pages show, and a key is never read back.
 */
class SpeechProviderToolsTest {
    private val preferences = InMemoryPreferences()
    private val store = SpeechProviderStore(preferences, { "sealed:$it" }, { it.removePrefix("sealed:") })
    private val stateHolder = ICodeStateHolder()
    private val providers = SpeechProviders(store, stateHolder, CoroutineScope(SupervisorJob() + Dispatchers.Default)) { id -> "sk-assistant".takeIf { id == "p-1" } }
    private val list = ListSpeechProvidersTool(providers)
    private val save = SaveSpeechProviderTool(providers)
    private val remove = RemoveSpeechProviderTool(providers)

    private fun run(tool: AssistantTool, arguments: JSONObject): String = runBlocking { tool.run(arguments) }

    private fun refusal(tool: AssistantTool, arguments: JSONObject): String = try {
        run(tool, arguments)
        fail("expected a refusal")
        ""
    } catch (refused: AssistantToolException) {
        refused.message.orEmpty()
    }

    private val assembly = JSONObject().put("kind", "AssemblyAI").put("key", "aai-secret").put("use", true)

    @Test
    fun `a provider is added, listened with, listed as the page shows it, and its key never read back`() {
        assertEquals(
            "android · Android · Android · listens now\nlanguage: automatic\naudio: On this phone, or its recognition service",
            run(list, JSONObject()),
        )
        val added = run(save, assembly)
        val id = store.providers().last().id
        assertEquals("Added AssemblyAI ($id), key set; it hears the microphone.", added)
        assertEquals(id, stateHolder.current.voice.inUse)
        val listed = run(list, JSONObject())
        assertTrue(listed, listed.endsWith("$id · AssemblyAI · AssemblyAI · listens now\nmodel: universal-3-5-pro · language: automatic · key: set\naudio: streaming.assemblyai.com"))
        assertFalse(listed.contains("aai-secret"))
        assertEquals("aai-secret", store.key(id))
        // What the user is asked names the key, never shows it.
        val asked = save.detail(assembly)
        assertTrue(asked, asked.contains("“AssemblyAI” · AssemblyAI · audio to streaming.assemblyai.com") && asked.contains("key: given") && !asked.contains("aai-secret"))
    }

    @Test
    fun `a provider is changed by its id, only in what is given`() {
        run(save, assembly)
        val id = store.providers().last().id
        assertEquals(
            "Changed Dictation ($id), key set; it hears the microphone.",
            run(save, JSONObject().put("id", id).put("name", "Dictation").put("language", "de")),
        )
        val kept = store.providers().last()
        assertEquals("Dictation", kept.name)
        assertEquals("de", kept.language)
        assertEquals("aai-secret", store.key(id))
        assertEquals("Change the speech provider Dictation", save.summary(JSONObject().put("id", id)))
    }

    @Test
    fun `what the page refuses, the model is told in its words`() {
        assertEquals("Enter AssemblyAI's API key", refusal(save, JSONObject().put("kind", "AssemblyAI")))
        val kinds = "AssemblyAI, OpenAI, OpenAICompatible, Deepgram, Google"
        assertEquals("Give kind, to add one: $kinds", refusal(save, JSONObject().put("key", "k")))
        assertEquals("Whisper is not a kind: $kinds", refusal(save, JSONObject().put("kind", "Whisper")))
        assertEquals("There is no speech provider s-0: list_speech_providers lists them", refusal(save, JSONObject().put("id", "s-0")))
        assertEquals("Android's recognizer cannot be removed", refusal(remove, JSONObject().put("id", SpeechProvider.ANDROID)))
        assertEquals(1, store.providers().size)
    }

    @Test
    fun `removing the one that listens gives the microphone back to Android`() {
        run(save, assembly)
        val id = store.providers().last().id
        assertEquals("Remove the speech provider AssemblyAI", remove.summary(JSONObject().put("id", id)))
        assertEquals("Removed AssemblyAI. Android's recognizer hears the microphone from now on.", run(remove, JSONObject().put("id", id)))
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.inUse)
        assertEquals(listOf(SpeechProvider.Android), store.providers())
    }

    @Test
    fun `changes ask every time, in every mode, and a reading mode lists only`() {
        assertTrue(save.asksAlways && remove.asksAlways)
        assertFalse(list.asksAlways)
        assertEquals(listOf(AssistantToolKind.Read, AssistantToolKind.Change, AssistantToolKind.Change), speechProviderTools(providers).map { it.kind })
    }

    @Test
    fun `a failed hearing shows in the list`() {
        providers.heard(SpeechProvider.ANDROID, "Speech recognition is busy: try again")
        assertTrue(run(list, JSONObject()).endsWith("\nlast failure: Speech recognition is busy: try again"))
    }
}
