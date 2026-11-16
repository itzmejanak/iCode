package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.util.Base64
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** AssemblyAI, Deepgram and OpenAI reading aloud over one socket a session (docs/voice-and-phone-control.md), against servers here that answer as theirs do. */
class StreamingVoicesTest {
    @Volatile private var script: suspend DefaultWebSocketServerSession.() -> Unit = {}
    private val queries = Collections.synchronizedList(mutableListOf<Map<String, String>>())
    @Volatile private var authorization: String? = null
    private val said = Collections.synchronizedList(mutableListOf<String>())
    private val server = SpeechServer {
        routing {
            listOf("/v1/ws", "/v1/speak", "/v1/realtime").forEach { path ->
                webSocket(path) {
                    queries += call.request.queryParameters.entries().associate { it.key to it.value.single() }
                    authorization = call.request.headers["Authorization"]
                    script()
                }
            }
        }
    }
    private val base get() = "ws://127.0.0.1:${server.port}"

    private val played = Collections.synchronizedList(mutableListOf<Byte>())
    private val output = object : VoiceOutput {
        override suspend fun play(pcm: ByteArray, rate: Int) {
            assertEquals(24_000, rate)
            played += pcm.toList()
        }

        override suspend fun drain() = Unit

        override fun close() = Unit
    }

    @After
    fun stop() = server.close()

    /** The next text the client sends, kept in [said]. */
    private suspend fun DefaultWebSocketServerSession.next(): String {
        while (true) {
            val frame = incoming.receive()
            if (frame is Frame.Text) return frame.readText().also { said += it }
        }
    }

    /** What the client has sent so far, copied under the lock: the server may still be adding its last frame. */
    private fun saidSoFar() = synchronized(said) { said.toList() }

    private fun sentences(vararg texts: String) = Channel<String>(Channel.UNLIMITED).apply {
        texts.forEach { trySend(it) }
        close()
    }

    private fun speak(voice: VoiceEngine, sentences: Channel<String>, request: VoiceRequest) =
        runBlocking(Dispatchers.Default) { withTimeout(10_000) { voice.speak(sentences, request) {} } }

    private fun type(json: String) = JSONObject(json).optString("type")

    private fun b64(vararg bytes: Byte) = Base64.getEncoder().encodeToString(bytes)

    @Test
    fun `AssemblyAI is asked for each sentence with Generate then Flush, its audio played in order, and Terminate ends it`() {
        script = {
            repeat(2) { sentence ->
                assertEquals("Generate", type(next()))
                assertEquals("Flush", type(next()))
                // A sentence's audio cut off its samples: the odd byte waits for the next chunk.
                send(Frame.Text("""{"type":"Audio","flush_id":$sentence,"audio":"${b64((10 * sentence + 1).toByte(), 2, 3)}"}"""))
                send(Frame.Text("""{"type":"Audio","flush_id":$sentence,"audio":"${b64(4)}"}"""))
                send(Frame.Text("""{"type":"FlushDone","flush_id":$sentence,"audio_duration_ms":10}"""))
            }
            assertEquals("Terminate", type(next()))
        }
        speak(assemblyAiVoice(server.http, "aai-key", "unused", { output }, base), sentences("Hello.", "Again."), VoiceRequest(voice = "aai-jane", language = "en-US"))
        assertEquals(listOf<Byte>(1, 2, 3, 4, 11, 2, 3, 4), played.toList())
        assertEquals(listOf("Hello.", "Again."), saidSoFar().filter { type(it) == "Generate" }.map { JSONObject(it).getString("text") })
        assertEquals("aai-key", authorization)
        assertEquals(
            mapOf("voice_id" to "aai-jane", "language" to "english", "sample_rate" to "24000", "encoding" to "pcm_s16le", "word_boundaries" to "false"),
            queries.single(),
        )
    }

    @Test
    fun `AssemblyAI's errors are said in the shared failures, once what came before is heard`() {
        script = {
            next()
            next()
            send(Frame.Text("""{"type":"Audio","flush_id":0,"audio":"${b64(1, 2)}"}"""))
            send(Frame.Text("""{"type":"FlushDone","flush_id":0,"audio_duration_ms":10}"""))
            next()
            next()
            send(Frame.Text("""{"type":"Error","error_type":"rate_limited","error":"Too many requests"}"""))
            close(CloseReason(1008, "rate_limited"))
        }
        try {
            speak(assemblyAiVoice(server.http, "aai-key", "unused", { output }, base), sentences("One.", "Two."), VoiceRequest(voice = "aai-jane", language = "en"))
            fail("Refused")
        } catch (failed: ProviderFailed) {
            assertEquals(SpeechFailure.Busy, failed.failure)
        }
        assertEquals(listOf<Byte>(1, 2), played.toList())
    }

    @Test
    fun `a language AssemblyAI's voices do not speak is refused before anything is sent`() {
        try {
            speak(assemblyAiVoice(server.http, "aai-key", "unused", { output }, base), sentences("こんにちは。"), VoiceRequest(voice = "aai-jane", language = "ja-JP"))
            fail("Refused")
        } catch (failed: ProviderFailed) {
            assertEquals(SpeechFailure.Language, failed.failure)
        }
        assertTrue(queries.isEmpty())
    }

