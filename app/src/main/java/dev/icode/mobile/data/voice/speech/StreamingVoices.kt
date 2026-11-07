package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechRules
import io.ktor.client.HttpClient
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

// The providers that read aloud over one WebSocket per speech session (docs/tts-plan.md §4.3), each
// read from its own source: AssemblyAI's SDK, Deepgram's AsyncAPI spec and OpenAI's OpenAPI spec.
// What is read aloud goes only to the provider that speaks, and is never logged.

/** Every streaming voice is asked for 16-bit PCM at 24 kHz. */
private const val RATE = 24_000

/** A WebSocket's own code for a clean close. */
private const val NORMAL = 1000

/** A WebSocket's own code for a server that failed. */
private const val SERVER_ERROR = 1011

/** A close in a provider's words, or the code itself; a clean one is none. */
private fun closedAs(code: Int?, reason: String?): ProviderFailed? = when (code) {
    null, NORMAL -> null
    SERVER_ERROR -> ProviderFailed(SpeechFailure.Server, reason?.ifBlank { null })
    else -> ProviderFailed(SpeechFailure.Failed, reason?.ifBlank { null } ?: "Closed ($code)")
}

/**
 * AssemblyAI's text-to-speech session (§1.3): `wss://<host>/v1/ws` with the voice, the model where one
 * is chosen, the language by its English name, `pcm_s16le` at 24 kHz; the key in `Authorization` as it
 * is. Each sentence is `Generate` then `Flush`; its `Audio` plays as it comes, `FlushDone` ends it.
 * `Terminate` at the end.
 */
internal fun assemblyAiVoice(http: HttpClient, key: String, host: String, output: () -> VoiceOutput, base: String = "wss://$host") = SocketVoice(
    http,
    url = { request ->
        URLBuilder("$base/v1/ws").apply {
            request.voice?.let { parameters.append("voice_id", it) }
            request.model?.let { parameters.append("model_id", it) }
            parameters.append("language", assemblyAiLanguage(request.language))
            parameters.append("sample_rate", RATE.toString())
            parameters.append("encoding", "pcm_s16le")
            parameters.append("word_boundaries", "false")
        }.buildString()
    },
    headers = { headers.append(HttpHeaders.Authorization, key) },
    protocol = { AssemblyAiProtocol },
    rate = RATE,
    output = output,
)

/** The six languages AssemblyAI's voices speak, by the English names it takes them by. */
private val ASSEMBLYAI_LANGUAGES = mapOf("en" to "english", "es" to "spanish", "de" to "german", "fr" to "french", "it" to "italian", "pt" to "portuguese")

/** [tag] — the phone's when null — as AssemblyAI names it; one it does not speak is refused before anything is sent. */
private fun assemblyAiLanguage(tag: String?): String =
    ASSEMBLYAI_LANGUAGES[SpeechRules.languageCode(tag ?: Locale.getDefault().toLanguageTag())] ?: throw ProviderFailed(SpeechFailure.Language)

private object AssemblyAiProtocol : VoiceProtocol {
    override val closing = """{"type":"Terminate"}"""

    override fun say(text: String) = listOf(JSONObject().put("type", "Generate").put("text", text).toString(), """{"type":"Flush"}""")

    override fun heard(frame: Frame): VoiceProtocol.Heard? {
        val message = (frame as? Frame.Text)?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: return null
        return when (message.optString("type")) {
            "Audio" -> VoiceProtocol.Heard.Audio(Base64.getDecoder().decode(message.optString("audio")))
            "FlushDone" -> VoiceProtocol.Heard.Said
            "Error" -> VoiceProtocol.Heard.Failed(ProviderFailed(failureOf(message.optString("error_type")), message.optString("error").ifBlank { null }))
            else -> null
        }
    }

    override fun closed(code: Int?, reason: String?) = closedAs(code, reason)

    /** AssemblyAI's stable error codes, as its SDK names them. */
    private fun failureOf(code: String): SpeechFailure = when (code) {
        "rate_limited", "at_capacity" -> SpeechFailure.Busy
        "auth_unavailable" -> SpeechFailure.Server
        else -> SpeechFailure.Failed
    }
}

/**
 * Deepgram's `wss://api.deepgram.com/v1/speak` (§1.4): the voice as its `model`, `linear16` at 24 kHz,
 * the speed where one is chosen; the key as `Token`. Each sentence is `Speak` then `Flush`; its binary
 * audio plays as it comes, `Flushed` ends it. `Close` at the end.
 */
