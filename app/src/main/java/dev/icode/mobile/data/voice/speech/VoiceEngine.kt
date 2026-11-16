package dev.icode.mobile.data.voice.speech

import kotlinx.coroutines.channels.ReceiveChannel

/**
 * One way to read the answers aloud (docs/voice-and-phone-control.md): every provider's adapter is one, and the
 * voice coordinator knows none of them. What is said is never logged.
 */
internal interface VoiceEngine {
    /**
     * Says each of [sentences] as it comes, and returns once the channel is closed and all of it is
     * said; [playing] follows whether it sounds now. Cancelled, it stops at once. Throws [ProviderFailed]
     * when it cannot.
     */
    suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit)
}

/** What every engine is asked, the same for all: each takes what its own API takes from it. */
internal data class VoiceRequest(
    /** The voice by its provider's name for it; null: the provider's default. */
    val voice: String? = null,
    /** The model to ask for; null where the provider has none to choose. */
    val model: String? = null,
    /** A BCP-47 tag (`en-US`); null: the language the phone speaks. */
    val language: String? = null,
    /** 1 is the voice's own pace; null where the provider takes none. */
    val speed: Float? = null,
)
