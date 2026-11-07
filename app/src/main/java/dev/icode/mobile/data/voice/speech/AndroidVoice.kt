package dev.icode.mobile.data.voice.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpokenText
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android's text-to-speech (docs/tts-plan.md §1.1), with the engine and voice the user chose in
 * Android's settings: each sentence queued the moment it comes, so the first is heard while the model
 * still writes the rest. The engine is made when a session begins — while the model still thinks — and
 * let go once none has spoken for a while.
 */
internal class AndroidVoice(context: Context, private val scope: CoroutineScope) : VoiceEngine {
    private val app = context.applicationContext
    private val lock = Any()
    private var engine: Engine? = null
    private var idle: Job? = null
    private var said = 0L

    /** Android's voices are chosen in Android's settings: [request] asks nothing of them. */
    override suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit) {
        val engine = take()
        try {
            speak(engine, sentences, playing)
        } finally {
            letGo()
        }
    }

    private suspend fun speak(engine: Engine, sentences: ReceiveChannel<String>, playing: (Boolean) -> Unit) {
        if (!engine.ready.await()) throw ProviderFailed(SpeechFailure.Unavailable)
        val speech = engine.speech
        val waiting = AtomicInteger()
        val failed = AtomicBoolean()
        val drained = Channel<Unit>(Channel.CONFLATED)
        fun spoken() {
            if (waiting.decrementAndGet() == 0) {
                playing(false)
                drained.trySend(Unit)
            }
        }
        speech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = playing(true)

            override fun onDone(utteranceId: String?) = spoken()

            @Deprecated("Android's own, still called before API 21's overload")
            override fun onError(utteranceId: String?) {
                failed.set(true)
                spoken()
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                failed.set(true)
                spoken()
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
        })
        speech.setAudioAttributes(VoicePlayer.SPEECH)
        try {
            for (sentence in sentences) {
                SpokenText.parts(sentence, TextToSpeech.getMaxSpeechInputLength() - 1).forEach { part ->
                    waiting.incrementAndGet()
                    if (speech.speak(part, TextToSpeech.QUEUE_ADD, null, "icode-${++said}") != TextToSpeech.SUCCESS) {
                        failed.set(true)
                        spoken()
                    }
                }
            }
            while (waiting.get() > 0) drained.receive()
        } catch (stopped: CancellationException) {
            speech.stop()
            playing(false)
            throw stopped
        }
        if (failed.get()) throw ProviderFailed(SpeechFailure.Failed)
    }

    private fun take(): Engine = synchronized(lock) {
        idle?.cancel()
        idle = null
        engine ?: Engine(app).also { engine = it }
    }

    /** No session has spoken for [IDLE_MILLIS]: the engine is let go. Cancelled under the lock, a release never ends a session's engine. */
    private fun letGo() = synchronized(lock) {
        idle = scope.launch {
            delay(IDLE_MILLIS)
            synchronized(lock) {
                if (!isActive) return@synchronized
                engine?.speech?.shutdown()
                engine = null
                idle = null
            }
        }
    }

    /** Android's engine, and whether it started. */
    private class Engine(context: Context) {
        val ready = CompletableDeferred<Boolean>()
        val speech = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    }

    private companion object {
        const val IDLE_MILLIS = 120_000L
    }
}
