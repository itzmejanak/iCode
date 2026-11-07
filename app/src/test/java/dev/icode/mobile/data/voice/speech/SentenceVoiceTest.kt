package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The per-sentence runner and the WAV reader (docs/tts-plan.md §4.3, §4.4). */
class SentenceVoiceTest {
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private val playing = Collections.synchronizedList(mutableListOf<Boolean>())

    /** Each sentence's audio is its own bytes, so what plays shows which it was. */
    private var synthesis = Synthesis { text, previous, _, audio ->
        events += "ask $text after $previous"
        audio(text.toByteArray(), 24_000)
    }
    private var onPlay: suspend (String) -> Unit = {}
    private val output = object : VoiceOutput {
        override suspend fun play(pcm: ByteArray, rate: Int) {
            events += "play ${String(pcm)} at $rate"
            onPlay(String(pcm))
        }

        override suspend fun drain() {
            events += "drain"
        }

        override fun close() {
            events += "close"
        }
    }

    private fun sentences(vararg said: String) = Channel<String>(Channel.UNLIMITED).apply {
        said.forEach { trySend(it) }
        close()
    }

    private fun speak(sentences: Channel<String>) = runBlocking(Dispatchers.Default) {
        withTimeout(5_000) { SentenceVoice(synthesis) { output }.speak(sentences, VoiceRequest(), playing::add) }
    }

    private fun played() = synchronized(events) { events.filter { it.startsWith("play") } }

    @Test
    fun `each sentence is said in order, the next asked for while one plays, never two ahead`() {
        val second = CompletableDeferred<Unit>()
        val asking = synthesis
        synthesis = Synthesis { text, previous, request, audio ->
            if (text == "Two.") second.complete(Unit)
            asking.say(text, previous, request, audio)
        }
        onPlay = { pcm ->
            if (pcm == "One.") {
                // One still plays: Two has been asked for, and Three waits until One is heard.
                second.await()
                delay(100)
                assertFalse(synchronized(events) { events.any { it.startsWith("ask Three.") } })
            }
        }
        speak(sentences("One.", "Two.", "Three."))
        assertEquals(listOf("play One. at 24000", "play Two. at 24000", "play Three. at 24000"), played())
        assertEquals(
            listOf("ask One. after null", "ask Two. after One.", "ask Three. after Two."),
            synchronized(events) { events.filter { it.startsWith("ask") } },
        )
        assertEquals(listOf("drain", "close"), events.takeLast(2))
        assertEquals(listOf(true, false), playing.toList())
    }

    @Test
    fun `with nothing more to say yet the voice is heard out and quiet, and sounds again when more comes`() = runBlocking(Dispatchers.Default) {
        val sentences = Channel<String>(Channel.UNLIMITED)
        val speaking = launch { SentenceVoice(synthesis) { output }.speak(sentences, VoiceRequest(), playing::add) }
        sentences.send("One.")
        withTimeout(5_000) { while (playing.toList() != listOf(true, false)) delay(5) }
        assertTrue(events.contains("drain"))
        sentences.send("Two.")
        sentences.close()
        withTimeout(5_000) { speaking.join() }
        assertEquals(listOf(true, false, true, false), playing.toList())
    }

    @Test
    fun `silence stops at once, and the player is let go`() = runBlocking(Dispatchers.Default) {
        val started = CompletableDeferred<Unit>()
        onPlay = {
            started.complete(Unit)
            awaitCancellation()
        }
        val speaking = launch { SentenceVoice(synthesis) { output }.speak(sentences("One.", "Two."), VoiceRequest(), playing::add) }
        withTimeout(5_000) { started.await() }
        speaking.cancelAndJoin()
        assertEquals("close", events.last())
        assertEquals(listOf("play One. at 24000"), played())
        assertEquals(false, playing.lastOrNull())
    }

