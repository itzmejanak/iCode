package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpokenText
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.Url
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/** What a streaming provider's frames say (docs/tts-plan.md §4.3): each adapter gives only its protocol. */
internal interface VoiceProtocol {
    /** What the provider is told when the socket opens, before any sentence. */
    val opening: List<String> get() = emptyList()

    /** One sentence at a time: the next is asked for once the last one's audio is all in. */
    val oneAtATime: Boolean get() = false

    /** Sent once everything is said, so the session ends cleanly; null: the socket just closes. */
    val closing: String?

    /** The frames that ask for [text]. */
    fun say(text: String): List<String>

    /** What a frame from the provider means; null: nothing to do. */
    fun heard(frame: Frame): Heard?

    /** Why the provider closed the socket with [code] and [reason]; null: a clean close. */
    fun closed(code: Int?, reason: String?): ProviderFailed?

    sealed interface Heard {
        class Audio(val pcm: ByteArray) : Heard

        /** All of a sentence's audio is in. */
        data object Said : Heard

        class Failed(val failure: ProviderFailed) : Heard
    }
}

/**
 * The socket runner (docs/tts-plan.md §4.3): one WebSocket per speech session, opened at once — while
 * the model still thinks — each sentence sent as it comes and its audio played as it arrives, so the
 * next is being made while one plays. A socket the provider closes while there is nothing to say is
 * opened again for the next sentence. Hushed, the socket closes at once, and with it the provider's
 * work. A failure says so once what came before it is heard.
 */
internal class SocketVoice(
    private val http: HttpClient,
    private val url: (VoiceRequest) -> String,
    private val headers: HttpRequestBuilder.() -> Unit,
    private val protocol: (VoiceRequest) -> VoiceProtocol,
    private val rate: Int,
    private val output: () -> VoiceOutput,
) : VoiceEngine {
    override suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit) = coroutineScope {
        val audio = Channel<Play>(Channel.UNLIMITED)
        val player = launch { play(audio, playing) }
        val parts = Channel<String>(Channel.UNLIMITED)
        launch {
            try {
                for (sentence in sentences) SpokenText.parts(sentence, LONGEST).forEach { parts.send(it) }
            } finally {
                parts.close()
            }
        }
        val address = url(request)
        val failure = try {
            var next: String? = null
            // Each pass is one socket: it ends with everything said, or closed by the provider with nothing left to say.
            while (true) {
                var done = false
                http.webSocket(address, headers) { done = converse(protocol(request), next, parts, audio) }
                if (done) break
                next = parts.receiveCatching().getOrNull() ?: break
            }
            null
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (failed: ProviderFailed) {
            failed
        } catch (failure: Exception) {
            SpeechSocket.failureOf(failure, Url(address).host).let { ProviderFailed(it.failure, it.detail) }
        }
        audio.close()
        player.join()
        if (failure != null) throw failure
    }

    /**
     * One socket's share of the session, [first] the sentence that opened it again: true once every
     * sentence is said, false when the provider closed it cleanly with nothing left to say. A sentence
     * is taken only when it can be sent, so none is lost to a close.
     */
    private suspend fun DefaultClientWebSocketSession.converse(protocol: VoiceProtocol, first: String?, parts: ReceiveChannel<String>, audio: Channel<Play>): Boolean =
        coroutineScope {
            val events = Channel<Event>(Channel.UNLIMITED)
            val reader = launch {
                for (frame in incoming) protocol.heard(frame)?.let { events.send(Event.Provider(it)) }
                events.send(Event.Closed)
            }
            val waiting = ArrayDeque<String>()
            var asked = 0
            var more = true
            var samples = WholeSamples()
            suspend fun ask(text: String) {
                if (protocol.oneAtATime && asked > 0) {
                    waiting += text
                    return
                }
                protocol.say(text).forEach { send(Frame.Text(it)) }
                asked++
            }
            try {
                protocol.opening.forEach { send(Frame.Text(it)) }
                first?.let { ask(it) }
                while (more || asked > 0 || waiting.isNotEmpty()) {
                    val event = select {
                        if (more) parts.onReceiveCatching { part -> part.getOrNull()?.let { Event.Sentence(it) } ?: Event.NoMore }
                        events.onReceive { it }
                    }
                    when (event) {
                        is Event.Sentence -> ask(event.text)
                        Event.NoMore -> more = false
                        is Event.Provider -> when (val heard = event.heard) {
                            is VoiceProtocol.Heard.Audio -> audio.send(Play.Audio(samples.of(heard.pcm)))
                            VoiceProtocol.Heard.Said -> {
                                asked--
                                samples = WholeSamples()
                                waiting.removeFirstOrNull()?.let { ask(it) }
                                if (asked == 0) audio.send(Play.Idle)
                            }
                            is VoiceProtocol.Heard.Failed -> throw heard.failure
                        }
                        Event.Closed -> {
                            val reason = closeReason.await()
                            protocol.closed(reason?.code?.toInt(), reason?.message)?.let { throw it }
                            if (asked > 0) throw ProviderFailed(SpeechFailure.Server, "It closed before the answer was read")
                            return@coroutineScope false
                        }
                    }
                }
                protocol.closing?.let { send(Frame.Text(it)) }
                true
            } finally {
                reader.cancel()
            }
        }

    /** Plays [audio] as it comes: heard out, and quiet, whenever nothing more is asked for. */
    private suspend fun play(audio: ReceiveChannel<Play>, playing: (Boolean) -> Unit) {
        val player = output()
        var sounding = false
        fun sound(now: Boolean) {
            if (sounding == now) return
            sounding = now
            playing(now)
        }
        try {
            for (item in audio) {
                when (item) {
                    is Play.Audio -> if (item.pcm.isNotEmpty()) {
                        sound(true)
                        player.play(item.pcm, rate)
                    }
                    Play.Idle -> if (audio.isEmpty) {
                        player.drain()
                        if (audio.isEmpty) sound(false)
                    }
                }
            }
            player.drain()
        } finally {
            player.close()
            sound(false)
        }
    }

    private sealed interface Event {
        class Sentence(val text: String) : Event
        data object NoMore : Event
        class Provider(val heard: VoiceProtocol.Heard) : Event
        data object Closed : Event
    }

    private sealed interface Play {
        class Audio(val pcm: ByteArray) : Play

        /** Nothing more is asked for now. */
        data object Idle : Play
    }

    private companion object {
        /** iCode's cap on one sentence's text: inside every streaming provider's own. */
        const val LONGEST = 1_000
    }
}
