package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** OpenAI's realtime transcription and Deepgram's live listening, against servers on this machine that answer as theirs do. */
class LiveSpeechTest {
    @Volatile private var script: suspend DefaultWebSocketServerSession.() -> Unit = {}
    @Volatile private var query = emptyMap<String, String>()
    @Volatile private var authorization: String? = null
    private val heard = ClientSaid()
    private val server = SpeechServer {
        routing {
            listOf("/v1/realtime", "/v1/listen").forEach { path ->
                webSocket(path) {
                    query = call.request.queryParameters.entries().associate { it.key to it.value.single() }
                    authorization = call.request.headers["Authorization"]
                    script()
                }
            }
        }
    }

    @After
    fun stop() = server.close()

    private fun openAi(done: CompletableDeferred<Unit> = CompletableDeferred(), language: String? = "en-US") = runBlocking {
        withTimeout(10_000) {
            OpenAiSpeech("sk-test", frames(2, 4_800), server.http, "ws://127.0.0.1:${server.port}/v1/realtime")
                .hear(SpeechRequest(language, "gpt-live-transcribe"), done).toList()
        }
    }

    private fun deepgram(done: CompletableDeferred<Unit> = CompletableDeferred()) = runBlocking {
        withTimeout(10_000) {
            DeepgramSpeech("dg-test", frames(2, 3_200), server.http, "ws://127.0.0.1:${server.port}/v1/listen")
                .hear(SpeechRequest("en-US", "nova-3"), done).toList()
        }
    }

    private val appended: (String) -> Boolean = { it.contains("input_audio_buffer.append") }

    private suspend fun DefaultWebSocketServerSession.say(json: String) = send(Frame.Text(json))

    @Test
    fun `OpenAI's transcription session is set first, audio goes base64, and the completed transcription is final`() {
        var session: JSONObject? = null
        var audio: String? = null
        script = {
            session = JSONObject((incoming.receive() as Frame.Text).let { String(it.data) })
            audio = JSONObject((incoming.receive() as Frame.Text).let { String(it.data) }).getString("audio")
            say("""{"type":"conversation.item.input_audio_transcription.delta","item_id":"i1","content_index":0,"delta":"Hello"}""")
            say("""{"type":"conversation.item.input_audio_transcription.delta","item_id":"i1","content_index":0,"delta":" there"}""")
            say("""{"type":"conversation.item.input_audio_transcription.completed","item_id":"i1","content_index":0,"transcript":"Hello there."}""")
            with(heard) { until("never", appended) }
        }
        assertEquals(
            listOf(SpeechEvent.Words("Hello", false), SpeechEvent.Words("Hello there", false), SpeechEvent.Words("Hello there.", true)),
            openAi(),
        )
        assertEquals(mapOf("intent" to "transcription"), query)
        assertEquals("Bearer sk-test", authorization)
        val expected = """{"type":"session.update","session":{"type":"transcription","audio":{"input":{"format":{"type":"audio/pcm","rate":24000},""" +
            """"transcription":{"model":"gpt-live-transcribe","language":"en"},"turn_detection":{"type":"server_vad"}}}}}"""
        assertTrue(session.toString(), sameJson(JSONObject(expected), session))
        assertEquals(4_800, Base64.getDecoder().decode(audio).size)
    }

    @Test
    fun `done commits OpenAI's buffer, and an error after it leaves what was heard`() {
        val done = CompletableDeferred<Unit>()
        script = {
            say("""{"type":"conversation.item.input_audio_transcription.delta","item_id":"i1","content_index":0,"delta":"stop it"}""")
            done.complete(Unit)
            with(heard) { until("input_audio_buffer.commit", appended) }
            say("""{"type":"error","error":{"type":"invalid_request_error","code":"input_audio_buffer_commit_empty","message":"buffer too small"}}""")
            with(heard) { until("never", appended) }
        }
        assertEquals(SpeechEvent.Words("stop it", final = true), openAi(done).last())
        assertTrue(heard.texts().any { it.contains("input_audio_buffer.commit") })
    }

    @Test
    fun `OpenAI's error says its own words`() {
        script = {
            say("""{"type":"error","error":{"type":"invalid_request_error","code":"x","message":"Model not found"}}""")
            with(heard) { until("never", appended) }
        }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.Failed, "Model not found")), openAi(language = null))
    }

    private fun results(text: String, final: Boolean, speechFinal: Boolean = false, fromFinalize: Boolean = false) =
        """{"type":"Results","is_final":$final,"speech_final":$speechFinal,"from_finalize":$fromFinalize,"channel":{"alternatives":[{"transcript":"$text","confidence":0.9}]}}"""

    @Test
    fun `Deepgram's segments are joined, the interim one with them, and speech_final ends it`() {
        script = {
            say(results("turn", final = false))
            say(results("turn on", final = true))
            say(results("the", final = false))
            say(results("the light.", final = true, speechFinal = true))
            with(heard) { until("CloseStream") }
        }
        assertEquals(
            listOf(
                SpeechEvent.Words("turn", false),
                SpeechEvent.Words("turn on", false),
                SpeechEvent.Words("turn on the", false),
                SpeechEvent.Words("turn on the light.", true),
            ),
            deepgram(),
        )
        assertEquals("Token dg-test", authorization)
        assertEquals(
            mapOf(
                "model" to "nova-3", "encoding" to "linear16", "sample_rate" to "16000", "channels" to "1",
                "interim_results" to "true", "smart_format" to "true", "punctuate" to "true", "language" to "en-US",
            ),
            query,
        )
        assertEquals(2, heard.audio)
    }

    @Test
    fun `done finalizes Deepgram's stream, and its final segment ends the hearing`() {
        val done = CompletableDeferred<Unit>()
        script = {
            say(results("open the", final = false))
            done.complete(Unit)
            with(heard) { until("Finalize") }
            say(results("open the door", final = true, fromFinalize = true))
            with(heard) { until("CloseStream") }
        }
        assertEquals(SpeechEvent.Words("open the door", final = true), deepgram(done).last())
    }

    @Test
    fun `Deepgram closing as a failed server says so`() {
        script = { close(CloseReason(1011, "")) }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.Server, "Closed (1011)")), deepgram())
    }
}