    @Test
    fun `Deepgram speaks with the voice as its model, Speak then Flush a sentence, binary audio, and Close`() {
        script = {
            assertEquals("Speak", type(next()))
            assertEquals("Flush", type(next()))
            send(Frame.Binary(true, byteArrayOf(5, 6)))
            send(Frame.Text("""{"type":"Metadata","request_id":"r"}"""))
            send(Frame.Text("""{"type":"Flushed","sequence_id":0}"""))
            assertEquals("Close", type(next()))
        }
        speak(deepgramVoice(server.http, "dg-key", { output }, base), sentences("Hola."), VoiceRequest(voice = "aura-2-celeste-es", speed = 1.25f))
        assertEquals(listOf<Byte>(5, 6), played.toList())
        assertEquals("Token dg-key", authorization)
        assertEquals(mapOf("model" to "aura-2-celeste-es", "encoding" to "linear16", "sample_rate" to "24000", "speed" to "1.25"), queries.single())
    }

    @Test
    fun `hushed, the socket closes at once, and with it the provider's work`() = runBlocking(Dispatchers.Default) {
        val asked = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        script = {
            next()
            next()
            asked.complete(Unit)
            runCatching { while (true) incoming.receive() }
            closed.complete(Unit)
        }
        val speaking = launch { deepgramVoice(server.http, "dg-key", { output }, base).speak(Channel<String>(Channel.UNLIMITED).apply { trySend("Hola.") }, VoiceRequest(voice = "aura-2-celeste-es")) {} }
        withTimeout(10_000) { asked.await() }
        speaking.cancelAndJoin()
        withTimeout(10_000) { closed.await() }
    }

    @Test
    fun `a socket the provider closes while there is nothing to say is opened again for the next sentence`() = runBlocking(Dispatchers.Default) {
        script = {
            next()
            next()
            send(Frame.Binary(true, byteArrayOf(queries.size.toByte(), 0)))
            send(Frame.Text("""{"type":"Flushed","sequence_id":0}"""))
            if (queries.size == 1) close(CloseReason(1000, "idle")) else assertEquals("Close", type(next()))
        }
        val sentences = Channel<String>(Channel.UNLIMITED)
        val speaking = launch { deepgramVoice(server.http, "dg-key", { output }, base).speak(sentences, VoiceRequest(voice = "aura-2-thalia-en")) {} }
        sentences.send("One.")
        withTimeout(10_000) { while (played.size < 2) delay(5) }
        delay(200)
        sentences.send("Two.")
        sentences.close()
        withTimeout(10_000) { speaking.join() }
        assertEquals(listOf<Byte>(1, 0, 2, 0), played.toList())
        assertEquals(2, queries.size)
    }

    @Test
    fun `OpenAI's session is set first, and each sentence is one response, the next asked for once the last is done`() {
        val early = java.util.concurrent.atomic.AtomicBoolean()
        script = {
            next()
            repeat(2) { index ->
                next()
                // The next is not asked for until this one is done: only one response writes at once.
                if (index == 0) early.set(withTimeoutOrNull(300) { incoming.receive() } != null)
                send(Frame.Text("""{"type":"response.output_audio.delta","response_id":"r$index","delta":"${b64((index + 7).toByte(), 0)}"}"""))
                send(Frame.Text("""{"type":"response.done","response":{"id":"r$index","status":"completed"}}"""))
            }
        }
        speak(openAiVoice(server.http, "sk-test", { output }, base), sentences("One.", "Two."), VoiceRequest(voice = "marin", model = "gpt-realtime-2.1-mini", speed = 1.25f))
        assertEquals(listOf<Byte>(7, 0, 8, 0), played.toList())
        assertEquals(false, early.get())
        assertEquals(listOf("session.update", "response.create", "response.create"), saidSoFar().map(::type))
        assertEquals("Bearer sk-test", authorization)
        assertEquals(mapOf("model" to "gpt-realtime-2.1-mini"), queries.single())
        val session = JSONObject(said.first()).getJSONObject("session")
        val expected = JSONObject()
            .put("type", "realtime")
            .put("output_modalities", org.json.JSONArray(listOf("audio")))
            .put("instructions", OpenAiProtocol.INSTRUCTIONS)
            .put("audio", JSONObject().put("output", JSONObject().put("format", JSONObject().put("type", "audio/pcm").put("rate", 24_000)).put("voice", "marin").put("speed", 1.25)))
        assertTrue(session.toString(), sameJson(expected, session))
        val response = JSONObject(said[1]).getJSONObject("response")
        assertEquals("none", response.getString("conversation"))
        assertEquals("One.", response.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text"))
    }

    @Test
    fun `OpenAI's failed response says its own words`() {
        script = {
            next()
            next()
            send(Frame.Text("""{"type":"response.done","response":{"status":"failed","status_details":{"type":"failed","error":{"type":"server_error","message":"The model failed"}}}}"""))
        }
        try {
            speak(openAiVoice(server.http, "sk-test", { output }, base), sentences("One."), VoiceRequest(voice = "marin"))
            fail("Refused")
        } catch (failed: ProviderFailed) {
            assertEquals(SpeechFailure.Failed, failed.failure)
            assertEquals("The model failed", failed.detail)
        }
    }
}
