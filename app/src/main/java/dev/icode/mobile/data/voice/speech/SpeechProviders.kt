package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.notices.VoiceNotices
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The speech providers' one owner (docs/voice-and-phone-control.md): the pages' intents and the agent's
 * tools call the same operations, one at a time, and what either changes shows at once. A refusal is
 * the user's notice, and the model's words.
 */
internal class SpeechProviders(
    private val store: SpeechProviderStore,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
    /** The key of the assistant's OpenAI provider [id], decrypted for a hearing; null when it has none. */
    private val assistantKey: (id: String) -> String?,
) {
    private val lock = Any()

    fun start() = publish()

    /** Every provider, Android's first; none carries its key. */
    fun list(): List<SpeechProvider> = store.providers()

    /** The provider that listens, by its ID. */
    fun inUse(): String = store.inUse()

    /** The provider that speaks, by its ID. */
    fun speaker(): String = store.speaker()

    /** The assistant's OpenAI providers with a key, which an OpenAI speech provider may take its key from: their IDs and names. */
    fun assistantKeys(): List<Pair<String, String>> = stateHolder.current.assistant.settings.providers
        .filter { it.kind == AssistantProviderKind.OpenAI && it.hasKey }.map { it.id to it.name }

    /** Each provider's last failure, in its words, while iCode runs. */
    fun failures(): Map<String, String> = stateHolder.current.voice.failures

    /** Adds [provider] (ID "") or changes it; [key] null keeps the saved one. Android's changes only its language. */
    fun save(provider: SpeechProvider, key: String?): Result<SpeechProvider> = change {
        val all = store.providers()
        val saved = all.firstOrNull { it.id == provider.id }
        if (provider.id.isNotEmpty() && saved == null) refuse("There is no speech provider ${provider.id}")
        // Android's keeps only its language; what else it is given, the rules refuse in their words.
        val next = if (provider.id == SpeechProvider.ANDROID) {
            SpeechProvider.Android.copy(language = provider.language, voice = provider.voice, voiceModel = provider.voiceModel, speed = provider.speed)
        } else {
            provider
        }
        val keySaved = saved?.hasKey == true && saved.kind == next.kind
        SpeechRules.problem(next, all, keyGiven = !key.isNullOrBlank(), keySaved = keySaved)?.let(::refuse)
        next.keyFrom?.takeIf { key.isNullOrBlank() }?.let { from ->
            if (assistantKeys().none { it.first == from }) refuse("The assistant has no OpenAI provider $from with a key")
        }
        store.save(next, key)
    }

    fun remove(id: String): Result<SpeechProvider> = change {
        if (id == SpeechProvider.ANDROID) refuse("Android's recognizer and voice cannot be removed")
        val saved = find(id)
        store.delete(id)
        saved
    }

    /** [id] serves [way] from now on. */
    fun use(id: String, way: SpeechWay): Result<SpeechProvider> = change {
        val provider = find(id)
        SpeechRules.wayProblem(provider, way)?.let(::refuse)
        store.use(id, way)
        provider
    }

    /** [id]'s provider — null: the one that serves [way] — and its key, decrypted for a hearing or a speech session; or why it cannot serve. */
    fun connection(id: String?, way: SpeechWay): Result<Pair<SpeechProvider, String?>> = runCatching {
        val provider = find(id ?: if (way == SpeechWay.Listening) store.inUse() else store.speaker())
        SpeechRules.wayProblem(provider, way)?.let(::refuse)
        val key = provider.keyFrom?.let(assistantKey) ?: store.key(provider.id)
        if (provider.kind.needsKey && key == null) refuse(SpeechRules.words(SpeechFailure.NoKey, provider))
        provider to key
    }

    /** [id]'s last hearing failed with [words]; or, null, it heard. Kept while iCode runs, for its page and the agent. */
    fun heard(id: String, words: String?) = stateHolder.update { snapshot ->
        val failures = snapshot.voice.failures
        snapshot.copy(voice = snapshot.voice.copy(failures = if (words == null) failures - id else failures + (id to words)))
    }

    /** The pages' operations; a test, a sample and a provider's choices are the voice coordinator's, which reaches the providers. */
    fun handle(intent: SpeechIntent) {
        when (intent) {
            is SpeechIntent.Edit -> stateHolder.update { it.copy(voice = it.voice.copy(editing = intent.provider)) }
            // Saved, its page shows it as kept: a new one now has its ID.
            is SpeechIntent.Save -> save(intent.provider, intent.key).said().onSuccess { kept ->
                stateHolder.update { it.copy(voice = it.voice.copy(editing = kept.takeIf { _ -> it.voice.editing?.id == intent.provider.id } ?: it.voice.editing)) }
            }
            is SpeechIntent.Remove -> remove(intent.id).said()
            is SpeechIntent.Use -> use(intent.id, intent.way).said()
            is SpeechIntent.Test, is SpeechIntent.Hear, is SpeechIntent.Choices -> Unit
        }
    }

    private fun find(id: String): SpeechProvider = store.providers().firstOrNull { it.id == id } ?: refuse("There is no speech provider $id")

    private fun change(block: () -> SpeechProvider): Result<SpeechProvider> = synchronized(lock) { runCatching(block).also { publish() } }

    private fun publish() = stateHolder.update {
        it.copy(voice = it.voice.copy(providers = store.providers(), inUse = store.inUse(), speaker = store.speaker()))
    }

    private fun Result<SpeechProvider>.said() = onFailure { failure ->
        scope.launch { stateHolder.tell(VoiceNotices.providerRefused(failure.message)) }
    }

    private fun refuse(words: String): Nothing = throw IllegalArgumentException(words)
}
