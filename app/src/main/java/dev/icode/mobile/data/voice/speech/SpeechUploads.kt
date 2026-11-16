package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechRules
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.util.Base64
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

// The providers that answer once the user stopped (docs/voice-and-phone-control.md): each takes the hearing's
// audio in one request and answers its words, or why not, in the shared failures.

/**
 * A server that speaks OpenAI's `/audio/transcriptions`: the hearing as a WAV file, its words
 * back as JSON. [address] is up to and including `/v1`, as for the assistant's OpenAI-compatible providers.
 */
internal class OpenAiTranscription(private val http: HttpClient, private val address: String, private val key: String?) : SpeechUpload {
    override suspend fun transcribe(pcm: ByteArray, rate: Int, request: SpeechRequest): SpeechEvent {
        val url = address.trimEnd('/') + "/audio/transcriptions"
        return answered(url) {
            http.submitFormWithBinaryData(
                url,
                formData {
                    append(
                        "file", UploadSpeech.wav(pcm, rate),
                        Headers.build {
                            append(HttpHeaders.ContentType, "audio/wav")
                            append(HttpHeaders.ContentDisposition, "filename=\"speech.wav\"")
                        },
                    )
                    request.model?.let { append("model", it) }
                    request.language?.let { append("language", SpeechRules.languageCode(it)) }
                    append("response_format", "json")
                },
            ) {
                key?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                waits()
            }
        }.let { (status, body) ->
            if (status in 200..299) SpeechEvent.Words(body.optString("text").trim(), final = true)
            else SpeechEvent.Failed(SpeechSocket.httpFailure(status), body.optJSONObject("error")?.optString("message")?.ifBlank { null } ?: "HTTP $status")
        }
    }
}

/**
 * Google Cloud Speech-to-Text v1's `speech:recognize`: the hearing as LINEAR16, base64, the key
 * in `X-Goog-Api-Key` — never in the address. Google needs a language: the phone's when none is set.
 */
internal class GoogleRecognition(private val http: HttpClient, private val key: String, private val endpoint: String = ENDPOINT) : SpeechUpload {
    override suspend fun transcribe(pcm: ByteArray, rate: Int, request: SpeechRequest): SpeechEvent {
        val config = JSONObject()
            .put("encoding", "LINEAR16")
            .put("sampleRateHertz", rate)
            .put("languageCode", request.language ?: Locale.getDefault().toLanguageTag())
            .put("enableAutomaticPunctuation", true)
            .apply { request.model?.let { put("model", it) } }
        val body = JSONObject().put("config", config).put("audio", JSONObject().put("content", Base64.getEncoder().encodeToString(pcm)))
        return answered(endpoint) {
            http.post(endpoint) {
                header(KEY_HEADER, key)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
                waits()
            }
        }.let { (status, answer) ->
            if (status in 200..299) {
                val results = answer.optJSONArray("results")
                val words = (0 until (results?.length() ?: 0)).mapNotNull { index ->
                    results?.optJSONObject(index)?.optJSONArray("alternatives")?.optJSONObject(0)?.optString("transcript")?.trim()?.ifBlank { null }
                }
                SpeechEvent.Words(words.joinToString(" "), final = true)
            } else {
                googleFailure(status, answer).let { SpeechEvent.Failed(it.failure, it.detail) }
            }
        }
    }

    internal companion object {
        const val ENDPOINT = "https://speech.googleapis.com/v1/speech:recognize"
        const val KEY_HEADER = "X-Goog-Api-Key"
    }
}

/** A request's status and its JSON; one that never reached its provider throws [ProviderFailed], with the network's words. */
internal suspend fun answered(url: String, send: suspend () -> HttpResponse): Pair<Int, JSONObject> {
    val response = reached(url, send)
    val text = response.bodyAsText()
    return response.status.value to (runCatching { JSONObject(text) }.getOrNull() ?: JSONObject())
}

/** [send]'s response; one that never reached its provider throws [ProviderFailed], with the network's words. */
internal suspend fun <T> reached(url: String, send: suspend () -> T): T = try {
    send()
} catch (stopped: CancellationException) {
    throw stopped
} catch (failed: ProviderFailed) {
    throw failed
} catch (failure: Exception) {
    SpeechSocket.failureOf(failure, Url(url).host).let { throw ProviderFailed(it.failure, it.detail) }
}

/**
 * Google's refusal: a key it cannot read is a 400, not a 401, and its reason says so (`API_KEY_INVALID`);
 * else its status, in its own words.
 */
internal fun googleFailure(status: Int, answer: JSONObject): ProviderFailed {
    val error = answer.optJSONObject("error")
    val badKey = error?.toString()?.contains("API_KEY_INVALID") == true
    return ProviderFailed(if (badKey) SpeechFailure.KeyRefused else SpeechSocket.httpFailure(status), error?.optString("message")?.ifBlank { null } ?: "HTTP $status")
}

/** A provider's audio, or its words, may take a while to send and to come. */
internal fun HttpRequestBuilder.waits() = timeout {
    requestTimeoutMillis = 120_000
    socketTimeoutMillis = 60_000
}
