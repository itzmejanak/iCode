package dev.icode.mobile.domain

import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechWay
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

    /** The provider that reads aloud says a sample, so the user hears it before an answer. */
    data object Sample : VoiceIntent

    /** An answer in the chat read aloud on request, from its ▶: [key] names it while it is read. */
    data class Read(val key: String, val text: String) : VoiceIntent

    /** When answers are read aloud, as the user chose. */
    data class ChooseSpokenReplies(val spoken: SpokenReplies) : VoiceIntent
}

/** The speech providers (docs/speech-plan.md §5, docs/tts-plan.md §5): the pages' operations, which the agent's tools share. */
sealed interface SpeechIntent : VoiceIntent {
    /** [provider]'s page opens: a new one has the ID "". */
    data class Edit(val provider: SpeechProvider) : SpeechIntent

    /** Adds [provider] (ID "") or changes it; [key] null keeps the one saved. */
    data class Save(val provider: SpeechProvider, val key: String?) : SpeechIntent

    data class Remove(val id: String) : SpeechIntent

    /** [id] serves [way] from now on. */
    data class Use(val id: String, val way: SpeechWay) : SpeechIntent

    /** Listens once with [id], from its page: what is heard shows there, and is sent nowhere. */
    data class Test(val id: String) : SpeechIntent

    /** [id] says a sample, as saved, from its page. */
    data class Hear(val id: String) : SpeechIntent

    /** [id]'s voices, and its models where it lists them, asked of its API for its page. */
    data class Choices(val id: String) : SpeechIntent
}