    @Test
    fun `a sentence that fails says so once the ones before it are heard`() {
        synthesis = Synthesis { text, _, _, audio ->
            if (text == "Two.") throw ProviderFailed(SpeechFailure.Busy)
            audio(text.toByteArray(), 24_000)
        }
        try {
            speak(sentences("One.", "Two.", "Three."))
            fail("Two failed")
        } catch (failed: ProviderFailed) {
            assertEquals(SpeechFailure.Busy, failed.failure)
        }
        assertEquals(listOf("play One. at 24000"), played())
        assertEquals("close", events.last())
    }

    @Test
    fun `a sentence's audio is played in whole samples, however its chunks are cut`() {
        synthesis = Synthesis { _, _, _, audio ->
            audio(byteArrayOf(1, 2, 3), 24_000)
            audio(byteArrayOf(4, 5, 6), 24_000)
        }
        val chunks = Collections.synchronizedList(mutableListOf<List<Byte>>())
        onPlay = {}
        val whole = object : VoiceOutput by output {
            override suspend fun play(pcm: ByteArray, rate: Int) {
                chunks += pcm.toList()
            }
        }
        runBlocking { withTimeout(5_000) { SentenceVoice(synthesis) { whole }.speak(sentences("Hello."), VoiceRequest()) {} } }
        assertEquals(listOf(listOf<Byte>(1, 2), listOf<Byte>(3, 4, 5, 6)), chunks.toList())
    }

    private fun wav(rate: Int, pcm: ByteArray, dataSize: Long = pcm.size.toLong(), channels: Int = 1, bits: Int = 16, extra: ByteArray = ByteArray(0)): ByteArray {
        fun int(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
        fun short(value: Int) = ByteArray(2) { (value shr (8 * it)).toByte() }
        val fmt = short(1) + short(channels) + int(rate.toLong()) + int(rate.toLong() * 2 * channels) + short(2 * channels) + short(bits)
        val body = "WAVE".toByteArray() + "fmt ".toByteArray() + int(16) + fmt + extra + "data".toByteArray() + int(dataSize) + pcm
        return "RIFF".toByteArray() + int(body.size.toLong()) + body
    }

    private fun read(file: ByteArray, cut: Int): Pair<ByteArray, Int> {
        val stream = WavStream()
        val pcm = file.toList().chunked(cut).fold(ByteArray(0)) { all, chunk -> all + stream.pcm(chunk.toByteArray()) }
        return pcm to stream.rate
    }

    @Test
    fun `a WAV file is read by its chunks, however it streams in`() {
        val pcm = ByteArray(20) { it.toByte() }
        // A LIST chunk before the data, of odd length and padded, and the header cut at every byte.
        val list = "LIST".toByteArray() + byteArrayOf(3, 0, 0, 0) + byteArrayOf(9, 9, 9, 0)
        listOf(1, 3, 7, 64).forEach { cut ->
            val (read, rate) = read(wav(22_050, pcm, extra = list), cut)
            assertArrayEquals("cut at $cut", pcm, read)
            assertEquals(22_050, rate)
        }
    }

    @Test
    fun `its data runs to its length, or to the end when a streaming server leaves it unknown`() {
        val pcm = ByteArray(20) { it.toByte() }
        assertArrayEquals(pcm.copyOf(8), read(wav(24_000, pcm, dataSize = 8), 5).first)
        assertArrayEquals(pcm, read(wav(24_000, pcm, dataSize = 0xFFFFFFFFL), 5).first)
        assertArrayEquals(pcm, read(wav(24_000, pcm, dataSize = 0), 5).first)
    }

    @Test
    fun `only 16-bit mono PCM is played`() {
        listOf(wav(24_000, ByteArray(8), channels = 2), wav(24_000, ByteArray(8), bits = 8), "ID3 not a wave file".toByteArray()).forEach { file ->
            try {
                read(file, 64)
                fail("Refused")
            } catch (failed: ProviderFailed) {
                assertEquals("Its audio is not 16-bit mono PCM", failed.detail)
            }
        }
    }
}
