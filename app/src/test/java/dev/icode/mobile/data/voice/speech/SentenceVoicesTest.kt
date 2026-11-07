package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.VoiceOption
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Google, ElevenLabs, Azure and OpenAI-compatible servers, each sentence one request (docs/tts-plan.md §4.3), against servers here that answer as theirs do. */
class SentenceVoicesTest {
    @Volatile private var answer: suspend RoutingContext.() -> Unit = { call.respondText("{}", ContentType.Application.Json) }
    @Volatile private var body = ""
    @Volatile private var path = ""
    @Volatile private var query = emptyMap<String, String>()
    @Volatile private var headers = emptyMap<String, String?>()
    private val server = SpeechServer {
        routing {
            post("{path...}") {
                body = call.receiveText()
                heard()
                answer()
            }
            get("{path...}") {
                heard()
                answer()
            }
        }
    }
    private val base get() = "http://127.0.0.1:${server.port}"

    private fun RoutingContext.heard() {
        path = call.request.local.uri.substringBefore('?')
        query = call.request.queryParameters.entries().associate { it.key to it.value.single() }
        headers = listOf("X-Goog-Api-Key", "xi-api-key", "Ocp-Apim-Subscription-Key", "Authorization", "X-Microsoft-OutputFormat", "Content-Type", "User-Agent")
            .associateWith { call.request.headers[it] }
    }

    @After
    fun stop() = server.close()

    /** What [synthesis] gives for [text]: its PCM, joined, and the rates. */
    private fun say(synthesis: Synthesis, text: String = "Hello & <you>.", previous: String? = null, request: VoiceRequest = VoiceRequest()): Pair<ByteArray, Set<Int>> {
        var pcm = ByteArray(0)
        val rates = mutableSetOf<Int>()
        runBlocking {
            withTimeout(10_000) {
                synthesis.say(text, previous, request) { chunk, rate ->
                    pcm += chunk
                    rates += rate
                }
            }
        }
        return pcm to rates
    }

    private fun refused(block: () -> Unit): ProviderFailed {
        try {
            block()
        } catch (failed: ProviderFailed) {
            return failed
        }
        fail("Refused")
        throw AssertionError()
    }

    private fun wav(pcm: ByteArray, rate: Int = 24_000): ByteArray {
        fun int(value: Int) = ByteArray(4) { (value shr (8 * it)).toByte() }
        fun short(value: Int) = ByteArray(2) { (value shr (8 * it)).toByte() }
        val fmt = short(1) + short(1) + int(rate) + int(rate * 2) + short(2) + short(16)
        val body = "WAVE".toByteArray() + "fmt ".toByteArray() + int(16) + fmt + "data".toByteArray() + int(-1) + pcm
        return "RIFF".toByteArray() + int(body.size) + body
    }

    /** [bytes] streamed in pieces of [piece], as a server streams them. */
    private suspend fun RoutingContext.stream(bytes: ByteArray, piece: Int = 7) = call.respondBytesWriter(ContentType.Application.OctetStream) {
        bytes.toList().chunked(piece).forEach { writeFully(it.toByteArray()); flush() }
    }

    @Test
    fun `Google gets raw PCM at 24 kHz with the voice, language and rate, the key in its header`() {
        val pcm = ByteArray(480) { it.toByte() }
        answer = { call.respondText(JSONObject().put("audioContent", Base64.getEncoder().encodeToString(pcm)).toString(), ContentType.Application.Json) }
        val (said, rates) = say(GoogleVoice(server.http, "AIza-test", base), request = VoiceRequest(voice = "en-US-Chirp3-HD-Achernar", language = "en-US", speed = 1.25f))
        assertArrayEquals(pcm, said)
        assertEquals(setOf(24_000), rates)
        assertEquals("/v1/text:synthesize", path)
        assertEquals("AIza-test", headers["X-Goog-Api-Key"])
        val expected = """{"input":{"text":"Hello & <you>."},"voice":{"languageCode":"en-US","name":"en-US-Chirp3-HD-Achernar"},""" +
            """"audioConfig":{"audioEncoding":"PCM","sampleRateHertz":24000,"speakingRate":1.25}}"""
        assertTrue(body, sameJson(JSONObject(expected), JSONObject(body)))
    }

