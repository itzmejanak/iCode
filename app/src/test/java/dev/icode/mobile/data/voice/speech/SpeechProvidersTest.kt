package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantProvider
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.VoiceRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The speech providers' store and owner (docs/speech-plan.md §4.5): keys sealed, refusals in the page's words. */
class SpeechProvidersTest {
    private val preferences = InMemoryPreferences()
    private var decryptable = true
    private val store = SpeechProviderStore(preferences, { "sealed:$it" }, { secret -> secret.removePrefix("sealed:").takeIf { decryptable } })
    private val stateHolder = ICodeStateHolder()
    private val providers = SpeechProviders(store, stateHolder, CoroutineScope(SupervisorJob() + Dispatchers.Default)) { id -> "sk-assistant".takeIf { id == "p-1" } }
    private val assembly = SpeechProvider("", SpeechProviderKind.AssemblyAI, "AssemblyAI")

    @Test
    fun `a key is sealed, never in a provider, and given only for a hearing`() {
        val saved = providers.save(assembly, "aai-secret").getOrThrow()
        assertTrue(saved.id.startsWith("s-"))
        assertTrue(saved.hasKey)
        assertEquals("sealed:aai-secret", preferences.getString("key.${saved.id}", null))
        assertFalse(preferences.all.values.any { it.toString() == "aai-secret" })
        assertEquals(saved to "aai-secret", providers.connection(saved.id, SpeechWay.Listening).getOrThrow())
        assertEquals(listOf(SpeechProvider.Android, saved), stateHolder.current.voice.providers)
    }

    @Test
    fun `saved without a new key keeps the old one`() {
        val saved = providers.save(assembly, "aai-secret").getOrThrow()
        providers.save(saved.copy(model = "universal-3-6"), key = null).getOrThrow()
        assertEquals("aai-secret", store.key(saved.id))
        assertEquals("universal-3-6", store.providers().last().modelUsed)
    }

    @Test
    fun `a key this phone cannot decrypt is forgotten, and a hearing asks for it`() {
        val saved = providers.save(assembly, "aai-secret").getOrThrow()
        decryptable = false
        assertEquals("Enter AssemblyAI's key in Settings › Assistant › Voice", providers.connection(saved.id, SpeechWay.Listening).exceptionOrNull()?.message)
        assertFalse(store.providers().last().hasKey)
    }

    @Test
    fun `what the page refuses, it says`() {
        assertEquals("Enter AssemblyAI's API key", providers.save(assembly, null).exceptionOrNull()?.message)
        assertEquals("Name the provider", providers.save(assembly.copy(name = " "), "k").exceptionOrNull()?.message)
        providers.save(assembly, "k").getOrThrow()
        assertEquals("Another provider is called AssemblyAI", providers.save(assembly, "k").exceptionOrNull()?.message)
        assertEquals("Write the language as a tag: en, or en-US", providers.save(assembly.copy(name = "B", language = "English"), "k").exceptionOrNull()?.message)
        assertEquals("There is no speech provider s-0", providers.save(assembly.copy(id = "s-0"), "k").exceptionOrNull()?.message)
        assertEquals("Android's recognizer and voice cannot be removed", providers.remove(SpeechProvider.ANDROID).exceptionOrNull()?.message)
        assertEquals(2, store.providers().size)
    }

