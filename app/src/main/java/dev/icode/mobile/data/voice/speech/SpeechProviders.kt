package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The speech providers' one owner (docs/speech-plan.md §1, §4.5): the pages' intents and the agent's
 * tools call the same operations, one at a time, and what either changes shows at once. A refusal is
 * the user's notice, and the model's words.
 */
internal class SpeechProviders(
    private val store: SpeechProviderStore,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val lock = Any()

    fun start() = publish()

    /** Every provider, Android's first; none carries its key. */
    fun list(): List<SpeechProvider> = store.providers()

    /** The provider that listens, by its ID. */
    fun inUse(): String = store.inUse()

    /** Adds [provider] (ID "") or changes it; [key] null keeps the saved one. Android's changes only its language. */
    fun save(provider: SpeechProvider, key: String?): Result<SpeechProvider> = change {
        val all = store.providers()
        val saved = all.firstOrNull { it.id == provider.id }
        if (provider.id.isNotEmpty() && saved == null) refuse("There is no speech provider ${provider.id}")
        val next = if (provider.id == SpeechProvider.ANDROID) SpeechProvider.Android.copy(language = provider.language) else provider
        val keySaved = saved?.hasKey == true && saved.kind == next.kind
        SpeechRules.problem(next, all, keyGiven = !key.isNullOrBlank(), keySaved = keySaved)?.let(::refuse)
        store.save(next, key)
    }

    fun remove(id: String): Result<SpeechProvider> = change {
        if (id == SpeechProvider.ANDROID) refuse("Android's recognizer cannot be removed")
        val saved = find(id)
        store.delete(id)
        saved
    }

    /** [id] listens from now on. */
    fun use(id: String): Result<SpeechProvider> = change {
        val provider = find(id)
        store.use(id)
        provider
    }

    /** [id]'s provider — null: the one that listens — and its key, decrypted for a hearing; or why it cannot hear. */
    fun connection(id: String?): Result<Pair<SpeechProvider, String?>> = runCatching {
        val provider = find(id ?: store.inUse())
        val key = store.key(provider.id)
        if (provider.kind.needsKey && key == null) refuse(SpeechRules.words(SpeechFailure.NoKey, provider))
        provider to key
    }

    /** [id]'s last hearing failed with [words]; or, null, it heard. Kept while iCode runs, for its page and the agent. */
    fun heard(id: String, words: String?) = stateHolder.update { snapshot ->
        val failures = snapshot.voice.failures
        snapshot.copy(voice = snapshot.voice.copy(failures = if (words == null) failures - id else failures + (id to words)))
    }

    /** The pages' operations; a test is the voice coordinator's, which hears it. */
    fun handle(intent: SpeechIntent) {
        when (intent) {
            is SpeechIntent.Edit -> stateHolder.update { it.copy(voice = it.voice.copy(editing = intent.provider)) }
            // Saved, its page shows it as kept: a new one now has its ID.
            is SpeechIntent.Save -> save(intent.provider, intent.key).said().onSuccess { kept ->
                stateHolder.update { it.copy(voice = it.voice.copy(editing = kept.takeIf { _ -> it.voice.editing?.id == intent.provider.id } ?: it.voice.editing)) }
            }
            is SpeechIntent.Remove -> remove(intent.id).said()
            is SpeechIntent.Use -> use(intent.id).said()
            is SpeechIntent.Test -> Unit
        }
    }

    private fun find(id: String): SpeechProvider = store.providers().firstOrNull { it.id == id } ?: refuse("There is no speech provider $id")

    private fun change(block: () -> SpeechProvider): Result<SpeechProvider> = synchronized(lock) { runCatching(block).also { publish() } }

    private fun publish() = stateHolder.update { it.copy(voice = it.voice.copy(providers = store.providers(), inUse = store.inUse())) }

    private fun Result<SpeechProvider>.said() = onFailure { failure ->
        scope.launch { stateHolder.notify(NoticeTone.HeadsUp, failure.message ?: "The speech provider was not saved") }
    }

    private fun refuse(words: String): Nothing = throw IllegalArgumentException(words)
}
