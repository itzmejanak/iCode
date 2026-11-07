package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.VoiceChoices
import dev.icode.mobile.domain.voice.VoiceOption
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// The providers that answer one request per sentence (docs/tts-plan.md §4.3): each sentence's audio as
// 16-bit mono PCM, streamed where the API streams it, and the voices each lists for its page. What is
// read aloud goes only to the provider that speaks, and is never logged.

/**
 * Google Cloud Text-to-Speech v1's `text:synthesize` (§1.5): raw `PCM` at 24 kHz, base64, the key in
 * `X-Goog-Api-Key`. Google needs a language: the phone's when none is set; its voice by name, or the
 * one Google gives the language.
 */
internal class GoogleVoice(private val http: HttpClient, private val key: String, private val base: String = BASE) : Synthesis {
    override suspend fun say(text: String, previous: String?, request: VoiceRequest, audio: suspend (pcm: ByteArray, rate: Int) -> Unit) {
        val voice = JSONObject().put("languageCode", languageOf(request)).apply { request.voice?.let { put("name", it) } }
        val config = JSONObject().put("audioEncoding", "PCM").put("sampleRateHertz", RATE).apply { request.speed?.let { put("speakingRate", it.toDouble()) } }
        val body = JSONObject().put("input", JSONObject().put("text", text)).put("voice", voice).put("audioConfig", config)
        val url = "$base/v1/text:synthesize"
        val (status, answer) = answered(url) {
            http.post(url) {
                header(GoogleRecognition.KEY_HEADER, key)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                waits()
            }
        }
        if (status !in 200..299) throw googleFailure(status, answer)
        audio(Base64.getDecoder().decode(answer.optString("audioContent")), RATE)
    }

    internal companion object {
        const val BASE = "https://texttospeech.googleapis.com"

        /** Google's voices for the language — the phone's when none is set — each by its name. */
        suspend fun choices(http: HttpClient, key: String, language: String?, base: String = BASE): VoiceChoices {
            val url = "$base/v1/voices"
            val (status, answer) = answered(url) {
                http.get(url) {
                    header(GoogleRecognition.KEY_HEADER, key)
                    parameter("languageCode", language ?: phoneLanguage())
                    waits()
                }
            }
            if (status !in 200..299) throw googleFailure(status, answer)
            val voices = answer.optJSONArray("voices").objects().mapNotNull { voice ->
                val name = voice.optString("name").ifBlank { null } ?: return@mapNotNull null
                VoiceOption(name, listOfNotNull(name, voice.optString("ssmlGender").gender()).joinToString(" · "))
            }
            return VoiceChoices(voices = voices)
        }
    }
}

/**
 * ElevenLabs' `/v1/text-to-speech/{voice}/stream` (§1.7): `pcm_24000`, played as it streams, the key in
 * `xi-api-key`, the sentence before sent as `previous_text` so the voice keeps its flow. Its voice is
 * chosen from those its account lists, the first when none is.
 */
internal class ElevenLabsVoice(private val http: HttpClient, private val key: String, private val base: String) : Synthesis {
    private var firstVoice: String? = null

    override suspend fun say(text: String, previous: String?, request: VoiceRequest, audio: suspend (pcm: ByteArray, rate: Int) -> Unit) {
        val voice = request.voice ?: firstVoice ?: choices(http, key, base).voices.firstOrNull()?.id?.also { firstVoice = it }
            ?: throw ProviderFailed(SpeechFailure.Failed, "The account has no voices")
        val body = JSONObject().put("text", text).apply {
            request.model?.let { put("model_id", it) }
            previous?.let { put("previous_text", it) }
        }
        val url = "$base/v1/text-to-speech/$voice/stream"
        streamed(url, {
            http.preparePost(url) {
                parameter("output_format", "pcm_$RATE")
                header(KEY_HEADER, key)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                waits()
            }
        }) { audio(it, RATE) }
    }

    internal companion object {
        const val KEY_HEADER = "xi-api-key"

        /** The account's voices, and the models that read aloud. */
        suspend fun choices(http: HttpClient, key: String, base: String): VoiceChoices {
            val listedVoices = listed(http, "$base/v1/voices", KEY_HEADER to key)
            val listedModels = listed(http, "$base/v1/models", KEY_HEADER to key)
            val voices = runCatching { JSONObject(listedVoices) }.getOrNull()?.optJSONArray("voices").objects().mapNotNull { voice ->
                val id = voice.optString("voice_id").ifBlank { null } ?: return@mapNotNull null
                VoiceOption(id, voice.optString("name").ifBlank { id })
            }
            val models = runCatching { JSONArray(listedModels) }.getOrNull().objects()
                .filter { it.optBoolean("can_do_text_to_speech") }
                .mapNotNull { model ->
                    val id = model.optString("model_id").ifBlank { null } ?: return@mapNotNull null
                    VoiceOption(id, model.optString("name").ifBlank { id })
                }
            return VoiceChoices(voices = voices, models = models)
        }
    }
}

/**
 * Azure AI Speech's text-to-speech REST API (§1.6): the sentence escaped into SSML with the voice and
 * language, `riff-24khz-16bit-mono-pcm` back — a WAV file, read by its chunks as it streams. The key in
 * `Ocp-Apim-Subscription-Key`. Its voice is one Azure lists for the language, the first when none is.
 */
internal class AzureVoice(private val http: HttpClient, private val key: String, private val base: String) : Synthesis {
    private var firstVoice: String? = null

