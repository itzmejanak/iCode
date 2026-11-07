package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.assistant.networkWords
import dev.icode.mobile.domain.voice.SpeechFailure
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.Url
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One hearing over a WebSocket, the same for every streaming provider (docs/speech-plan.md §4.3): the
 * microphone's audio goes up as it comes, what comes down is the provider's to read, and the hearing
 * ends at the first final words or failure — or when a deadline the provider set passes. Each adapter
 * gives only its protocol: how audio is framed, what a message means, what its close codes say.
 */
internal class SpeechSocket private constructor(private val session: DefaultClientWebSocketSession) {
    private var frames: suspend (Frame) -> SpeechEvent? = { null }
    private var whenDone: suspend () -> Unit = {}
    private var late: () -> SpeechEvent = { SpeechEvent.Failed(SpeechFailure.Server) }
    private var closed: (Int?, String?) -> SpeechEvent = { _, _ -> SpeechEvent.Failed(SpeechFailure.Server) }
    private var ending: suspend () -> Unit = {}
    private var deadline = Long.MAX_VALUE

    /** What a frame from the provider means: words so far, final words, a failure, or nothing. */
    fun onFrame(handler: suspend (Frame) -> SpeechEvent?) {
        frames = handler
    }

    /** The user is done talking. */
    fun onDone(handler: suspend () -> Unit) {
        whenDone = handler
    }

    /** A deadline set by [endBy] passed. */
    fun onLate(handler: () -> SpeechEvent) {
        late = handler
    }

    /** The provider closed the socket, with its code and reason. */
    fun onClosed(handler: (code: Int?, reason: String?) -> SpeechEvent) {
        closed = handler
    }

    /** The hearing is over: what the provider is told before the socket closes. */
    fun onEnd(handler: suspend () -> Unit) {
        ending = handler
    }

    /** The hearing ends within [millis] at the latest. */
    fun endBy(millis: Long) {
        deadline = minOf(deadline, now() + millis)
    }

    suspend fun sendText(text: String) = sendQuietly(Frame.Text(text))

    /**
     * [frame] sent while the socket is open. Closed by the provider — Ktor closes what goes out with a
     * cancellation of its own — there is no one left to tell, and its close says why; this hearing's own
     * cancellation still stops it.
     */
    private suspend fun sendQuietly(frame: Frame) {
        try {
            session.send(frame)
        } catch (_: ClosedSendChannelException) {
            // Closed already.
        } catch (closed: CancellationException) {
            currentCoroutineContext().ensureActive()
        }
    }

    private sealed interface Signal {
        class Received(val frame: Frame) : Signal
        data object Closed : Signal
        data object Done : Signal
        data object Late : Signal
        data object Microphone : Signal
    }

    private suspend fun hear(audio: SpeechAudio, rate: Int, done: Deferred<Unit>, upload: (ByteArray) -> Frame, emit: suspend (SpeechEvent) -> Unit): SpeechEvent =
        coroutineScope {
            val signals = Channel<Signal>(Channel.UNLIMITED)
            val jobs = listOf(
                launch {
                    audio.frames(rate).catch { signals.send(Signal.Microphone) }.collect { pcm -> sendQuietly(upload(pcm)) }
                },
                launch {
                    for (frame in session.incoming) signals.send(Signal.Received(frame))
                    signals.send(Signal.Closed)
                },
                launch {
                    done.await()
                    signals.send(Signal.Done)
                },
            )
            try {
                var event: SpeechEvent? = null
                while (event == null) {
                    val signal = if (deadline == Long.MAX_VALUE) signals.receive() else withTimeoutOrNull((deadline - now()).coerceAtLeast(0)) { signals.receive() } ?: Signal.Late
                    event = when (signal) {
                        is Signal.Received -> frames(signal.frame)?.let { said -> if (said.ends) said else null.also { emit(said) } }
                        Signal.Closed -> session.closeReason.await().let { closed(it?.code?.toInt(), it?.message) }
                        Signal.Done -> null.also { whenDone() }
                        Signal.Late -> late()
                        Signal.Microphone -> SpeechEvent.Failed(SpeechFailure.Microphone)
                    }
                }
                ending()
                event
            } finally {
                jobs.forEach { it.cancel() }
            }
        }

    companion object {
        /** The final words or a failure: what ends a hearing. */
        val SpeechEvent.ends: Boolean get() = this is SpeechEvent.Failed || (this as SpeechEvent.Words).final

        /**
         * One hearing at [url], with [request]'s headers: its words so far go to [emit], and what ended it
         * is returned. [upload] frames the audio as the provider takes it.
         */
        suspend fun run(
            http: HttpClient,
            url: String,
            request: HttpRequestBuilder.() -> Unit,
            audio: SpeechAudio,
            rate: Int,
            done: Deferred<Unit>,
            emit: suspend (SpeechEvent) -> Unit,
            upload: (ByteArray) -> Frame = { Frame.Binary(true, it) },
            setup: SpeechSocket.() -> Unit,
        ): SpeechEvent {
            var outcome: SpeechEvent? = null
            try {
                http.webSocket(url, request) {
                    outcome = SpeechSocket(this).apply(setup).hear(audio, rate, done, upload, emit)
                }
            } catch (stopped: CancellationException) {
                throw stopped
            } catch (failure: Exception) {
                return failureOf(failure, Url(url).host)
            }
            return outcome ?: SpeechEvent.Failed(SpeechFailure.Server)
        }

        /** A connection that failed: the network's words, or the status the provider answered the handshake with. */
        fun failureOf(failure: Throwable, host: String): SpeechEvent.Failed {
            val causes = generateSequence(failure) { it.cause }.toList()
            causes.firstNotNullOfOrNull { networkWords(it, host) }?.let { return SpeechEvent.Failed(SpeechFailure.Unreachable, it) }
            val status = causes.firstNotNullOfOrNull { cause -> STATUS.find(cause.message.orEmpty())?.groupValues?.get(1)?.toInt() }
            return when (status) {
                null -> SpeechEvent.Failed(SpeechFailure.Failed, failure.javaClass.simpleName)
                else -> SpeechEvent.Failed(httpFailure(status), "HTTP $status")
            }
        }

        /** An HTTP status, as every provider's means it. */
        fun httpFailure(status: Int): SpeechFailure = when (status) {
            401, 403 -> SpeechFailure.KeyRefused
            402 -> SpeechFailure.NoCredit
            429 -> SpeechFailure.Busy
            413 -> SpeechFailure.TooLong
            in 500..599 -> SpeechFailure.Server
            else -> SpeechFailure.Failed
        }

        /** The handshake's status in OkHttp's words ("Expected HTTP 101 response but was '401 …'") and Ktor's. */
        private val STATUS = Regex("""but was '?(\d{3})""")

        private fun now() = System.nanoTime() / 1_000_000
    }
}
