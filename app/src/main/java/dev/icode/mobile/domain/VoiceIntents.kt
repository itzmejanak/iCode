package dev.icode.mobile.domain

import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceSettings

/** Voice in and out (docs/phone-agent-plan.md §5.8): the microphone, the spoken answer, the models and the settings. */
sealed interface VoiceIntent : ICodeIntent {
    /** Whether the microphone may be used, read again: Android's prompt answered, or iCode back in front. */
    data object Refresh : VoiceIntent

    /** The microphone tapped in [from]: listening starts, and what is heard goes back there. */
    data class Listen(val from: VoiceFrom) : VoiceIntent

    /** Tapped again: the user is done talking, and what was heard is final. */
    data object FinishListening : VoiceIntent

    /** Listening stops, and nothing heard is kept. */
    data object CancelListening : VoiceIntent

    /** The spoken answer stops: a tap on the bubble, or Stop. */
    data object Silence : VoiceIntent

    /** The chosen voice says a sample, so the user hears it before an answer. */
    data object Sample : VoiceIntent

    /** The voice settings as the user set them; a model they need is downloaded only by [Download]. */
    data class Choose(val settings: VoiceSettings) : VoiceIntent

    /** A model of [dev.icode.mobile.domain.voice.VoiceCatalog], by its id, downloaded once the user saw its size. */
    data class Download(val model: String) : VoiceIntent

    /** A model's files removed from the phone. */
    data class Remove(val model: String) : VoiceIntent
}
