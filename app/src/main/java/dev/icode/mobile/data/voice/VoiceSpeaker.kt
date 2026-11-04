package dev.icode.mobile.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.icode.mobile.domain.voice.SpokenText
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Android's text-to-speech, reading the assistant aloud (docs/phone-agent-plan.md §5.8) with the engine
 * and voice the user chose in Android's settings: each sentence queued the moment it comes, so the first
 * is heard while the model still writes the rest.
 */
internal class VoiceSpeaker(context: Context) {
    private val ready = CompletableDeferred<Boolean>()
    private val engine = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    private var said = 0L

    /**
     * Says each of [sentences] as it comes, and returns once the channel is closed and all of it said;
     * [playing] follows whether a sentence sounds now. Cancelled, it stops at once.
     */
    suspend fun speak(sentences: ReceiveChannel<String>, playing: (Boolean) -> Unit) {
        if (!ready.await()) throw IOException("This phone has no text-to-speech engine")
        val waiting = AtomicInteger()
        val failed = AtomicBoolean()
        val drained = Channel<Unit>(Channel.CONFLATED)
        fun spoken() {
            if (waiting.decrementAndGet() == 0) {
                playing(false)
                drained.trySend(Unit)
            }
        }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
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
        engine.setAudioAttributes(SPEECH)
        try {
            for (sentence in sentences) {
                SpokenText.parts(sentence, TextToSpeech.getMaxSpeechInputLength() - 1).forEach { part ->
                    waiting.incrementAndGet()
                    if (engine.speak(part, TextToSpeech.QUEUE_ADD, null, "icode-${++said}") != TextToSpeech.SUCCESS) {
                        failed.set(true)
                        spoken()
                    }
                }
            }
            while (waiting.get() > 0) drained.receive()
        } catch (stopped: CancellationException) {
            engine.stop()
            playing(false)
            throw stopped
        }
        if (failed.get()) throw IOException("Text-to-speech could not read the answer")
    }

    fun release() = engine.shutdown()

    private companion object {
        /** The assistant speaking. */
        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    }
}