    @Test
    fun `Google's voices are its list for the language, and a key it cannot read is refused as one`() {
        answer = { call.respondText("""{"voices":[{"name":"de-DE-Neural2-A","languageCodes":["de-DE"],"ssmlGender":"FEMALE"},{"name":"de-DE-Standard-B","ssmlGender":"MALE"}]}""", ContentType.Application.Json) }
        val choices = runBlocking { GoogleVoice.choices(server.http, "AIza-test", "de-DE", base) }
        assertEquals(listOf(VoiceOption("de-DE-Neural2-A", "de-DE-Neural2-A · Female"), VoiceOption("de-DE-Standard-B", "de-DE-Standard-B · Male")), choices.voices)
        assertEquals("/v1/voices", path)
        assertEquals(mapOf("languageCode" to "de-DE"), query)
        answer = { call.respondText("""{"error":{"code":400,"message":"API key not valid.","details":[{"reason":"API_KEY_INVALID"}]}}""", ContentType.Application.Json, HttpStatusCode.BadRequest) }
        assertEquals(SpeechFailure.KeyRefused, refused { say(GoogleVoice(server.http, "AIza-bad", base)) }.failure)
    }

    @Test
    fun `ElevenLabs streams pcm_24000 for the voice, the sentence before sent for its flow`() {
        val pcm = ByteArray(101) { it.toByte() }
        answer = { stream(pcm) }
        val (said, rates) = say(ElevenLabsVoice(server.http, "xi-test", base), previous = "Before.", request = VoiceRequest(voice = "v-1", model = "eleven_multilingual_v2", language = "en"))
        assertArrayEquals(pcm, said)
        assertEquals(setOf(24_000), rates)
        assertEquals("/v1/text-to-speech/v-1/stream", path)
        assertEquals(mapOf("output_format" to "pcm_24000"), query)
        assertEquals("xi-test", headers["xi-api-key"])
        assertTrue(body, sameJson(JSONObject("""{"text":"Hello & <you>.","model_id":"eleven_multilingual_v2","previous_text":"Before."}"""), JSONObject(body)))
    }

    @Test
    fun `ElevenLabs lists the account's voices and the models that read aloud, and with none chosen its first voice speaks`() {
        answer = {
            when (path) {
                "/v1/voices" -> call.respondText("""{"voices":[{"voice_id":"v-9","name":"Rachel"},{"voice_id":"v-8","name":"Adam"}]}""", ContentType.Application.Json)
                "/v1/models" -> call.respondText(
                    """[{"model_id":"eleven_v3","name":"Eleven v3","can_do_text_to_speech":true},{"model_id":"eleven_multilingual_sts_v2","can_do_text_to_speech":false}]""",
                    ContentType.Application.Json,
                )
                else -> stream(ByteArray(4))
            }
        }
        val choices = runBlocking { ElevenLabsVoice.choices(server.http, "xi-test", base) }
        assertEquals(listOf(VoiceOption("v-9", "Rachel"), VoiceOption("v-8", "Adam")), choices.voices)
        assertEquals(listOf(VoiceOption("eleven_v3", "Eleven v3")), choices.models)
        say(ElevenLabsVoice(server.http, "xi-test", base))
        assertEquals("/v1/text-to-speech/v-9/stream", path)
    }

    @Test
    fun `ElevenLabs' refusal says its own words`() {
        answer = { call.respondText("""{"detail":{"status":"invalid_api_key","message":"Invalid API key"}}""", ContentType.Application.Json, HttpStatusCode.Unauthorized) }
        val failed = refused { say(ElevenLabsVoice(server.http, "xi-bad", base), request = VoiceRequest(voice = "v-1")) }
        assertEquals(SpeechFailure.KeyRefused, failed.failure)
        assertEquals("Invalid API key", failed.detail)
        answer = { call.respondText("""{"detail":[{"loc":["body","text"],"msg":"Field required","type":"missing"}]}""", ContentType.Application.Json, HttpStatusCode.UnprocessableEntity) }
        assertEquals("Field required", refused { say(ElevenLabsVoice(server.http, "xi-test", base), request = VoiceRequest(voice = "v-1")) }.detail)
    }

