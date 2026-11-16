package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.json.JSONObject

/**
 * Deepgram's live speech-to-text (docs/voice-and-phone-control.md), as its AsyncAPI spec gives it: one
 * socket to `/v1/listen` with `Authorization: Token`, 16 kHz linear16 with interim results. The words so
 * far are the final segments heard plus the one still being heard; `speech_final` ends the hearing.
 * Done sends `Finalize`, and its final segment ends it; the hearing over, `CloseStream`.
 */
internal class DeepgramSpeech(
    private val key: String,
    private val audio: SpeechAudio,
    private val http: HttpClient,
    private val endpoint: String = ENDPOINT,
) : SpeechEngine {
    override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> = channelFlow {
        val url = URLBuilder(endpoint).apply {
            request.model?.let { parameters.append("model", it) }
            parameters.append("encoding", "linear16")
            parameters.append("sample_rate", RATE.toString())
            parameters.append("channels", "1")
            parameters.append("interim_results", "true")
            parameters.append("smart_format", "true")
            parameters.append("punctuate", "true")
            // Deepgram takes the tag as it is: `en`, or `en-US`.
            request.language?.let { parameters.append("language", it) }
        }.buildString()
        val outcome = SpeechSocket.run(http, url, { headers.append(HttpHeaders.Authorization, "Token $key") }, audio, RATE, done, emit = { send(it) }) {
            // The segments Deepgram has made final, and the one it still hears.
            val heard = mutableListOf<String>()
            var hearing = ""
            var finishing = false
            fun said() = (heard + hearing).filter { it.isNotBlank() }.joinToString(" ")
            onDone {
                finishing = true
                sendText(FINALIZE)
                endBy(DONE_WAIT_MILLIS)
            }
            onFrame { frame ->
                val message = (frame as? Frame.Text)?.let { JSONObject(it.readText()) } ?: return@onFrame null
                if (message.optString("type") != "Results") return@onFrame null
                val text = message.optJSONObject("channel")?.optJSONArray("alternatives")?.optJSONObject(0)?.optString("transcript").orEmpty()
                val segmentFinal = message.optBoolean("is_final")
                if (segmentFinal) {
                    heard += text
                    hearing = ""
                } else {
                    hearing = text
                }
                val ends = message.optBoolean("speech_final") || (segmentFinal && (finishing || message.optBoolean("from_finalize")))
                SpeechEvent.Words(said(), final = ends)
            }
            onLate { SpeechEvent.Words(said(), final = true) }
            onClosed { code, reason -> if (code == null || code == NORMAL) SpeechEvent.Words(said(), final = true) else closed(code, reason) }
            onEnd { sendText(CLOSE_STREAM) }
        }
        send(outcome)
    }

    private fun closed(code: Int, reason: String?): SpeechEvent.Failed =
        SpeechEvent.Failed(if (code == SERVER_ERROR) SpeechFailure.Server else SpeechFailure.Failed, reason?.ifBlank { null } ?: "Closed ($code)")

    internal companion object {
        const val ENDPOINT = "wss://api.deepgram.com/v1/listen"
        const val RATE = 16_000
        const val NORMAL = 1000
        /** A WebSocket's own code for a server that failed. */
        const val SERVER_ERROR = 1011
        const val FINALIZE = """{"type":"Finalize"}"""
        const val CLOSE_STREAM = """{"type":"CloseStream"}"""
        const val DONE_WAIT_MILLIS = 3_000L
    }
}
