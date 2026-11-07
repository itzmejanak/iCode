package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechRules
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.Base64
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.json.JSONObject

/**
 * OpenAI's realtime transcription (docs/speech-plan.md §2.3, §4.3), as its OpenAPI spec and SDK give it:
 * one socket to `/v1/realtime?intent=transcription`, a `transcription` session of 24 kHz PCM with the
 * server's turn detection. Audio goes up base64 in `input_audio_buffer.append`; the deltas are the
 * words so far, and the first transcription completed is final. Done commits the buffer.
 */
internal class OpenAiSpeech(
    private val key: String,
    private val audio: SpeechAudio,
    private val http: HttpClient,
    private val endpoint: String = ENDPOINT,
) : SpeechEngine {
    override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> = channelFlow {
        val outcome = SpeechSocket.run(
            http, "$endpoint?intent=transcription", { headers.append(HttpHeaders.Authorization, "Bearer $key") }, audio, RATE, done,
            emit = { send(it) },
            upload = { pcm -> Frame.Text(JSONObject().put("type", "input_audio_buffer.append").put("audio", Base64.getEncoder().encodeToString(pcm)).toString()) },
        ) {
            var said = ""
            var finishing = false
            onOpen { sendText(session(request)) }
            onDone {
                finishing = true
                sendText(COMMIT)
                endBy(DONE_WAIT_MILLIS)
            }
            onFrame { frame ->
                val message = (frame as? Frame.Text)?.let { JSONObject(it.readText()) } ?: return@onFrame null
                when (message.optString("type")) {
                    "conversation.item.input_audio_transcription.delta" -> {
                        said += message.optString("delta")
                        SpeechEvent.Words(said, final = false)
                    }
                    "conversation.item.input_audio_transcription.completed" -> SpeechEvent.Words(message.optString("transcript").ifBlank { said }, final = true)
                    "error" -> {
                        val error = message.optJSONObject("error")
                        // Done, the buffer may have been committed already by the turn detection: what was heard stands.
                        if (finishing && said.isNotBlank()) SpeechEvent.Words(said, final = true)
                        else SpeechEvent.Failed(if (error?.optString("type") == "server_error") SpeechFailure.Server else SpeechFailure.Failed, error?.optString("message")?.ifBlank { null })
                    }
                    else -> null
                }
            }
            onLate { SpeechEvent.Words(said, final = true) }
            onClosed { code, reason -> if (code == null || code == NORMAL) SpeechEvent.Words(said, final = true) else SpeechEvent.Failed(SpeechFailure.Failed, reason?.ifBlank { null } ?: "Closed ($code)") }
        }
        send(outcome)
    }

    /** The `session.update` that makes this a transcription session. */
    private fun session(request: SpeechRequest): String {
        val transcription = JSONObject().apply {
            request.model?.let { put("model", it) }
            request.language?.let { put("language", SpeechRules.languageCode(it)) }
        }
        val input = JSONObject()
            .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
            .put("transcription", transcription)
            .put("turn_detection", JSONObject().put("type", "server_vad"))
        return JSONObject().put("type", "session.update")
            .put("session", JSONObject().put("type", "transcription").put("audio", JSONObject().put("input", input)))
            .toString()
    }

    internal companion object {
        const val ENDPOINT = "wss://api.openai.com/v1/realtime"

        /** The only rate its PCM takes. */
        const val RATE = 24_000
        const val NORMAL = 1000
        const val COMMIT = """{"type":"input_audio_buffer.commit"}"""
        const val DONE_WAIT_MILLIS = 5_000L
    }
}
