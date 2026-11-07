package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The providers that answer once the user stopped (docs/speech-plan.md §4.3, §4.4): when a hearing ends, and what each is sent. */
class UploadSpeechTest {
    @Volatile private var answer: Pair<HttpStatusCode, String> = HttpStatusCode.OK to "{}"
    @Volatile private var body = ByteArray(0)
    @Volatile private var headers = emptyMap<String, String?>()
    private val server = SpeechServer {
        routing {
            post("/v1/audio/transcriptions") { reply() }
            post("/v1/speech:recognize") { reply() }
        }
    }

    private suspend fun io.ktor.server.routing.RoutingContext.reply() {
        body = call.receive<ByteArray>()
        headers = listOf("Authorization", "X-Goog-Api-Key").associateWith { call.request.headers[it] }
        call.respondText(answer.second, ContentType.Application.Json, answer.first)
    }

    @After
    fun stop() = server.close()

    /** The room for [ROOM] frames, speech for [loud], then quiet. */
    private fun speech(loud: Int) = SpeechAudio {
        kotlinx.coroutines.flow.flow {
            repeat(ROOM) { emit(pcm(3_200, 50)) }
            repeat(loud) { emit(pcm(3_200, 3_000)) }
            while (true) emit(pcm(3_200, 50))
        }
    }

    private fun hear(upload: SpeechUpload, audio: SpeechAudio = speech(5), done: CompletableDeferred<Unit> = CompletableDeferred(), language: String? = "en-US", model: String? = null) =
        runBlocking { withTimeout(10_000) { UploadSpeech(audio, upload).hear(SpeechRequest(language, model), done).toList() } }

    @Test
    fun `a hearing ends after a second and a half of quiet that follows speech`() {
        val quiet = SpeechQuiet()
        repeat(3) { assertFalse(quiet.ended(pcm(3_200, 0))) }
        assertFalse(quiet.ended(pcm(3_200, 3_000)))
        assertTrue(quiet.spoke)
        repeat(14) { assertFalse(quiet.ended(pcm(3_200, 0))) }
        assertTrue(quiet.ended(pcm(3_200, 0)))
    }

    @Test
    fun `eight seconds of nothing ends it, and nothing is sent`() {
        val quiet = SpeechQuiet()
        repeat(79) { assertFalse(quiet.ended(pcm(3_200, 0))) }
        assertTrue(quiet.ended(pcm(3_200, 0)))
        assertFalse(quiet.spoke)
        var sent = false
        assertEquals(listOf(SpeechEvent.Words("", final = true)), hear({ _, _, _ -> SpeechEvent.Words("x", true).also { sent = true } }, audio = speech(0)))
        assertFalse(sent)
    }

    @Test
    fun `a room louder than the threshold is not speech`() {
        val quiet = SpeechQuiet()
        repeat(3) { quiet.ended(pcm(3_200, 1_000)) }
        quiet.ended(pcm(3_200, 1_000))
        assertFalse(quiet.spoke)
        quiet.ended(pcm(3_200, 3_500))
        assertTrue(quiet.spoke)
    }

    @Test
    fun `speech from the first frame is heard once the room is`() {
        val quiet = SpeechQuiet()
        repeat(3) { quiet.ended(pcm(3_200, 3_000)) }
        assertFalse(quiet.spoke)
        quiet.ended(pcm(3_200, 40))
        quiet.ended(pcm(3_200, 3_000))
        assertTrue(quiet.spoke)
    }

    @Test
    fun `done sends what was heard at once`() {
        val done = CompletableDeferred<Unit>()
        val audio = SpeechAudio {
            kotlinx.coroutines.flow.flow {
                repeat(ROOM) { emit(pcm(3_200, 50)) }
                repeat(2) { emit(pcm(3_200, 3_000)) }
                done.complete(Unit)
                while (true) emit(pcm(3_200, 3_000))
            }
        }
        var length = 0
        hear({ pcm, _, _ -> SpeechEvent.Words("ok", true).also { length = pcm.size } }, audio = audio, done = done)
        assertEquals((ROOM + 3) * 3_200, length)
    }

    @Test
    fun `an OpenAI-compatible server gets the hearing as a WAV file, and its text is final`() {
        answer = HttpStatusCode.OK to """{"text":" Turn it off. "}"""
        val events = hear(OpenAiTranscription(server.http, "http://127.0.0.1:${server.port}/v1", "local-key"), model = "whisper-large-v3")
        assertEquals(listOf(SpeechEvent.Words("Turn it off.", final = true)), events)
        val sent = String(body, Charsets.ISO_8859_1)
        assertTrue(sent.contains("filename=\"speech.wav\"") && sent.contains("RIFF") && sent.contains("WAVEfmt "))
        assertTrue(sent.contains("name=\"model\"") && sent.contains("\r\nwhisper-large-v3\r\n"))
        assertTrue(sent.contains("name=\"language\"") && sent.contains("\r\nen\r\n"))
        assertTrue(sent.contains("name=\"response_format\"") && sent.contains("\r\njson\r\n"))
        assertEquals("Bearer local-key", headers["Authorization"])
    }

    @Test
    fun `its refusal says the server's own words`() {
        answer = HttpStatusCode.Unauthorized to """{"error":{"message":"Incorrect API key provided"}}"""
        assertEquals(
            listOf(SpeechEvent.Failed(SpeechFailure.KeyRefused, "Incorrect API key provided")),
            hear(OpenAiTranscription(server.http, "http://127.0.0.1:${server.port}/v1", key = null)),
        )
        assertEquals(null, headers["Authorization"])
    }

    @Test
    fun `Google gets LINEAR16 at 16 kHz, base64, the key in its header, and its results are joined`() {
        answer = HttpStatusCode.OK to """{"results":[{"alternatives":[{"transcript":"turn on"}]},{"alternatives":[{"transcript":" the light"}]}]}"""
        val events = hear(GoogleRecognition(server.http, "AIza-test", "http://127.0.0.1:${server.port}/v1/speech:recognize"), model = "latest_short")
        assertEquals(listOf(SpeechEvent.Words("turn on the light", final = true)), events)
        val sent = JSONObject(String(body))
        val config = """{"encoding":"LINEAR16","sampleRateHertz":16000,"languageCode":"en-US","enableAutomaticPunctuation":true,"model":"latest_short"}"""
        assertTrue(sent.toString(), sameJson(JSONObject(config), sent.getJSONObject("config")))
        assertEquals((ROOM + 5 + 15) * 3_200, Base64.getDecoder().decode(sent.getJSONObject("audio").getString("content")).size)
        assertEquals("AIza-test", headers["X-Goog-Api-Key"])
    }

    @Test
    fun `a key Google cannot read is refused as one`() {
        answer = HttpStatusCode.BadRequest to
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""
        assertEquals(
            listOf(SpeechEvent.Failed(SpeechFailure.KeyRefused, "API key not valid. Please pass a valid API key.")),
            hear(GoogleRecognition(server.http, "AIza-bad", "http://127.0.0.1:${server.port}/v1/speech:recognize")),
        )
    }

    private companion object {
        /** Frames of the room's own sound before anyone speaks. */
        const val ROOM = 3
    }

    @Test
    fun `no server says so in the network's words`() {
        assertEquals(
            listOf(SpeechEvent.Failed(SpeechFailure.Unreachable, "Unable to connect to 127.0.0.1 — is the server running?")),
            hear(OpenAiTranscription(server.http, "http://127.0.0.1:1/v1", key = null)),
        )
    }
}
