package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechRules
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * AssemblyAI's streaming speech-to-text, v3 (docs/speech-plan.md §2.2, §4.3), read from AssemblyAI's
 * own SDK: one WebSocket per hearing, the key in `Authorization` as it is. The microphone's PCM goes
 * up as it comes; each `Turn` comes back as the words so far, and the end of the turn, formatted, is
 * final. Done sends `ForceEndpoint`; the hearing over, `Terminate`.
 */
internal class AssemblyAiSpeech(
    private val key: String,
    private val audio: SpeechAudio,
    private val http: HttpClient,
    private val endpoint: String = ENDPOINT,
) : SpeechEngine {
    override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> = channelFlow {
        val url = URLBuilder(endpoint).apply {
            parameters.append("sample_rate", RATE.toString())
            parameters.append("encoding", "pcm_s16le")
            parameters.append("format_turns", "true")
            request.model?.let { parameters.append("speech_model", it) }
            request.language?.let { parameters.append("language_codes", JSONArray(listOf(SpeechRules.languageCode(it))).toString()) }
        }.buildString()
        val outcome = SpeechSocket.run(http, url, { headers.append(HttpHeaders.Authorization, key) }, audio, RATE, done, emit = { send(it) }) {
            var said = ""
            onDone {
                sendText(FORCE_ENDPOINT)
                endBy(DONE_WAIT_MILLIS)
            }
            onFrame { frame ->
                val message = (frame as? Frame.Text)?.let { JSONObject(it.readText()) } ?: return@onFrame null
                when (message.optString("type")) {
                    "Turn" -> {
                        said = message.optString("transcript")
                        val end = message.optBoolean("end_of_turn")
                        // The end of a turn not yet formatted: the formatted one follows, or this is final.
                        if (end && !message.optBoolean("turn_is_formatted")) endBy(FORMAT_WAIT_MILLIS)
                        SpeechEvent.Words(said, final = end && message.optBoolean("turn_is_formatted"))
                    }
                    "Error" -> SpeechEvent.Failed(failureOf(message.optInt("error_code", 0)), message.optString("error").ifBlank { null })
                    "Termination" -> SpeechEvent.Words(said, final = true)
                    else -> null
                }
            }
            onLate { SpeechEvent.Words(said, final = true) }
            onClosed { code, reason -> if (code == null || code == NORMAL) SpeechEvent.Words(said, final = true) else closed(code, reason) }
            onEnd { sendText(TERMINATE) }
        }
        send(outcome)
    }

    private fun closed(code: Int, reason: String?): SpeechEvent.Failed =
        SpeechEvent.Failed(failureOf(code), reason?.takeIf { it.isNotBlank() } ?: CODES[code])

    internal companion object {
        const val ENDPOINT = "wss://streaming.assemblyai.com/v3/ws"
        const val RATE = 16_000
        const val NORMAL = 1000
        const val FORCE_ENDPOINT = """{"type":"ForceEndpoint"}"""
        const val TERMINATE = """{"type":"Terminate"}"""

        /** After done, how long the turn's end may take to come. */
        const val DONE_WAIT_MILLIS = 3_000L

        /** After a turn ended unformatted, how long its formatted text may take to come. */
        const val FORMAT_WAIT_MILLIS = 2_000L

        /** AssemblyAI's close and error codes, as its SDK names them (`RealTimeErrorCodes`). */
        val CODES = mapOf(
            3005 to "Server error",
            3006 to "Input validation error",
            3007 to "Audio chunk duration violation",
            3008 to "Session expired: maximum session duration exceeded",
            3009 to "Too many concurrent sessions",
            4000 to "Sample rate must be a positive integer",
            4001 to "Not Authorized",
            4002 to "Insufficient Funds",
            4003 to "This feature is paid-only and requires a credit card",
            4008 to "Session Expired",
            4029 to "Client sent audio too fast",
            4031 to "Session idle for too long",
            4033 to "Audio duration is too long",
            4100 to "Endpoint received invalid JSON",
            4101 to "Endpoint received a message with an invalid schema",
            4102 to "This account has exceeded the number of allowed streams",
            1013 to "Temporary server condition forced blocking client's request",
        )

        fun failureOf(code: Int): SpeechFailure = when (code) {
            4001, 401, 403 -> SpeechFailure.KeyRefused
            4002, 4003, 402 -> SpeechFailure.NoCredit
            3009, 4102, 4029, 1013, 429 -> SpeechFailure.Busy
            3008, 4008, 4033 -> SpeechFailure.TooLong
            3005, in 500..599 -> SpeechFailure.Server
            else -> SpeechFailure.Failed
        }
    }
}