    @Test
    fun `Azure gets the sentence escaped into SSML with the voice and language, and its WAV is read as it streams`() {
        val pcm = ByteArray(96) { it.toByte() }
        answer = { stream(wav(pcm), piece = 5) }
        val (said, rates) = say(AzureVoice(server.http, "az-test", base), request = VoiceRequest(voice = "en-US-JennyNeural", language = "en-US"))
        assertArrayEquals(pcm, said)
        assertEquals(setOf(24_000), rates)
        assertEquals("/cognitiveservices/v1", path)
        assertEquals("az-test", headers["Ocp-Apim-Subscription-Key"])
        assertEquals("riff-24khz-16bit-mono-pcm", headers["X-Microsoft-OutputFormat"])
        assertEquals("application/ssml+xml", headers["Content-Type"]?.substringBefore(';'))
        assertEquals("iCode", headers["User-Agent"])
        assertEquals(
            """<speak xmlns="http://www.w3.org/2001/10/synthesis" version="1.0" xml:lang="en-US"><voice name="en-US-JennyNeural">Hello &amp; &lt;you&gt;.</voice></speak>""",
            body,
        )
    }

    @Test
    fun `Azure's voices are those for the language, a language alone taking each of its places`() {
        val list = """[{"ShortName":"en-US-JennyNeural","DisplayName":"Jenny","Locale":"en-US","Gender":"Female"},""" +
            """{"ShortName":"en-GB-RyanNeural","DisplayName":"Ryan","Locale":"en-GB","Gender":"Male"},""" +
            """{"ShortName":"de-DE-KatjaNeural","DisplayName":"Katja","Locale":"de-DE","Gender":"Female"}]"""
        answer = { call.respondText(list, ContentType.Application.Json) }
        val english = runBlocking { AzureVoice.choices(server.http, "az-test", "en", base) }
        assertEquals(listOf("en-US-JennyNeural", "en-GB-RyanNeural"), english.voices.map { it.id })
        assertEquals("Jenny · en-US · Female", english.voices.first().label)
        assertEquals("/cognitiveservices/voices/list", path)
        assertEquals("az-test", headers["Ocp-Apim-Subscription-Key"])
        assertEquals(listOf("de-DE-KatjaNeural"), runBlocking { AzureVoice.choices(server.http, "az-test", "de-DE", base) }.voices.map { it.id })
        answer = { call.respondBytes(ByteArray(0), status = HttpStatusCode.Unauthorized) }
        assertEquals(SpeechFailure.KeyRefused, refused { say(AzureVoice(server.http, "az-bad", base), request = VoiceRequest(voice = "en-US-JennyNeural")) }.failure)
    }

    @Test
    fun `an OpenAI-compatible server gets the Speech API's request for a WAV file, its rate read from it`() {
        val pcm = ByteArray(64) { it.toByte() }
        answer = { stream(wav(pcm, rate = 22_050)) }
        val (said, rates) = say(CompatibleVoice(server.http, "$base/v1/", "sk-local"), request = VoiceRequest(voice = "af_heart", model = "kokoro", speed = 1.5f))
        assertArrayEquals(pcm, said)
        assertEquals(setOf(22_050), rates)
        assertEquals("/v1/audio/speech", path)
        assertEquals("Bearer sk-local", headers["Authorization"])
        assertTrue(body, sameJson(JSONObject("""{"input":"Hello & <you>.","response_format":"wav","model":"kokoro","voice":"af_heart","speed":1.5}"""), JSONObject(body)))
    }

    @Test
    fun `no server says so in the network's words`() {
        val gone = CompatibleVoice(server.http, "http://127.0.0.1:1/v1", null)
        assertEquals(SpeechFailure.Unreachable, refused { say(gone) }.failure)
    }
}
