package dev.icode.mobile.data.voice.speech

import android.content.Context
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind

/**
 * The engine for a provider (docs/speech-plan.md §4.2): one branch per kind. A new provider is a kind,
 * an adapter — or one already here that speaks its protocol — and its branch; nothing else changes.
 */
internal class SpeechEngines(context: Context) {
    private val android by lazy { AndroidSpeech(context.applicationContext) }

    /** [provider]'s engine; [key] is its key, decrypted for this hearing alone. */
    @Suppress("UNUSED_PARAMETER")
    fun of(provider: SpeechProvider, key: String?): SpeechEngine = when (provider.kind) {
        SpeechProviderKind.Android -> android
    }
}
