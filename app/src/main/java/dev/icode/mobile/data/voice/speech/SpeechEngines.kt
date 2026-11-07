package dev.icode.mobile.data.voice.speech

import android.content.Context
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.CoroutineScope

/**
 * The engines for a provider (docs/speech-plan.md §4.2, docs/tts-plan.md §4.2): one branch per kind for
 * each way. A new provider is a kind, an adapter — or one already here that speaks its protocol — and
 * its branch; nothing else changes.
 */
internal class SpeechEngines(context: Context, scope: CoroutineScope, private val audio: SpeechAudio = SpeechMicrophone()) {
    private val android by lazy { AndroidSpeech(context.applicationContext) }
    private val androidVoice by lazy { AndroidVoice(context.applicationContext, scope) }

    /** One client for every provider's sockets and requests, made when one is first used. */
    private val http by lazy {
        HttpClient(OkHttp) {
            install(WebSockets)
            // Set per request, for the uploads: a socket waits on the user, and has no timeout of its own.
            install(HttpTimeout)
        }
    }

    /** [provider]'s engine; [key] is its key, decrypted for this hearing alone. */
    fun of(provider: SpeechProvider, key: String?): SpeechEngine = when (provider.kind) {
        SpeechProviderKind.Android -> android
        SpeechProviderKind.AssemblyAI -> AssemblyAiSpeech(requireNotNull(key), audio, http)
        SpeechProviderKind.OpenAI -> OpenAiSpeech(requireNotNull(key), audio, http)
        SpeechProviderKind.OpenAICompatible -> UploadSpeech(audio, OpenAiTranscription(http, requireNotNull(provider.address), key))
        SpeechProviderKind.Deepgram -> DeepgramSpeech(requireNotNull(key), audio, http)
        SpeechProviderKind.Google -> UploadSpeech(audio, GoogleRecognition(http, requireNotNull(key)))
    }

    /** [provider]'s voice; [key] is its key, decrypted for this speech session alone. */
    fun voiceOf(provider: SpeechProvider, key: String?): VoiceEngine = when (provider.kind) {
        SpeechProviderKind.Android -> androidVoice
        else -> throw IllegalArgumentException("${provider.name} cannot read aloud yet")
    }
}