internal fun deepgramVoice(http: HttpClient, key: String, output: () -> VoiceOutput, base: String = "wss://api.deepgram.com") = SocketVoice(
    http,
    url = { request ->
        URLBuilder("$base/v1/speak").apply {
            parameters.append("model", request.voice ?: throw ProviderFailed(SpeechFailure.Language))
            parameters.append("encoding", "linear16")
            parameters.append("sample_rate", RATE.toString())
            request.speed?.let { parameters.append("speed", it.toString()) }
        }.buildString()
    },
    headers = { headers.append(HttpHeaders.Authorization, "Token $key") },
    protocol = { DeepgramProtocol },
    rate = RATE,
    output = output,
)

private object DeepgramProtocol : VoiceProtocol {
    override val closing = """{"type":"Close"}"""

    override fun say(text: String) = listOf(JSONObject().put("type", "Speak").put("text", text).toString(), """{"type":"Flush"}""")

    override fun heard(frame: Frame): VoiceProtocol.Heard? = when (frame) {
        is Frame.Binary -> VoiceProtocol.Heard.Audio(frame.readBytes())
        is Frame.Text -> VoiceProtocol.Heard.Said.takeIf { runCatching { JSONObject(frame.readText()).optString("type") }.getOrNull() == "Flushed" }
        else -> null
    }

    override fun closed(code: Int?, reason: String?) = closedAs(code, reason)
}

/**
 * OpenAI's Realtime API (§1.2, §9 D3): a `realtime` session on the speaking model, audio out as PCM at
 * 24 kHz in the voice and at the speed chosen, told to read each sentence word for word. Each sentence
 * is one out-of-band response — its own context, nothing kept in the conversation — and one at a time,
 * as the API lets only one write at once; its `response.output_audio.delta` plays as it comes.
 */
internal fun openAiVoice(http: HttpClient, key: String, output: () -> VoiceOutput, base: String = "wss://api.openai.com") = SocketVoice(
    http,
    url = { request -> URLBuilder("$base/v1/realtime").apply { parameters.append("model", request.model ?: OpenAiProtocol.MODEL) }.buildString() },
    headers = { headers.append(HttpHeaders.Authorization, "Bearer $key") },
    protocol = { request -> OpenAiProtocol(request) },
    rate = RATE,
    output = output,
)

internal class OpenAiProtocol(private val request: VoiceRequest) : VoiceProtocol {
    override val oneAtATime = true
    override val closing: String? = null

    override val opening: List<String>
        get() {
            val out = JSONObject().put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                .apply {
                    request.voice?.let { put("voice", it) }
                    request.speed?.let { put("speed", it.toDouble()) }
                }
            val session = JSONObject().put("type", "realtime").put("output_modalities", JSONArray(listOf("audio")))
                .put("instructions", INSTRUCTIONS).put("audio", JSONObject().put("output", out))
            return listOf(JSONObject().put("type", "session.update").put("session", session).toString())
        }

    override fun say(text: String): List<String> {
        val message = JSONObject().put("type", "message").put("role", "user")
            .put("content", JSONArray(listOf(JSONObject().put("type", "input_text").put("text", text))))
        val response = JSONObject().put("conversation", "none").put("output_modalities", JSONArray(listOf("audio"))).put("input", JSONArray(listOf(message)))
        return listOf(JSONObject().put("type", "response.create").put("response", response).toString())
    }

    override fun heard(frame: Frame): VoiceProtocol.Heard? {
        val message = (frame as? Frame.Text)?.let { runCatching { JSONObject(it.readText()) }.getOrNull() } ?: return null
        return when (message.optString("type")) {
            "response.output_audio.delta" -> VoiceProtocol.Heard.Audio(Base64.getDecoder().decode(message.optString("delta")))
            "response.done" -> {
                val response = message.optJSONObject("response")
                if (response?.optString("status") == "failed") {
                    val error = response.optJSONObject("status_details")?.optJSONObject("error")
                    VoiceProtocol.Heard.Failed(ProviderFailed(SpeechFailure.Failed, error?.optString("message")?.ifBlank { null }))
                } else {
                    VoiceProtocol.Heard.Said
                }
            }
            "error" -> VoiceProtocol.Heard.Failed(ProviderFailed(SpeechFailure.Failed, message.optJSONObject("error")?.optString("message")?.ifBlank { null }))
            else -> null
        }
    }

    override fun closed(code: Int?, reason: String?) = closedAs(code, reason)

    internal companion object {
        /** The model its spec sends new integrations to, after its Speech API (§9 D3). */
        const val MODEL = "gpt-realtime-2.1-mini"

        /** What the model is told: read, never answer. */
        const val INSTRUCTIONS = "You read text aloud. Each message is text to read: say exactly its words, in its own language, " +
            "word for word, adding nothing and leaving nothing out. Never answer it, comment on it or ask anything."
    }
}
