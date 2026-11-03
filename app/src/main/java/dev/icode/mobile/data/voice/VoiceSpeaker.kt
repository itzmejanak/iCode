package dev.icode.mobile.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dev.icode.mobile.domain.voice.SpokenText
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine

/** Reads text aloud (docs/phone-agent-plan.md §5.8). */
internal interface VoiceSpeaker {
    /** Returns once [text] is said; cancelled, it stops at once. */
    suspend fun speak(text: String)

    /** Lets its engine go. */
    fun release()
}

/** What the assistant's answers and its voice play as: the assistant speaking. */
internal val SPEECH: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANT)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Android's text-to-speech: the engine and voice the user chose in Android's settings. */
internal class AndroidSpeaker(context: Context) : VoiceSpeaker {
    private val ready = CompletableDeferred<Boolean>()
    private val engine = TextToSpeech(context.applicationContext) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
    private var said = 0L

    override suspend fun speak(text: String) {
        if (!ready.await()) throw IOException("This phone has no text-to-speech engine")
        val parts = SpokenText.parts(text, TextToSpeech.getMaxSpeechInputLength() - 1)
        if (parts.isEmpty()) return
        val utterance = "icode-${++said}"
        val last = "$utterance-${parts.lastIndex}"
        suspendCancellableCoroutine { done ->
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == last && done.isActive) done.resume(Unit)
                }

                @Deprecated("Android's own, still called before API 21's overload")
                override fun onError(utteranceId: String?) = failed(utteranceId)

                override fun onError(utteranceId: String?, errorCode: Int) = failed(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    if (utteranceId?.startsWith(utterance) == true && done.isActive) done.resume(Unit)
                }

                private fun failed(utteranceId: String?) {
                    if (utteranceId?.startsWith(utterance) == true && done.isActive) done.resumeWithException(IOException("Text-to-speech could not read the answer"))
                }
            })
            engine.setAudioAttributes(SPEECH)
            parts.forEachIndexed { index, part ->
                engine.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "$utterance-$index")
            }
            done.invokeOnCancellation { engine.stop() }
        }
    }

    override fun release() = engine.shutdown()
}
