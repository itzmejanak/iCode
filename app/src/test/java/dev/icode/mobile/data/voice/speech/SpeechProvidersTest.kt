package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.assistant.InMemoryPreferences
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
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
    private val providers = SpeechProviders(store, stateHolder, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    private val assembly = SpeechProvider("", SpeechProviderKind.AssemblyAI, "AssemblyAI")

    @Test
    fun `a key is sealed, never in a provider, and given only for a hearing`() {
        val saved = providers.save(assembly, "aai-secret").getOrThrow()
        assertTrue(saved.id.startsWith("s-"))
        assertTrue(saved.hasKey)
        assertEquals("sealed:aai-secret", preferences.getString("key.${saved.id}", null))
        assertFalse(preferences.all.values.any { it.toString() == "aai-secret" })
        assertEquals(saved to "aai-secret", providers.connection(saved.id).getOrThrow())
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
        assertEquals("Enter AssemblyAI's key in Settings › Assistant › Voice", providers.connection(saved.id).exceptionOrNull()?.message)
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
        assertEquals("Android's recognizer cannot be removed", providers.remove(SpeechProvider.ANDROID).exceptionOrNull()?.message)
        assertEquals(2, store.providers().size)
    }

    @Test
    fun `the one in use listens, and once it is removed Android's does`() {
        val saved = providers.save(assembly, "k").getOrThrow()
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.inUse)
        providers.use(saved.id).getOrThrow()
        assertEquals(saved.id, stateHolder.current.voice.inUse)
        assertEquals(saved.id, providers.connection(null).getOrThrow().first.id)
        providers.remove(saved.id).getOrThrow()
        assertEquals(SpeechProvider.ANDROID, stateHolder.current.voice.inUse)
        assertNull(preferences.getString("key.${saved.id}", null))
    }

    @Test
    fun `Android's keeps only its language`() {
        providers.save(SpeechProvider.Android.copy(name = "Mine", language = "de-DE"), null).getOrThrow()
        assertEquals(SpeechProvider.Android.copy(language = "de-DE"), store.providers().first())
        assertEquals(SpeechProvider.Android.copy(language = "de-DE") to null, providers.connection(SpeechProvider.ANDROID).getOrThrow())
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
}
