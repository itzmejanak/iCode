package dev.icode.mobile.data.voice.speech

import android.content.Context
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets

/**
 * The engine for a provider (docs/speech-plan.md §4.2): one branch per kind. A new provider is a kind,
 * an adapter — or one already here that speaks its protocol — and its branch; nothing else changes.
 */
internal class SpeechEngines(context: Context, private val audio: SpeechAudio = SpeechMicrophone()) {
    private val android by lazy { AndroidSpeech(context.applicationContext) }

    /** One client for every provider's sockets and requests, made when one is first used. */
    private val http by lazy { HttpClient(OkHttp) { install(WebSockets) } }

    /** [provider]'s engine; [key] is its key, decrypted for this hearing alone. */
    fun of(provider: SpeechProvider, key: String?): SpeechEngine = when (provider.kind) {
        SpeechProviderKind.Android -> android
        SpeechProviderKind.AssemblyAI -> AssemblyAiSpeech(requireNotNull(key), audio, http)
    }
}
