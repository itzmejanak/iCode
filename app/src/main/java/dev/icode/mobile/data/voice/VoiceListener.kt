package dev.icode.mobile.data.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Hears the user once (docs/phone-agent-plan.md §5.8): the words as they come, then the last ones, final. */
internal interface VoiceListener {
    /** [heard] gets the words so far, final once the user stopped talking; [problem] ends it instead. */
    fun start(heard: (text: String, final: Boolean) -> Unit, problem: (String) -> Unit)

    /** The user is done: what was heard so far becomes final. */
    fun finish()

    /** Stops at once, reporting nothing more. */
    fun cancel()
}

/**
 * Android's own recognizer: on the phone itself where it can (API 31), else the user's recognition
 * service; it ends by itself when the user stops talking. Used on the main thread, as Android requires.
 */
internal class AndroidListener(private val context: Context) : VoiceListener {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    override fun start(heard: (String, Boolean) -> Unit, problem: (String) -> Unit) {
        main.post { begin(onDevice = onDeviceAvailable(), heard, problem) }
    }

    override fun finish() {
        main.post { recognizer?.stopListening() }
    }

    override fun cancel() {
        main.post {
            recognizer?.cancel()
            release()
        }
    }

    private fun onDeviceAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    private fun begin(onDevice: Boolean, heard: (String, Boolean) -> Unit, problem: (String) -> Unit) {
        val made = when {
            onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> return problem(NO_RECOGNIZER)
        }
        recognizer = made
        var said = ""
        made.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                words(partialResults)?.let { said = it; heard(it, false) }
            }

            override fun onResults(results: Bundle?) {
                release()
                heard(words(results) ?: said, true)
            }

            override fun onError(error: Int) {
                release()
                when {
                    // The phone's own recognizer lacks this language: the user's recognition service may have it.
                    onDevice && error in LANGUAGE_MISSING -> begin(onDevice = false, heard, problem)
                    error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> heard(said, true)
                    else -> problem(failure(error))
                }
            }

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        made.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
    }

    private fun release() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun words(results: Bundle?): String? = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val NO_RECOGNIZER = "This phone has no speech recognition: choose a model on this phone in Settings › Assistant › Voice"

        /** ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE, API 31. */
        val LANGUAGE_MISSING = setOf(12, 13)

        fun failure(error: Int): String = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "iCode may not use the microphone"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs the network, and it is not there"
            SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be read"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "Speech recognition is busy: try again"
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "The speech service did not answer: try again"
            else -> "Speech recognition failed: try again"
        }
    }
}
