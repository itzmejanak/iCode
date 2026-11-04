package dev.icode.mobile.domain

import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.VoiceFrom

/** Voice in and out (docs/phone-agent-plan.md §5.8): the microphone, the spoken answer, and when answers are read aloud. */
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

    /** Android's voice says a sample, so the user hears it before an answer. */
    data object Sample : VoiceIntent

    /** An answer in the chat read aloud on request, from its ▶: [key] names it while it is read. */
    data class Read(val key: String, val text: String) : VoiceIntent

    /** When answers are read aloud, as the user chose. */
    data class ChooseSpokenReplies(val spoken: SpokenReplies) : VoiceIntent
}
