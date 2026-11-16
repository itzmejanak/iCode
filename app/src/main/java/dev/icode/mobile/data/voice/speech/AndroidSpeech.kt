package dev.icode.mobile.data.voice.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dev.icode.mobile.domain.voice.SpeechFailure
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Android's own recognizer (docs/voice-and-phone-control.md): on the phone itself where it can (API 31),
 * else the user's recognition service; the words as they come, then the last ones, final, once the user
 * stops talking. It takes the microphone itself, on the main thread, as Android requires.
 */
internal class AndroidSpeech(private val context: Context) : SpeechEngine {
    override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> = callbackFlow {
        var recognizer: SpeechRecognizer? = null
        fun release() {
            recognizer?.destroy()
            recognizer = null
        }

        fun begin(onDevice: Boolean) {
            val made = when {
                onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
                else -> {
                    trySend(SpeechEvent.Failed(SpeechFailure.Unavailable))
                    close()
                    return
                }
            }
            recognizer = made
            var said = ""
            made.setRecognitionListener(object : RecognitionListener {
                override fun onPartialResults(partialResults: Bundle?) {
                    words(partialResults)?.let {
                        said = it
                        trySend(SpeechEvent.Words(it, final = false))
                    }
                }

                override fun onResults(results: Bundle?) {
                    release()
                    trySend(SpeechEvent.Words(words(results) ?: said, final = true))
                    close()
                }

                override fun onError(error: Int) {
                    release()
                    when {
                        // The phone's own recognizer lacks this language: the user's recognition service may have it.
                        onDevice && error in LANGUAGE_MISSING -> return begin(onDevice = false)
                        error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            trySend(SpeechEvent.Words(said, final = true))
                        else -> trySend(SpeechEvent.Failed(failure(error)))
                    }
                    close()
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
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    .apply { request.language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) } },
            )
        }

        begin(onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
        // The user is done: what was heard so far becomes final.
        launch {
            done.await()
            recognizer?.stopListening()
        }
        awaitClose {
            recognizer?.cancel()
            release()
        }
    }.flowOn(Dispatchers.Main)

    private fun words(results: Bundle?): String? =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        /** ERROR_LANGUAGE_NOT_SUPPORTED and ERROR_LANGUAGE_UNAVAILABLE, API 31. */
        val LANGUAGE_MISSING = setOf(12, 13)

        fun failure(error: Int): SpeechFailure = when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.NotAllowed
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechFailure.Unreachable
            SpeechRecognizer.ERROR_AUDIO -> SpeechFailure.Microphone
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SpeechFailure.Busy
            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechFailure.Server
            in LANGUAGE_MISSING -> SpeechFailure.Language
            else -> SpeechFailure.Failed
        }
    }
}
