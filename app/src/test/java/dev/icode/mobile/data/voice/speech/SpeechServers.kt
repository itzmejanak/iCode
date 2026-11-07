package dev.icode.mobile.data.voice.speech

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import io.ktor.server.websocket.WebSockets as ServerWebSockets

/** A provider's server on this machine, for its adapter's tests, and the client the app's engines use. */
internal class SpeechServer(routes: Application.() -> Unit) : AutoCloseable {
    private val server: EmbeddedServer<*, *> = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        install(ServerWebSockets)
        routes()
    }.start(wait = false)
    val port = runBlocking { server.engine.resolvedConnectors().single().port }
    val http = HttpClient(OkHttp) {
        install(WebSockets)
        install(HttpTimeout)
    }

    override fun close() {
        http.close()
        server.stop(0, 0)
    }
}

/** What a test's server heard from the client: its texts, and how much audio. */
internal class ClientSaid {
    val texts = mutableListOf<String>()
    @Volatile var audio = 0

    /** Reads until a text containing [text] comes; binary frames count as audio, [audioOf] reads it out of a text. */
    suspend fun DefaultWebSocketServerSession.until(text: String, audioOf: (String) -> Boolean = { false }) {
        for (frame in incoming) {
            when (frame) {
                is Frame.Binary -> audio++
                is Frame.Text -> {
                    val said = frame.readText()
                    if (audioOf(said)) {
                        audio++
                    } else {
                        synchronized(texts) { texts += said }
                        if (said.contains(text)) return
                    }
                }
                else -> Unit
            }
        }
    }

    fun texts(): List<String> = synchronized(texts) { texts.toList() }
}

/** A microphone that says [frames] frames of [bytes] each, then waits. */
internal fun frames(frames: Int, bytes: Int, sample: Short = 0) = SpeechAudio {
    flow {
        repeat(frames) { emit(pcm(bytes, sample)) }
        awaitCancellation()
    }
}

/** [bytes] of 16-bit PCM, alternating ±[sample]: its level is [sample]. */
internal fun pcm(bytes: Int, sample: Short): ByteArray = ByteArray(bytes).also { out ->
    for (index in 0 until bytes / 2) {
        val value = if (index % 2 == 0) sample.toInt() else -sample
        out[index * 2] = value.toByte()
        out[index * 2 + 1] = (value shr 8).toByte()
    }
}

/** Whether [a] and [b] are the same JSON, whatever the order of their keys. */
internal fun sameJson(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() &&
        a.keys().asSequence().all { sameJson(a.get(it), b.get(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameJson(a.get(it), b.get(it)) }
    a is Number && b is Number -> a.toDouble() == b.toDouble()
    else -> a == b
}
