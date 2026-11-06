package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.Heard
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.VoiceFrom
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One hearing at a time, whichever engine hears it (docs/speech-plan.md §4.1). */
class SpeechHearingsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val heard = MutableStateFlow<Heard?>(null)
    private val engine = FakeEngine()
    private var refusal: String? = null
    private var use: Result<SpeechUse> = Result.success(SpeechUse(SpeechProvider.Android, engine, SpeechRequest(language = "en-US")))
    private val endings = mutableListOf<Pair<String, SpeechFailure?>>()
    private val hearings = SpeechHearings(
        scope,
        refusal = { refusal },
        use = { use },
        heard = { change -> synchronized(heard) { heard.value = change(heard.value) } },
        ended = { provider, failure -> synchronized(endings) { endings += provider.id to failure } },
    )

    @After
    fun stop() = scope.cancel()

    private fun until(check: (Heard?) -> Boolean): Heard? = runBlocking { withTimeout(5_000) { heard.first(check) } }

    @Test
    fun `the words come as they are heard, then final, and the request is the engine's`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        assertEquals("en-US", engine.request?.language)
        engine.say(SpeechEvent.Words("turn on", final = false))
        val partial = until { it?.text == "turn on" }
        assertEquals(Heard(partial!!.id, VoiceFrom.Bubble, "turn on", final = false), partial)
        engine.say(SpeechEvent.Words("turn on the light", final = true))
        val last = until { it?.final == true }
        assertEquals("turn on the light", last?.sent)
        runBlocking { withTimeout(5_000) { while (synchronized(endings) { endings.isEmpty() }) kotlinx.coroutines.delay(5) } }
        assertEquals(listOf(SpeechProvider.ANDROID to null), endings)
    }

    @Test
    fun `done tells the engine the user is done`() {
        hearings.listen(VoiceFrom.Chat)
        engine.started()
        hearings.finish()
        runBlocking { withTimeout(5_000) { engine.done!!.await() } }
    }

    @Test
    fun `a failure is said in the shared words, naming the provider`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        engine.say(SpeechEvent.Failed(SpeechFailure.Busy))
        assertEquals("Speech recognition is busy: try again", until { it?.final == true }?.problem)
    }

    @Test
    fun `an engine that ends without its final words makes what it heard final`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        engine.say(SpeechEvent.Words("open the door", final = false))
        engine.events.close()
        val last = until { it?.final == true }
        assertEquals("open the door", last?.sent)
    }

    @Test
    fun `nothing heard says so`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        engine.say(SpeechEvent.Words("", final = true))
        assertEquals("Nothing was heard", until { it?.final == true }?.problem)
    }

    @Test
    fun `a refusal ends the hearing before any engine starts`() {
        refusal = "Open iCode once to use the microphone"
        hearings.listen(VoiceFrom.Bubble)
        assertEquals("Open iCode once to use the microphone", until { it?.final == true }?.problem)
        assertEquals(0, engine.calls)
    }

    @Test
    fun `no provider to use says why`() {
        use = Result.failure(IllegalStateException("Enter AssemblyAI's key in Settings › Assistant › Voice"))
        hearings.listen(VoiceFrom.Chat)
        assertEquals("Enter AssemblyAI's key in Settings › Assistant › Voice", until { it?.final == true }?.problem)
        assertEquals(0, engine.calls)
    }

    @Test
    fun `stopped, the engine stops and nothing heard is kept`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        engine.say(SpeechEvent.Words("half a", final = false))
        until { it?.text == "half a" }
        hearings.stop()
        runBlocking { withTimeout(5_000) { engine.closed.await() } }
        assertNull(heard.value)
    }

    @Test
    fun `a later tap leaves the earlier hearing's words out`() {
        hearings.listen(VoiceFrom.Bubble)
        engine.started()
        val first = engine.events
        engine.reset()
        hearings.listen(VoiceFrom.Chat)
        engine.started()
        first.trySend(SpeechEvent.Words("late words", final = true))
        engine.say(SpeechEvent.Words("new words", final = true))
        val last = until { it?.final == true }
        assertEquals(VoiceFrom.Chat, last?.from)
        assertEquals("new words", last?.text)
        assertTrue(heard.value?.text != "late words")
    }

    /** An engine the test speaks for. */
    private class FakeEngine : SpeechEngine {
        @Volatile var events = Channel<SpeechEvent>(Channel.UNLIMITED)
        @Volatile var request: SpeechRequest? = null
        @Volatile var done: Deferred<Unit>? = null
        @Volatile var calls = 0
        @Volatile private var began = CompletableDeferred<Unit>()
        @Volatile var closed = CompletableDeferred<Unit>()

        override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> {
            val mine = events
            val close = closed
            val begin = began
            return channelFlow {
                calls++
                this@FakeEngine.request = request
                this@FakeEngine.done = done
                begin.complete(Unit)
                launch {
                    for (event in mine) send(event)
                    this@channelFlow.close()
                }
                awaitClose { close.complete(Unit) }
            }
        }

        fun started() = runBlocking { withTimeout(5_000) { began.await() } }

        fun say(event: SpeechEvent) {
            events.trySend(event)
        }

        fun reset() {
            events = Channel(Channel.UNLIMITED)
            began = CompletableDeferred()
            closed = CompletableDeferred()
        }
    }
}
