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
import dev.icode.mobile.domain.voice.ModelState
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.SpokenText
import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceCue
import dev.icode.mobile.domain.voice.VoiceEngine
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceSettings
import dev.icode.mobile.domain.voice.VoiceState
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Voice in and out (docs/phone-agent-plan.md §5.8), one per process: the microphone for the chat and the
 * bubble, the assistant's answers and questions read aloud as Spoken replies says, and the models on this
 * phone. What is heard and said is never logged; a failure logs its type only.
 */
internal class VoiceCoordinator(
    context: Context,
    private val stateHolder: ICodeStateHolder,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val models = VoiceModels(File(app.noBackupFilesDir, FOLDER))
    private val iCodeOnScreen = TerminalSessionManager.get(app).screenVisible
    private val downloads = ConcurrentHashMap<String, Job>()
    private val speaking = Mutex()

    @Volatile private var listener: VoiceListener? = null

    @Volatile private var hearing = 0L

    @Volatile private var speech: Job? = null

    /** The engine that spoke last, by its key: "android", or a voice's id; let go after a while unused. */
    private var speaker: Pair<String, VoiceSpeaker>? = null
    private var idle: Job? = null

    private val voice: VoiceState get() = stateHolder.current.voice

    fun start() {
        scope.launch {
            val settings = load()
            update { it.copy(settings = settings, models = models.states(), microphone = microphone()) }
        }
        scope.launch {
            var before = stateHolder.current.assistant
            stateHolder.snapshot.map { it.assistant }.distinctUntilChanged().collect { after ->
                SpokenText.cue(before, after, voice.settings.spoken)?.let(::cue)
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
            VoiceIntent.Sample -> say(SAMPLE)
            is VoiceIntent.Choose -> choose(intent.settings)
            is VoiceIntent.Download -> download(intent.model)
            is VoiceIntent.Remove -> remove(intent.model)
        }
    }

    private fun listen(from: VoiceFrom) {
        stopListening()
        hush()
        val settings = voice.settings
        val problem = when {
            !microphone() -> "Allow the microphone in Settings › Assistant › Voice"
            !iCodeOnScreen.value && !voice.away -> "Open iCode once to use the microphone"
            settings.listenModels.any { !models.ready(it) } -> "Download ${VoiceCatalog.listener(settings.listenModel).name} in Settings › Assistant › Voice"
            else -> null
        }
        val id = ++hearing
        if (problem != null) return update { it.copy(heard = Heard(id, from, "", final = true, problem = problem)) }
        update { it.copy(heard = Heard(id, from, "", final = false)) }
        val next = if (settings.listening == VoiceEngine.Android) AndroidListener(app)
        else ModelListener(models, VoiceCatalog.listener(settings.listenModel), scope)
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

    private fun stopListening() {
        hearing++
        listener?.cancel()
        listener = null
        update { it.copy(heard = it.heard?.takeIf { heard -> heard.final }) }
    }

    private fun cue(cue: VoiceCue) = when (cue) {
        VoiceCue.Hush -> hush()
        is VoiceCue.Say -> say(cue.text)
    }

    private fun hush() {
        speech?.cancel()
    }

    /** Says [text] with the chosen engine, after what was being said stops. */
    private fun say(text: String) {
        val previous = speech
        speech = scope.launch {
            previous?.cancelAndJoin()
            speaking.withLock {
                val settings = voice.settings
                if (settings.speakModels.any { !models.ready(it) }) return@withLock stateHolder.notify(NoticeTone.HeadsUp, "The answer was not read aloud", VOICE_MISSING)
                idle?.cancel()
                update { it.copy(speaking = true) }
                try {
                    speaker(settings).speak(text)
                } catch (stopped: CancellationException) {
                    throw stopped
                } catch (failed: Exception) {
                    BackendLog.w(TAG, "Speaking failed (${failed.javaClass.simpleName})")
                    stateHolder.notify(NoticeTone.HeadsUp, "The answer was not read aloud", failed.message)
                } finally {
                    update { it.copy(speaking = false) }
                    idle = scope.launch {
                        delay(IDLE_MILLIS)
                        speaking.withLock { release() }
                    }
                }
            }
        }
    }

    /** The engine [settings] speak with, made again when the choice changed. */
    private fun speaker(settings: VoiceSettings): VoiceSpeaker {
        val key = if (settings.speaking == VoiceEngine.Android) ANDROID else settings.voice
        speaker?.takeIf { it.first == key }?.let { return it.second }
        release()
        val made = if (settings.speaking == VoiceEngine.Android) AndroidSpeaker(app) else ModelSpeaker(models, VoiceCatalog.voice(settings.voice))
        speaker = key to made
        return made
    }

    private fun release() {
        speaker?.second?.release()
        speaker = null
    }

    private fun choose(settings: VoiceSettings) {
        preferences.edit {
            putString(LISTENING, settings.listening.name)
            putString(LISTEN_MODEL, settings.listenModel)
            putString(SPEAKING, settings.speaking.name)
            putString(VOICE, settings.voice)
            putString(REPLIES, settings.spoken.name)
        }
        if (settings.spoken == SpokenReplies.Off) hush()
        update { it.copy(settings = settings) }
    }

    private fun load() = VoiceSettings(
        listening = VoiceEngine.entries.firstOrNull { it.name == preferences.getString(LISTENING, null) } ?: VoiceEngine.Android,
        listenModel = VoiceCatalog.listener(preferences.getString(LISTEN_MODEL, null).orEmpty()).id,
        speaking = VoiceEngine.entries.firstOrNull { it.name == preferences.getString(SPEAKING, null) } ?: VoiceEngine.Android,
        voice = VoiceCatalog.voice(preferences.getString(VOICE, null).orEmpty()).id,
        spoken = SpokenReplies.of(preferences.getString(REPLIES, null)),
    )

    private fun download(id: String) {
        val model = VoiceCatalog.model(id) ?: return
        if (downloads[id]?.isActive == true) return
        downloads[id] = scope.launch {
            state(id, ModelState.Downloading(0f))
            try {
                models.download(model, { state(id, ModelState.Downloading(it)) }, { state(id, ModelState.Unpacking) })
                state(id, models.states()[id] ?: ModelState.Absent)
            } catch (stopped: CancellationException) {
                state(id, ModelState.Absent)
                throw stopped
            } catch (failed: Exception) {
                BackendLog.w(TAG, "A voice model's download failed (${failed.javaClass.simpleName})")
                val reason = failed.message?.takeIf { "No space left" in it }?.let { "Not enough free space on this phone" } ?: failed.message ?: "The download failed"
                state(id, ModelState.Failed(reason))
            }
        }
    }

    private fun remove(id: String) {
        val model = VoiceCatalog.model(id) ?: return
        scope.launch {
            downloads.remove(id)?.cancelAndJoin()
            // A voice's engine holds its files open: it is let go, and made again when next needed.
            speaking.withLock { release() }
            models.remove(model)
            state(id, ModelState.Absent)
        }
    }

    private fun state(id: String, state: ModelState) = update { it.copy(models = it.models + (id to state)) }

    private fun microphone() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun update(change: (VoiceState) -> VoiceState) = stateHolder.update { it.copy(voice = change(it.voice)) }

    private companion object {
        const val TAG = "Voice"
        const val PREFERENCES = "voice"
        const val FOLDER = "voice"
        const val LISTENING = "listening"
        const val LISTEN_MODEL = "listenModel"
        const val SPEAKING = "speaking"
        const val VOICE = "voice"
        const val REPLIES = "replies"
        const val ANDROID = "android"
        const val SAMPLE = "Hello! This is how I read my answers aloud."
        const val NOTHING_HEARD = "Nothing was heard"
        const val VOICE_MISSING = "Download the voice in Settings › Assistant › Voice"
        const val IDLE_MILLIS = 120_000L
    }
}