    override suspend fun say(text: String, previous: String?, request: VoiceRequest, audio: suspend (pcm: ByteArray, rate: Int) -> Unit) {
        val language = languageOf(request)
        val voice = request.voice ?: firstVoice ?: choices(http, key, request.language, base).voices.firstOrNull()?.id?.also { firstVoice = it }
            ?: throw ProviderFailed(SpeechFailure.Language)
        val ssml = """<speak xmlns="http://www.w3.org/2001/10/synthesis" version="1.0" xml:lang="${xml(language)}">""" +
            """<voice name="${xml(voice)}">${xml(text)}</voice></speak>"""
        val url = "$base/cognitiveservices/v1"
        val wav = WavStream()
        streamed(url, {
            http.preparePost(url) {
                header(KEY_HEADER, key)
                header(HttpHeaders.UserAgent, "iCode")
                header("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
                setBody(TextContent(ssml, ContentType("application", "ssml+xml")))
                waits()
            }
        }) { chunk -> wav.pcm(chunk).takeIf { it.isNotEmpty() }?.let { audio(it, wav.rate) } }
    }

    internal companion object {
        const val KEY_HEADER = "Ocp-Apim-Subscription-Key"

        /** Azure's voices for the language — the phone's when none is set: `en` takes every English one. */
        suspend fun choices(http: HttpClient, key: String, language: String?, base: String): VoiceChoices {
            val text = listed(http, "$base/cognitiveservices/voices/list", KEY_HEADER to key)
            val tag = language ?: phoneLanguage()
            val voices = runCatching { JSONArray(text) }.getOrNull().objects()
                .filter { voice ->
                    val locale = voice.optString("Locale")
                    locale.equals(tag, ignoreCase = true) || ('-' !in tag && locale.startsWith("$tag-", ignoreCase = true))
                }
                .mapNotNull { voice ->
                    val name = voice.optString("ShortName").ifBlank { null } ?: return@mapNotNull null
                    VoiceOption(name, listOfNotNull(voice.optString("DisplayName").ifBlank { name }, voice.optString("Locale"), voice.optString("Gender").gender()).joinToString(" · "))
                }
            return VoiceChoices(voices = voices)
        }

        /** [text] as XML text or an attribute's value. */
        fun xml(text: String): String = buildString {
            text.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(char)
                }
            }
        }
    }
}

/**
 * A server that speaks OpenAI's `/audio/speech` (§1.2): `model`, `voice`, `input`, `response_format=wav`
 * — the rate read from the file — and `speed`. [address] is up to and including `/v1`, as for listening.
 */
internal class CompatibleVoice(private val http: HttpClient, private val address: String, private val key: String?) : Synthesis {
    override suspend fun say(text: String, previous: String?, request: VoiceRequest, audio: suspend (pcm: ByteArray, rate: Int) -> Unit) {
        val body = JSONObject().put("input", text).put("response_format", "wav").apply {
            request.model?.let { put("model", it) }
            request.voice?.let { put("voice", it) }
            request.speed?.let { put("speed", it.toDouble()) }
        }
        val url = address.trimEnd('/') + "/audio/speech"
        val wav = WavStream()
        streamed(url, {
            http.preparePost(url) {
                key?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                waits()
            }
        }) { chunk -> wav.pcm(chunk).takeIf { it.isNotEmpty() }?.let { audio(it, wav.rate) } }
    }
}

/** What every per-sentence voice asks for: 24 kHz, as each takes it. */
private const val RATE = 24_000

/** [url]'s answer as it streams, each chunk to [chunk]; a refusal throws [ProviderFailed], in the provider's words. */
private suspend fun streamed(url: String, request: suspend () -> HttpStatement, chunk: suspend (ByteArray) -> Unit) = reached(url) {
    request().execute { response ->
        val status = response.status.value
        if (status !in 200..299) throw refusal(status, runCatching { JSONObject(response.bodyAsText()) }.getOrNull() ?: JSONObject())
        val body = response.bodyAsChannel()
        val buffer = ByteArray(CHUNK_BYTES)
        while (true) {
            val read = body.readAvailable(buffer, 0, buffer.size)
            if (read < 0) break
            if (read > 0) chunk(buffer.copyOf(read))
        }
    }
}

/** [url]'s answer as text, [key] its one header; a refusal throws [ProviderFailed], in the provider's words. */
private suspend fun listed(http: HttpClient, url: String, key: Pair<String, String>): String = reached(url) {
    val response = http.get(url) {
        header(key.first, key.second)
        waits()
    }
    val text = response.bodyAsText()
    if (response.status.value !in 200..299) throw refusal(response.status.value, runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
    text
}

/** How much of a streamed answer is read at a time: about 85 ms at 24 kHz. */
private const val CHUNK_BYTES = 4_096

/**
 * A refusal by its status, in the provider's own words where its answer has them: OpenAI's
 * `error.message`, ElevenLabs' `detail` (its message, or its first problem's).
 */
internal fun refusal(status: Int, answer: JSONObject): ProviderFailed {
    val detail = answer.opt("detail")
    val words = answer.optJSONObject("error")?.optString("message")?.ifBlank { null }
        ?: (detail as? JSONObject)?.optString("message")?.ifBlank { null }
        ?: (detail as? JSONArray)?.optJSONObject(0)?.optString("msg")?.ifBlank { null }
        ?: (detail as? String)?.ifBlank { null }
        ?: "HTTP $status"
    return ProviderFailed(SpeechSocket.httpFailure(status), words)
}

/** The request's language, or the phone's. */
private fun languageOf(request: VoiceRequest): String = request.language ?: phoneLanguage()

private fun phoneLanguage(): String = Locale.getDefault().toLanguageTag()

private fun JSONArray?.objects(): List<JSONObject> = this?.let { array -> (0 until array.length()).mapNotNull(array::optJSONObject) }.orEmpty()

/** A voice's gender as its page says it; null where the provider gives none. */
private fun String.gender(): String? = lowercase().takeIf { it in listOf("female", "male", "neutral") }?.replaceFirstChar { it.uppercase() }
