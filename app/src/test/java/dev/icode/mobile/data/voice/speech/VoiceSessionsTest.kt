package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One speech session at a time, whichever engine says it (docs/voice-and-phone-control.md). */
class VoiceSessionsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val voice = FakeVoice()
    private val google = SpeechProvider("s-1", SpeechProviderKind.Google, "Google Cloud", hasKey = true)
    private var use: Result<VoiceUse> = Result.success(VoiceUse(SpeechProvider.Android, voice, VoiceRequest(voice = "aai-jane")))
    private val shown = mutableListOf<String>()
    private val sessions = VoiceSessions(
        scope,
        use = { use },
        began = { reading -> record("began $reading") },
        playing = { playing -> record("playing $playing") },
        ended = { failure -> record("ended $failure") },
    )

    private val original = BackendLog.writer
    private val logged = Collections.synchronizedList(mutableListOf<String>())

    init {
        BackendLog.writer = { _, _, message -> logged += message }
    }

    @After
    fun stop() {
        scope.cancel()
        BackendLog.writer = original
    }

    private fun record(what: String) = synchronized(shown) { shown += what }

    private fun until(what: String) = runBlocking { withTimeout(5_000) { while (synchronized(shown) { what !in shown }) delay(5) } }

    @Test
    fun `the sentences are said in order with the request, and the session ends once they are`() {
        sessions.begin()
        sessions.say("One.")
        sessions.say("Two.")
        sessions.end()
        until("ended null")
        assertEquals(listOf("One.", "Two."), voice.said)
        assertEquals(VoiceRequest(voice = "aai-jane"), voice.request)
        assertEquals(listOf("began null", "playing true", "playing false", "ended null"), shown)
    }

    @Test
    fun `a sentence without a session begins one, and what is read on request is named`() {
        sessions.say("Hello.")
        sessions.end()
        until("ended null")
        sessions.begin(reading = "m-1")
        sessions.say("Again.")
        sessions.end()
        runBlocking { withTimeout(5_000) { while (synchronized(shown) { shown.count { it == "ended null" } } < 2) delay(5) } }
        assertEquals(listOf("Hello.", "Again."), voice.said)
        assertEquals(listOf("began null", "began m-1"), synchronized(shown) { shown.filter { it.startsWith("began") } })
    }

    @Test
    fun `silence stops the engine at once, and the session ends without a failure`() {
        voice.hold = true
        sessions.begin()
        sessions.say("A long answer.")
        runBlocking { withTimeout(5_000) { voice.speaking.await() } }
        sessions.hush()
        until("ended null")
        assertTrue(voice.stopped)
    }

    @Test
    fun `a session that begins stops the one before, which ends unseen`() {
        voice.hold = true
        sessions.begin()
        sessions.say("First.")
        runBlocking { withTimeout(5_000) { voice.speaking.await() } }
        voice.hold = false
        sessions.begin(reading = "m-2")
        sessions.say("Second.")
        sessions.end()
        runBlocking { withTimeout(5_000) { while (voice.said.size < 2) delay(5) } }
        until("ended null")
        assertTrue(voice.stopped)
        assertEquals(listOf("First.", "Second."), voice.said)
        // The first session's end is not shown: the second one is the session now.
        assertEquals(1, synchronized(shown) { shown.count { it.startsWith("ended") } })
    }

    @Test
    fun `a failure is said in the shared words, naming the provider and the way`() {
        use = Result.success(VoiceUse(google, voice, VoiceRequest()))
        voice.failure = ProviderFailed(SpeechFailure.KeyRefused)
        sessions.begin()
        sessions.say("Hello.")
        sessions.end()
        until("ended Google Cloud refused the key: check it in Settings › Assistant › Voice")
        // What was said, and the provider's words, never reach the log: the failure's type alone.
        assertEquals(listOf("Speaking failed (ProviderFailed)"), logged)
    }

    @Test
    fun `Android's failures are text-to-speech's, and anything else is the provider failing`() {
        voice.failure = ProviderFailed(SpeechFailure.Unavailable)
        sessions.say("Hello.")
        sessions.end()
        until("ended This phone has no text-to-speech engine")
        use = Result.success(VoiceUse(google, voice, VoiceRequest()))
        voice.failure = IllegalStateException("not words")
        sessions.say("Again.")
        sessions.end()
        until("ended Google Cloud failed: try again")
    }

    @Test
    fun `a provider that cannot speak says why, and no engine starts`() {
        use = Result.failure(IllegalArgumentException("Enter Google Cloud's key in Settings › Assistant › Voice"))
        sessions.say("Hello.")
        sessions.end()
        until("ended Enter Google Cloud's key in Settings › Assistant › Voice")
        assertNull(voice.request)
    }

    /** A voice that says what it is given, or holds on until it is stopped, or fails. */
    private class FakeVoice : VoiceEngine {
        val said = Collections.synchronizedList(mutableListOf<String>())
        val speaking = CompletableDeferred<Unit>()

        @Volatile var request: VoiceRequest? = null

        @Volatile var hold = false

        @Volatile var stopped = false

        @Volatile var failure: Exception? = null

        override suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit) {
            this.request = request
            failure?.let { throw it }
            var sounding = false
            try {
                for (sentence in sentences) {
                    if (!sounding) playing(true)
                    sounding = true
                    said += sentence
                    speaking.complete(Unit)
                    if (hold) awaitCancellation()
                }
                if (sounding) playing(false)
            } catch (stopped: CancellationException) {
                this.stopped = true
                throw stopped
            }
        }
    }
}
