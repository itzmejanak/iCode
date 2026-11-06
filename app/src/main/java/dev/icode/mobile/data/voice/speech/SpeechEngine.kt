package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow

/**
 * One way to hear the user (docs/speech-plan.md §4.1): every provider's adapter is one, and the voice
 * coordinator knows none of them. What is heard is never logged.
 */
internal interface SpeechEngine {
    /**
     * Hears once: the words as they come, then the final ones — or a failure — and the flow ends.
     * [done] completes when the user is done talking: what was heard so far becomes final. Cancelling
     * the collection stops the hearing and keeps nothing.
     */
    fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent>
}

/** What every engine is asked, the same for all: each takes what its own API takes from it. */
internal data class SpeechRequest(
    /** A BCP-47 tag (`en-US`); null: the language the phone speaks. */
    val language: String?,
    /** The model to ask for; null where the provider has none to choose. */
    val model: String? = null,
)

/** What every engine answers, the same for all. */
internal sealed interface SpeechEvent {
    /** The words so far; [final] once the user stopped talking, and the hearing is over. */
    data class Words(val text: String, val final: Boolean) : SpeechEvent

    /** The hearing ended without words: why, and the provider's own words where it gave some. */
    data class Failed(val failure: SpeechFailure, val detail: String? = null) : SpeechEvent
}