    @Test
    fun `the one in use listens, and once it is removed Android's does`() {
        val saved = providers.save(assembly, "k").getOrThrow()
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.inUse)
        providers.use(saved.id, SpeechWay.Listening).getOrThrow()
        assertEquals(saved.id, stateHolder.current.voice.inUse)
        assertEquals(saved.id, providers.connection(null, SpeechWay.Listening).getOrThrow().first.id)
        providers.remove(saved.id).getOrThrow()
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.inUse)
        assertNull(preferences.getString("key.${saved.id}", null))
    }

    @Test
    fun `Android's speaks until another is chosen, and removing that one gives each way it served back`() {
        val saved = providers.save(assembly, "k").getOrThrow()
        assertEquals(SpeechProvider.ANDROID, store.speaker())
        store.use(saved.id, SpeechWay.Speaking)
        providers.use(saved.id, SpeechWay.Listening).getOrThrow()
        assertEquals(saved.id, store.speaker())
        assertEquals(saved.id, stateHolder.current.voice.chosen(SpeechWay.Listening).id)
        assertEquals(saved to "k", providers.connection(null, SpeechWay.Listening).getOrThrow())
        providers.remove(saved.id).getOrThrow()
        assertEquals(SpeechProvider.ANDROID, store.speaker())
        assertEquals(SpeechProvider.ANDROID, store.inUse())
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.speaker)
    }

    @Test
    fun `the speaking fields are kept, and a voice choice survives the listening one`() {
        val saved = store.save(assembly.copy(voice = " aai-jane ", voiceModel = "assemblyai-tts-1", speed = 1.25f), "k")
        assertEquals(Triple("aai-jane", "assemblyai-tts-1", 1.25f), Triple(saved.voice, saved.voiceModel, saved.speed))
        store.use(saved.id, SpeechWay.Speaking)
        store.use(SpeechProvider.ANDROID, SpeechWay.Listening)
        assertEquals(saved.id, store.speaker())
        store.use(SpeechProvider.ANDROID, SpeechWay.Speaking)
        assertEquals(SpeechProvider.ANDROID, store.speaker())
    }

    @Test
    fun `a way a kind does not serve, and a field it does not take, are refused in its words`() {
        val azure = providers.save(SpeechProvider("", SpeechProviderKind.Azure, "Azure AI Speech", region = "eastus"), "k").getOrThrow()
        assertEquals("Azure AI Speech cannot listen yet: choose it for reading aloud", providers.connection(azure.id, SpeechWay.Listening).exceptionOrNull()?.message)
        assertEquals(SpeechProvider.ANDROID, store.inUse())
        val openAi = SpeechProvider("", SpeechProviderKind.OpenAI, "OpenAI")
        assertEquals("OpenAI has no voice nova", providers.save(openAi.copy(voice = "nova"), "k").exceptionOrNull()?.message)
        assertEquals("AssemblyAI reads at one speed", providers.save(assembly.copy(speed = 1.25f), "k").exceptionOrNull()?.message)
        assertEquals("Google Cloud has no models to choose for reading aloud", providers.save(SpeechProvider("", SpeechProviderKind.Google, "Google Cloud", voiceModel = "x"), "k").exceptionOrNull()?.message)
        assertEquals("Deepgram reads at 0.75×, Normal, 1.25×, 1.5×", providers.save(SpeechProvider("", SpeechProviderKind.Deepgram, "Deepgram", speed = 2f), "k").exceptionOrNull()?.message)
        // An account's own AssemblyAI voice is named by its slug.
        assertEquals("my-voice", providers.save(assembly.copy(voice = "my-voice"), "k").getOrThrow().voice)
        assertEquals("Android's voice is chosen in Android's settings", providers.save(SpeechProvider.Android.copy(voice = "x"), null).exceptionOrNull()?.message)
        assertEquals(SpeechProvider.Android, providers.connection(null, SpeechWay.Speaking).getOrThrow().first)
    }

    @Test
    fun `a kind that only reads aloud is chosen for speaking, in its region, on its servers`() {
        val azure = SpeechProvider("", SpeechProviderKind.Azure, "Azure AI Speech")
        assertEquals("Enter Azure AI Speech's region: eastus, for one", providers.save(azure, "k").exceptionOrNull()?.message)
        assertEquals("Write the region in lower case, as eastus is", providers.save(azure.copy(region = "East US"), "k").exceptionOrNull()?.message)
        val saved = providers.save(azure.copy(region = "westeurope", voice = "en-US-JennyNeural"), "k").getOrThrow()
        assertEquals("westeurope.tts.speech.microsoft.com", SpeechRules.where(saved, SpeechWay.Speaking))
        assertEquals("Azure AI Speech cannot listen yet: choose it for reading aloud", providers.use(saved.id, SpeechWay.Listening).exceptionOrNull()?.message)
        providers.use(saved.id, SpeechWay.Speaking).getOrThrow()
        assertEquals(saved to "k", providers.connection(null, SpeechWay.Speaking).getOrThrow())
        val eleven = SpeechProvider("", SpeechProviderKind.ElevenLabs, "ElevenLabs")
        assertEquals("api.elevenlabs.io", VoiceRules.host(eleven))
        assertEquals("api.eu.residency.elevenlabs.io", VoiceRules.host(eleven.copy(region = "api.eu.residency.elevenlabs.io")))
        assertTrue(providers.save(eleven.copy(region = "evil.example"), "k").exceptionOrNull()?.message.orEmpty().startsWith("ElevenLabs's servers are api.elevenlabs.io, "))
        assertEquals("Google Cloud has one place: it takes no region", providers.save(SpeechProvider("", SpeechProviderKind.Google, "Google Cloud", region = "eu"), "k").exceptionOrNull()?.message)
    }

    @Test
    fun `Android's keeps only its language`() {
        providers.save(SpeechProvider.Android.copy(name = "Mine", language = "de-DE"), null).getOrThrow()
        assertEquals(SpeechProvider.Android.copy(language = "de-DE"), store.providers().first())
        assertEquals(SpeechProvider.Android.copy(language = "de-DE") to null, providers.connection(SpeechProvider.ANDROID, SpeechWay.Listening).getOrThrow())
    }

    @Test
    fun `a new provider saved from its page is the one its page shows`() {
        providers.handle(SpeechIntent.Edit(assembly))
        providers.handle(SpeechIntent.Save(assembly, "k"))
        val shown = stateHolder.current.voice.editing
        assertTrue(shown?.id.orEmpty().startsWith("s-"))
        assertTrue(shown?.hasKey == true)
    }

    @Test
    fun `a hearing's failure is kept for its page and the agent, and a good one clears it`() {
        providers.heard("s-1", "AssemblyAI refused the key: check it in Settings › Assistant › Voice")
        assertEquals(mapOf("s-1" to "AssemblyAI refused the key: check it in Settings › Assistant › Voice"), stateHolder.current.voice.failures)
        providers.heard("s-1", null)
        assertTrue(stateHolder.current.voice.failures.isEmpty())
    }

    @Test
    fun `an OpenAI provider may use the assistant's OpenAI key, linked and never copied`() {
        stateHolder.update { it.copy(assistant = it.assistant.copy(settings = it.assistant.settings.copy(providers = listOf(AssistantProvider("p-1", AssistantProviderKind.OpenAI, "OpenAI", null, hasKey = true, models = emptyList()))))) }
        val openAi = SpeechProvider("", SpeechProviderKind.OpenAI, "OpenAI", keyFrom = "p-1")
        val saved = providers.save(openAi, key = null).getOrThrow()
        assertEquals("p-1", saved.keyFrom)
        assertFalse(saved.hasKey)
        assertEquals("sk-assistant", providers.connection(saved.id, SpeechWay.Listening).getOrThrow().second)
        assertFalse(preferences.all.values.any { it.toString().contains("sk-assistant") })
        // A key of its own given, the link goes.
        val own = providers.save(saved, "sk-own").getOrThrow()
        assertNull(own.keyFrom)
        assertEquals("sk-own", providers.connection(own.id, SpeechWay.Listening).getOrThrow().second)
        assertEquals("The assistant has no OpenAI provider p-9 with a key", providers.save(own.copy(keyFrom = "p-9"), null).exceptionOrNull()?.message)
        assertEquals("Only OpenAI can use the assistant's key", providers.save(assembly.copy(keyFrom = "p-1"), null).exceptionOrNull()?.message)
    }
}
