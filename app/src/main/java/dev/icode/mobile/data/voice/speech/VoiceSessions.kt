package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch

/** The provider a speech session uses, its engine, and what it is asked. */
internal class VoiceUse(val provider: SpeechProvider, val engine: VoiceEngine, val request: VoiceRequest)

/**
 * One speech session at a time (docs/tts-plan.md §4), whichever engine says it: a turn's sentences, the
 * chat's ▶, or Play Sample. A session that begins stops the one before it; its engine gets ready at
 * once — while the model still thinks — and says each sentence in turn until the session ends. What is
 * said is never logged; a failure logs its type only.
 */
internal class VoiceSessions(
    private val scope: CoroutineScope,
    /** The provider [id] names — null: the one that speaks — with its engine; or why there is none (its key missing, for one). */
    private val use: (id: String?) -> Result<VoiceUse>,
    /** A session began; [reading] is the answer it reads on request, by its key. */
    private val began: (reading: String?) -> Unit,
    /** Whether the session sounds now. */
    private val playing: (Boolean) -> Unit,
    /** The session ended: why it said nothing more, in the provider's words; or, null, it said it all, or was hushed. */
    private val ended: (failure: String?) -> Unit,
) {
    private val lock = Any()
    private var sentences: Channel<String>? = null
    private var job: Job? = null
    private var session = 0L

    /** A new session, with [provider] — null: the one that speaks; [reading]: the answer it reads on request, by its key. */
    fun begin(reading: String? = null, provider: String? = null) {
        synchronized(lock) { beginLocked(reading, provider) }
    }

    /** Says [text] in this session, or in a new one. */
    fun say(text: String) {
        synchronized(lock) { (sentences ?: beginLocked(null, null)).trySend(text) }
    }

    /** Nothing more is said: the session ends once what it has is said. */
    fun end() {
        synchronized(lock) {
            sentences?.close()
            sentences = null
        }
    }

    /** Silence, at once. */
    fun hush() {
        synchronized(lock) { hushLocked() }
    }

    private fun beginLocked(reading: String?, provider: String?): Channel<String> {
        val previous = job
        hushLocked()
        val queue = Channel<String>(Channel.UNLIMITED)
        val id = ++session
        sentences = queue
        began(reading)
        job = scope.launch {
            previous?.cancelAndJoin()
            var failure: String? = null
            try {
                failure = speak(id, provider, queue)
            } finally {
                synchronized(lock) { if (session == id) ended(failure) }
            }
        }
        return queue
    }

    /** Session [id] says [queue] with [provider]: why it failed, in the provider's words; or null. */
    private suspend fun speak(id: Long, provider: String?, queue: ReceiveChannel<String>): String? {
        val chosen = use(provider).getOrElse { return it.message }
        return try {
            chosen.engine.speak(queue, chosen.request) { sounds -> synchronized(lock) { if (session == id) playing(sounds) } }
            null
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: Exception) {
            BackendLog.w(TAG, "Speaking failed (${failed.javaClass.simpleName})")
            val known = failed as? VoiceFailed
            SpeechRules.words(known?.failure ?: SpeechFailure.Failed, chosen.provider, known?.detail, SpeechWay.Speaking)
        }
    }

    private fun hushLocked() {
        sentences?.close()
        sentences = null
        job?.cancel()
    }

    private companion object {
        const val TAG = "Voice"
    }
}
