package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/**
 * AssemblyAI's streaming v3 as its SDK speaks it (docs/voice-and-phone-control.md), against a server on this
 * machine that answers as AssemblyAI's does.
 */
class AssemblyAiSpeechTest {
    @Volatile private var script: suspend DefaultWebSocketServerSession.() -> Unit = {}
    @Volatile private var query = emptyMap<String, String>()
    @Volatile private var authorization: String? = null
    private val audioFrames = AtomicInteger()
    private val clientTexts = mutableListOf<String>()

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        install(ServerWebSockets)
        routing {
            webSocket("/v3/ws") {
                query = call.request.queryParameters.entries().associate { it.key to it.value.single() }
                authorization = call.request.headers["Authorization"]
                script()
            }
            get("/v3/refused") { call.respond(HttpStatusCode.Unauthorized) }
        }
    }.start(wait = false)
    private val port = runBlocking { server.engine.resolvedConnectors().single().port }
    private val http = HttpClient(OkHttp) { install(WebSockets) }

    @After
    fun stop() {
        http.close()
        server.stop(0, 0)
    }

    private val microphone = SpeechAudio { flow { repeat(FRAMES) { emit(ByteArray(3_200)); delay(10) }; awaitCancellation() } }

    private fun hear(
        path: String = "/v3/ws",
        audio: SpeechAudio = microphone,
        done: CompletableDeferred<Unit> = CompletableDeferred(),
        language: String? = "en-US",
    ): List<SpeechEvent> = runBlocking {
        withTimeout(10_000) {
            AssemblyAiSpeech("aai-test-key", audio, http, "ws://127.0.0.1:$port$path").hear(SpeechRequest(language, "universal-3-5-pro"), done).toList()
        }
    }

    /** Reads what the client sends until [text] comes, counting the audio. */
    private suspend fun DefaultWebSocketServerSession.until(text: String) {
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> audioFrames.incrementAndGet()
                is Frame.Text -> {
                    val said = frame.readText()
                    synchronized(clientTexts) { clientTexts += said }
                    if (said.contains(text)) return
                }
                else -> Unit
            }
        }
    }

    private suspend fun DefaultWebSocketServerSession.turn(text: String, end: Boolean, formatted: Boolean) =
        send(Frame.Text("""{"type":"Turn","turn_order":0,"transcript":"$text","end_of_turn":$end,"turn_is_formatted":$formatted,"end_of_turn_confidence":0.9,"words":[]}"""))

    @Test
    fun `turns are the words so far, the formatted end of the turn is final, and Terminate ends it`() {
        script = {
            send(Frame.Text("""{"type":"Begin","id":"s-1","expires_at":1893456000}"""))
            repeat(FRAMES) { (incoming.receive() as? Frame.Binary)?.let { audioFrames.incrementAndGet() } }
            turn("turn on", end = false, formatted = false)
            turn("turn on the light", end = true, formatted = false)
            turn("Turn on the light.", end = true, formatted = true)
            until("Terminate")
            send(Frame.Text("""{"type":"Termination","audio_duration_seconds":1}"""))
            close(CloseReason(CloseReason.Codes.NORMAL, ""))
        }
        val events = hear()
        assertEquals(
            listOf(
                SpeechEvent.Words("turn on", final = false),
                SpeechEvent.Words("turn on the light", final = false),
                SpeechEvent.Words("Turn on the light.", final = true),
            ),
            events,
        )
        assertEquals("aai-test-key", authorization)
        assertEquals(
            mapOf("sample_rate" to "16000", "encoding" to "pcm_s16le", "format_turns" to "true", "speech_model" to "universal-3-5-pro", "language_codes" to """["en"]"""),
            query,
        )
        assertEquals(FRAMES, audioFrames.get())
        runBlocking { withTimeout(5_000) { while (synchronized(clientTexts) { clientTexts.isEmpty() }) delay(10) } }
        assertEquals(listOf("""{"type":"Terminate"}"""), synchronized(clientTexts) { clientTexts.toList() })
    }

    @Test
    fun `done forces the end of the turn`() {
        val done = CompletableDeferred<Unit>()
        script = {
            turn("open the", end = false, formatted = false)
            done.complete(Unit)
            until("ForceEndpoint")
            turn("Open the door.", end = true, formatted = true)
            until("Terminate")
        }
        assertEquals(SpeechEvent.Words("Open the door.", final = true), hear(done = done).last())
        assertTrue(synchronized(clientTexts) { clientTexts.first() }.contains("ForceEndpoint"))
    }

    @Test
    fun `a turn that ends without its formatted text is final after a while`() {
        script = {
            turn("lights off", end = true, formatted = false)
            until("Terminate")
        }
        assertEquals(listOf(SpeechEvent.Words("lights off", final = false), SpeechEvent.Words("lights off", final = true)), hear(language = null))
        assertEquals(null, query["language_codes"])
    }

    @Test
    fun `the close codes say why, in the shared failures`() {
        script = { close(CloseReason(4001, "Not Authorized")) }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.KeyRefused, "Not Authorized")), hear())
        script = { close(CloseReason(3009, "")) }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.Busy, "Too many concurrent sessions")), hear())
    }

    @Test
    fun `an Error message ends the hearing with its own words`() {
        script = {
            send(Frame.Text("""{"type":"Error","error_code":4002,"error":"Insufficient Funds"}"""))
            until("Terminate")
        }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.NoCredit, "Insufficient Funds")), hear())
    }

    @Test
    fun `a refused handshake is the key's, by its status`() {
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.KeyRefused, "HTTP 401")), hear(path = "/v3/refused"))
    }

    @Test
    fun `no server says so in the network's words`() {
        val events = runBlocking {
            AssemblyAiSpeech("k", microphone, http, "ws://127.0.0.1:1/v3/ws").hear(SpeechRequest(null), CompletableDeferred()).toList()
        }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.Unreachable, "Unable to connect to 127.0.0.1 — is the server running?")), events)
    }

    @Test
    fun `a microphone that fails ends the hearing`() {
        script = { until("Terminate") }
        assertEquals(listOf(SpeechEvent.Failed(SpeechFailure.Microphone)), hear(audio = { flow { throw IOException("gone") } }))
    }

    private companion object {
        const val FRAMES = 3
    }
}
