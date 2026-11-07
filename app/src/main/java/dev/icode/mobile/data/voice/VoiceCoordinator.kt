package dev.icode.mobile.data.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import dev.icode.mobile.data.assistant.SharedPreferencesAssistantProviderStore
import dev.icode.mobile.data.phone.PhoneForegroundService
import dev.icode.mobile.data.runtime.TerminalSessionManager
import dev.icode.mobile.data.voice.speech.SpeechEngines
import dev.icode.mobile.data.voice.speech.SpeechHearings
import dev.icode.mobile.data.voice.speech.SpeechProviderStore
import dev.icode.mobile.data.voice.speech.SpeechProviders
import dev.icode.mobile.data.voice.speech.SpeechRequest
import dev.icode.mobile.data.voice.speech.SpeechUse
import dev.icode.mobile.data.voice.speech.VoiceRequest
import dev.icode.mobile.data.voice.speech.VoiceSessions
import dev.icode.mobile.data.voice.speech.VoiceUse
import dev.icode.mobile.data.workspace.ICodeStateHolder
import dev.icode.mobile.domain.AssistantProviderKind
import dev.icode.mobile.domain.NoticeTone
import dev.icode.mobile.domain.SpeechIntent
import dev.icode.mobile.domain.VoiceIntent
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.SpokenReplies
import dev.icode.mobile.domain.voice.SpokenText
import dev.icode.mobile.domain.voice.SpokenTurn
import dev.icode.mobile.domain.voice.VoiceCue
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Voice in and out (docs/phone-agent-plan.md §5.8), one per process: the microphone for the chat and the
 * bubble, heard by the provider that listens (docs/speech-plan.md), and the assistant read aloud by the
 * provider that speaks (docs/tts-plan.md) as Spoken replies says — each sentence as soon as it is
 * written ([SpokenTurn]), from one speech session per turn. What is heard and said is never logged; a
 * failure logs its type only.
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

    /** The speech providers' owner: the pages' and the agent's (docs/speech-plan.md §1). */
    val providers = SpeechProviders(SpeechProviderStore(app), stateHolder, scope, assistantKey = ::assistantKey)

    /** The assistant's providers' keys, read only: an OpenAI speech provider may use one (docs/speech-plan.md §9 D4). */
    private val assistantKeys by lazy { SharedPreferencesAssistantProviderStore(app) }
    private val engines = SpeechEngines(app, scope)
    private val hearings = SpeechHearings(
        scope,
        refusal = ::refusal,
        use = { id ->
            providers.connection(id, SpeechWay.Listening).map { (provider, key) ->
                SpeechUse(provider, engines.of(provider, key), SpeechRequest(language = provider.language, model = provider.modelUsed))
            }
        },
        heard = { change -> update { it.copy(heard = change(it.heard)) } },
        ended = { provider, failure -> providers.heard(provider.id, failure) },
    )

    private val sessions = VoiceSessions(
        scope,
        use = { id ->
            providers.connection(id, SpeechWay.Speaking).mapCatching { (provider, key) ->
                val request = VoiceRequest(voice = provider.voice, model = provider.voiceModelUsed, language = provider.language, speed = provider.speed)
                VoiceUse(provider, engines.voiceOf(provider, key), request)
            }
        },
        began = { key -> update { it.copy(reading = key) } },
        playing = { playing -> update { it.copy(speaking = playing) } },
        ended = { failure ->
            update { it.copy(speaking = false, reading = null) }
            failure?.let { words -> scope.launch { stateHolder.notify(NoticeTone.HeadsUp, "The answer was not read aloud", words) } }
        },
    )

    private val voice: VoiceState get() = stateHolder.current.voice

    fun start() {
        providers.start()
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
            is VoiceIntent.Listen -> {
                sessions.hush()
                hearings.listen(intent.from)
            }
            VoiceIntent.FinishListening -> hearings.finish()
            VoiceIntent.CancelListening -> hearings.stop()
            is SpeechIntent.Test -> {
                sessions.hush()
                hearings.listen(VoiceFrom.Test, intent.id)
            }
            is SpeechIntent.Hear -> sample(intent.id)
            is SpeechIntent -> providers.handle(intent)
            VoiceIntent.Silence -> sessions.hush()
            VoiceIntent.Sample -> sample(provider = null)
            is VoiceIntent.Read -> read(intent.key, intent.text)
            is VoiceIntent.ChooseSpokenReplies -> choose(intent.spoken)
        }
    }

    /**
     * Why the microphone cannot listen now, or null. Away from iCode, Android lets an app take it only
     * through a foreground service that already holds it: the bubble asks phone control's service to
     * take it first.
     */
    private suspend fun refusal(): String? {
        if (!microphone()) return "Allow the microphone in Settings › Assistant › Voice"
        if (iCodeOnScreen.value || voice.away) return null
        if (stateHolder.current.phone.bubble) {
            PhoneForegroundService.start(app, microphone = true)
            withTimeoutOrNull(MIC_WAIT_MILLIS) { PhoneForegroundService.microphone.first { it } }
        }
        return "Open iCode once to use the microphone".takeUnless { iCodeOnScreen.value || voice.away }
    }

    /** The sample, said by [provider] — null: the one that speaks. */
    private fun sample(provider: String?) {
        sessions.begin(provider = provider)
        sessions.say(SAMPLE)
        sessions.end()
    }

    /** An answer read on request, as Spoken replies would say it, whatever they are set to: the user asked. */
    private fun read(key: String, text: String) {
        val sentences = SpokenText.ready(text, final = true).sentences
        if (sentences.isEmpty()) return
        sessions.begin(reading = key)
        sentences.forEach(sessions::say)
        sessions.end()
    }

    private fun cue(cue: VoiceCue) = when (cue) {
        VoiceCue.Begin -> sessions.begin()
        is VoiceCue.Say -> sessions.say(cue.text)
        VoiceCue.End -> sessions.end()
        VoiceCue.Hush -> sessions.hush()
    }

    private fun choose(spoken: SpokenReplies) {
        preferences.edit { putString(REPLIES, spoken.name) }
        if (spoken == SpokenReplies.Off) sessions.hush()
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

    private fun assistantKey(id: String): String? = assistantKeys.connection(id)?.takeIf { it.kind == AssistantProviderKind.OpenAI }?.apiKey

    private fun microphone() = ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun update(change: (VoiceState) -> VoiceState) = stateHolder.update { it.copy(voice = change(it.voice)) }

    private companion object {
        const val PREFERENCES = "voice"
        const val REPLIES = "replies"
        const val OLD_MODELS = "voice"
        val OLD_KEYS = listOf("listening", "listenModel", "speaking", "voice")
        const val SAMPLE = "Hello! This is how I read my answers aloud."
        const val MIC_WAIT_MILLIS = 1_500L
    }
}
