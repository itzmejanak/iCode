package dev.icode.mobile.data.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.icode.mobile.domain.voice.SpokenReplies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Voice (docs/phone-agent-plan.md §5.8), with Android's own engines: its speech recognition listens,
 * its text-to-speech reads answers aloud. One per process, shared by the chat page and the bubble.
 * The on-phone models of §5.8 come in a later cycle.
 */
internal object Voice {
    private val main = Handler(Looper.getMainLooper())
    private val mutableReplies = MutableStateFlow(SpokenReplies.DEFAULT)
    private val mutableListening = MutableStateFlow(false)
    private var loaded = false
    private var speaker: TextToSpeech? = null
    private var speakerReady = false
    private var pending: String? = null
    private var recognizer: SpeechRecognizer? = null

    /** The turn now running was sent from the bubble: its answer is spoken under [SpokenReplies.InBubble]. */
    @Volatile var bubbleTurn = false

    /** The turn now running was stopped by the user: its answer is not spoken. */
    @Volatile var stopped = false

    val replies: StateFlow<SpokenReplies> = mutableReplies.asStateFlow()
    val listening: StateFlow<Boolean> = mutableListening.asStateFlow()

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        mutableReplies.value = SpokenReplies.of(prefs(context).getString(REPLIES, null))
    }

    fun setReplies(context: Context, value: SpokenReplies) {
        prefs(context).edit { putString(REPLIES, value.name) }
        mutableReplies.value = value
        if (value == SpokenReplies.Off) stopSpeaking()
    }

    fun canListen(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Listens once; what is heard goes to [onHeard], why nothing was to [onProblem]. */
    fun listen(context: Context, onHeard: (String) -> Unit, onProblem: (String) -> Unit) = main.post {
        stopSpeaking()
        if (!hasMicrophone(context)) return@post onProblem("Allow the microphone in Settings › Assistant › Voice")
        if (!canListen(context)) return@post onProblem("No speech recognition on this phone")
        recognizer?.destroy()
        val next = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = next
        next.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                finish(next)
                if (heard.isNotEmpty()) onHeard(heard) else onProblem("Nothing was heard")
            }

            override fun onError(error: Int) {
                finish(next)
                onProblem(problem(error))
            }

            override fun onReadyForSpeech(params: Bundle?) { mutableListening.value = true }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        mutableListening.value = true
        next.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName),
        )
    }

    fun stopListening() = main.post {
        recognizer?.let(::finish)
    }

    /** Reads [text] aloud with the phone's text-to-speech engine and voice. */
    fun speak(context: Context, text: String) = main.post {
        if (text.isBlank()) return@post
        val engine = speaker
        if (engine == null) {
            pending = text
            speaker = TextToSpeech(context.applicationContext) { status ->
                main.post {
                    speakerReady = status == TextToSpeech.SUCCESS
                    pending?.takeIf { speakerReady }?.let { say(it) }
                    pending = null
                }
            }
        } else if (speakerReady) {
            say(text)
        } else {
            pending = text
        }
    }

    fun stopSpeaking() = main.post {
        pending = null
        speaker?.takeIf { speakerReady }?.stop()
    }

    private fun say(text: String) {
        speaker?.speak(text.take(TextToSpeech.getMaxSpeechInputLength() - 1), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE)
    }

    private fun finish(done: SpeechRecognizer) {
        done.destroy()
        if (recognizer === done) recognizer = null
        mutableListening.value = false
    }

    private fun problem(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nothing was heard"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Allow the microphone in Settings › Assistant › Voice"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs the network on this phone"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy; try again"
        SpeechRecognizer.ERROR_AUDIO -> "The microphone could not be opened; open iCode once to use it"
        else -> "Speech recognition stopped (error $error)"
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private const val PREFERENCES = "voice"
    private const val REPLIES = "replies"
    private const val UTTERANCE = "icode-reply"
}
