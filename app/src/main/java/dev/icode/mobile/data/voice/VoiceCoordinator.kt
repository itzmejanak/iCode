package dev.icode.mobile.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.data.phone.PhoneForegroundService
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.Heard
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.SpokenTurn
import dev.icode.mobile.domain.voice.VoiceCue
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Voice in and out (docs/phone-agent-plan.md §5.8), one per process, with Android's own engines: the
 * microphone for the chat and the bubble, and the assistant read aloud as Spoken replies says — each
 * sentence as soon as it is written ([SpokenTurn]), from one speech session per turn. What is heard and
 * said is never logged; a failure logs its type only.
 */
internal class VoiceCoordinator(
    context: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val iCodeOnScreen = TerminalSessionManager.get(app).screenVisible
    private val reader = SpokenTurn()
    private val lock = Any()

    @Volatile private var listener: VoiceListener? = null

    @Volatile private var hearing = 0L

    /** The speech session: its sentences waiting to be said, the job saying them, and which one it is. */
    private var sentences: Channel<String>? = null
    private var speech: Job? = null
    private var session = 0L

    /** Android's engine, kept while it speaks and a while after, then let go. */
    private var speaker: VoiceSpeaker? = null
    private var idle: Job? = null

    private val voice: VoiceState get() = stateHolder.current.voice

    fun start() {
        scope.launch {
            forgetModels()
            update { it.copy(spoken = SpokenReplies.of(preferences.getString(REPLIES, null)), microphone = microphone()) }
        }
        scope.launch {
            var before = stateHolder.current.assistant
            stateHolder.snapshot.map { it.assistant }.distinctUntilChanged().collect { after ->
                reader.next(before, after, voice.spoken).forEach(::cue)
                before = after
            }
        }
        scope.launch { PhoneForegroundService.microphone.collect { away -> update { it.copy(away = away) } } }
    }

    fun handle(intent: VoiceIntent) {
        when (intent) {
            VoiceIntent.Refresh -> update { it.copy(microphone = microphone()) }
            is VoiceIntent.Listen -> listen(intent.from)
            VoiceIntent.FinishListening -> listener?.finish()
            VoiceIntent.CancelListening -> stopListening()
            VoiceIntent.Silence -> hush()
            VoiceIntent.Sample -> listOf(VoiceCue.Begin, VoiceCue.Say(SAMPLE), VoiceCue.End).forEach(::cue)
            is VoiceIntent.ChooseSpokenReplies -> choose(intent.spoken)
        }
    }

    private fun listen(from: VoiceFrom) {
        stopListening()
        hush()
        val id = ++hearing
        scope.launch {
            if (!microphone()) return@launch refused(id, from, "Allow the microphone in Settings › Assistant › Voice")
            if (!iCodeOnScreen.value && !voice.away) {
                // Android lets a background app take the microphone only through a foreground service
                // that already holds it: the bubble asks phone control's service to take it now.
                if (stateHolder.current.phone.bubble) {
                    PhoneForegroundService.start(app, microphone = true)
                    withTimeoutOrNull(MIC_WAIT_MILLIS) { PhoneForegroundService.microphone.first { it } }
                }
                if (!iCodeOnScreen.value && !voice.away) return@launch refused(id, from, "Open iCode once to use the microphone")
            }
            if (hearing != id) return@launch
            update { it.copy(heard = Heard(id, from, "", final = false)) }
            val next = VoiceListener(app)
            listener = next
            next.start(
                heard = { text, final ->
                    if (hearing == id) {
                        if (final) listener = null
                        update { it.copy(heard = Heard(id, from, text, final, problem = NOTHING_HEARD.takeIf { final && text.isBlank() })) }
                    }
                },
                problem = { words ->
                    if (hearing == id) {
                        listener = null
                        update { it.copy(heard = Heard(id, from, "", final = true, problem = words)) }
                    }
                },
            )
        }
    }

    /** [id]'s hearing ended before it began, and why. A later tap has its own [id] and is left alone. */
    private fun refused(id: Long, from: VoiceFrom, problem: String) {
        if (hearing == id) update { it.copy(heard = Heard(id, from, "", final = true, problem = problem)) }
    }

    private fun stopListening() {
        hearing++
        listener?.cancel()
        listener = null
        update { it.copy(heard = it.heard?.takeIf { heard -> heard.final }) }
    }

    /** Cues come from the assistant's changes and from intents: one at a time. */
    private fun cue(cue: VoiceCue) {
        synchronized(lock) {
            when (cue) {
                VoiceCue.Begin -> begin()
                is VoiceCue.Say -> (sentences ?: begin()).trySend(cue.text)
                VoiceCue.End -> sentences = sentences?.let { it.close(); null }
                VoiceCue.Hush -> hush()
            }
        }
    }

    /**
     * A speech session: what was said before stops, Android's engine gets ready now — while the model
     * still thinks — and each sentence queued is said in turn until the session ends.
     */
    private fun begin(): Channel<String> {
        val previous = speech
        hush()
        val queue = Channel<String>(Channel.UNLIMITED)
        val id = ++session
        sentences = queue
        speech = scope.launch {
            previous?.cancelAndJoin()
            idle?.cancel()
            try {
                engine().speak(queue) { playing -> if (session == id) update { it.copy(speaking = playing) } }
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (failed: Exception) {
                BackendLog.w(TAG, "Speaking failed (${failed.javaClass.simpleName})")
                stateHolder.notify(NoticeTone.HeadsUp, "The answer was not read aloud", failed.message)
            } finally {
                if (session == id) {
                    update { it.copy(speaking = false) }
                    idle = scope.launch {
                        delay(IDLE_MILLIS)
                        synchronized(lock) {
                            speaker?.release()
                            speaker = null
                        }
                    }
                }
            }
        }
        return queue
    }

    private fun engine(): VoiceSpeaker = synchronized(lock) { speaker ?: VoiceSpeaker(app).also { speaker = it } }

    private fun hush() {
        synchronized(lock) {
            sentences?.close()
            sentences = null
            speech?.cancel()
        }
    }

    private fun choose(spoken: SpokenReplies) {
        preferences.edit { putString(REPLIES, spoken.name) }
        if (spoken == SpokenReplies.Off) hush()
        update { it.copy(spoken = spoken) }
    }

    /**
     * Until 2026-11-04 iCode could download open-source voice models; the owner chose Android's engines
     * alone. What an earlier build left — the models' files and their settings — is removed.
     */
    private fun forgetModels() {
        File(app.noBackupFilesDir, OLD_MODELS).takeIf { it.exists() }?.deleteRecursively()
        if (OLD_KEYS.any(preferences::contains)) preferences.edit { OLD_KEYS.forEach(::remove) }
    }

    private fun microphone() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun update(change: (VoiceState) -> VoiceState) = stateHolder.update { it.copy(voice = change(it.voice)) }

    private companion object {
        const val TAG = "Voice"
        const val PREFERENCES = "voice"
        const val REPLIES = "replies"
        const val OLD_MODELS = "voice"
        val OLD_KEYS = listOf("listening", "listenModel", "speaking", "voice")
        const val SAMPLE = "Hello! This is how I read my answers aloud."
        const val NOTHING_HEARD = "Nothing was heard"
        const val IDLE_MILLIS = 120_000L
        const val MIC_WAIT_MILLIS = 1_500L
    }
}
