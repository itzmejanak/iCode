package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.Heard
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.VoiceFrom
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/** The provider a hearing uses, its engine, and what it is asked. */
internal class SpeechUse(val provider: SpeechProvider, val engine: SpeechEngine, val request: SpeechRequest)

/**
 * One hearing at a time (docs/speech-plan.md §4.1), whichever engine hears it: its words go to
 * [heard] as they come, final once the user stopped talking, or why nothing was heard. A tap that
 * starts another leaves an earlier one's words alone.
 */
internal class SpeechHearings(
    private val scope: CoroutineScope,
    /** Why a hearing cannot start now, or null: asked before any engine starts. */
    private val refusal: suspend () -> String?,
    /** The provider [id] names — null: the one in use — with its engine; or why there is none (its key missing, for one). */
    private val use: (id: String?) -> Result<SpeechUse>,
    /** Changes what was heard; the change is given the last one. */
    private val heard: ((Heard?) -> Heard?) -> Unit,
    /** A hearing with this provider ended: why it failed, in its words; or, null, it heard. */
    private val ended: (SpeechProvider, String?) -> Unit = { _, _ -> },
) {
    private val lock = Any()
    private var hearing = 0L
    private var job: Job? = null
    private var done: CompletableDeferred<Unit>? = null

    /** Listens for [from] with [provider] — null: the one in use. Whatever was heard before and not final goes. */
    fun listen(from: VoiceFrom, provider: String? = null) {
        val id = synchronized(lock) {
            stopLocked()
            ++hearing
        }
        heard { it?.takeIf { heard -> heard.final } }
        val started = scope.launch {
            refusal()?.let {
                end(id, Heard(id, from, "", final = true, problem = it))
                return@launch
            }
            val chosen = use(provider).getOrElse {
                end(id, Heard(id, from, "", final = true, problem = it.message))
                return@launch
            }
            val finish = CompletableDeferred<Unit>()
            synchronized(lock) {
                if (hearing != id) return@launch
                done = finish
            }
            if (!end(id, Heard(id, from, "", final = false))) return@launch
            var said = ""
            var over = false
            var failure: String? = null
            // The final words or a failure end the hearing, and stop the engine.
            chosen.engine.hear(chosen.request, finish).transformWhile { event ->
                emit(event)
                event is SpeechEvent.Words && !event.final
            }.collect { event ->
                val next = when (event) {
                    is SpeechEvent.Words -> heardOf(id, from, event.text, event.final, chosen.provider).also { said = event.text; over = event.final }
                    is SpeechEvent.Failed -> Heard(id, from, "", final = true, problem = SpeechRules.words(event.failure, chosen.provider, event.detail))
                        .also { failure = it.problem; over = true }
                }
                if (!end(id, next)) throw Stopped()
            }
            // An engine that ended without its final words: what it heard is final.
            if (!over && !end(id, heardOf(id, from, said, final = true, chosen.provider))) return@launch
            synchronized(lock) { if (hearing == id) ended(chosen.provider, failure) }
        }
        synchronized(lock) { if (hearing == id) job = started }
    }

    /** The user is done talking: what was heard so far becomes final. */
    fun finish() {
        synchronized(lock) { done }?.complete(Unit)
    }

    /** Listening stops, and nothing heard is kept. */
    fun stop() {
        synchronized(lock) { stopLocked() }
        heard { it?.takeIf { heard -> heard.final } }
    }

    private fun stopLocked() {
        hearing++
        job?.cancel()
        job = null
        done = null
    }

    /** [next] shown, while [id] is still the hearing: false once another took its place. */
    private fun end(id: Long, next: Heard): Boolean = synchronized(lock) {
        if (hearing != id) return false
        heard { next }
        true
    }

    private fun heardOf(id: Long, from: VoiceFrom, text: String, final: Boolean, provider: SpeechProvider) =
        Heard(id, from, text, final, problem = SpeechRules.words(SpeechFailure.NothingHeard, provider).takeIf { final && text.isBlank() })

    /** A hearing another took the place of: its engine stops at once. */
    private class Stopped : CancellationException("Another hearing started")
}
