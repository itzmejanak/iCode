package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import dev.icode.mobile.domain.voice.SpokenText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** A provider's text-to-speech that answers one request per sentence (docs/tts-plan.md §4.3). */
internal fun interface Synthesis {
    /**
     * Says [text] — [previous] said just before it, for an API that keeps the voice's flow across
     * requests — giving its 16-bit mono PCM to [audio] as it comes, at the rate it has. Throws
     * [ProviderFailed] when the provider refuses.
     */
    suspend fun say(text: String, previous: String?, request: VoiceRequest, audio: suspend (pcm: ByteArray, rate: Int) -> Unit)
}

/**
 * The per-sentence runner (docs/tts-plan.md §4.3): each sentence is asked for as it comes, and the next
 * one while this one plays, so no gap falls between them; never two ahead. A sentence that fails says
 * so once the ones before it are heard.
 */
internal class SentenceVoice(private val synthesis: Synthesis, private val output: () -> VoiceOutput) : VoiceEngine {
    override suspend fun speak(sentences: ReceiveChannel<String>, request: VoiceRequest, playing: (Boolean) -> Unit) = coroutineScope {
        // The sentences asked for, in order: one plays while at most one more is asked for.
        val asked = Channel<Channel<Chunk>>(capacity = 1)
        launch {
            var previous: String? = null
            try {
                for (sentence in sentences) {
                    for (part in SpokenText.parts(sentence, LONGEST)) {
                        val audio = Channel<Chunk>(Channel.UNLIMITED)
                        asked.send(audio)
                        val samples = WholeSamples()
                        try {
                            synthesis.say(part, previous, request) { pcm, rate -> audio.send(Chunk(samples.of(pcm), rate)) }
                            audio.close()
                        } catch (stopped: CancellationException) {
                            throw stopped
                        } catch (failed: Exception) {
                            audio.close(failed)
                            return@launch
                        }
                        previous = part
                    }
                }
            } finally {
                asked.close()
            }
        }
        val player = output()
        var sounding = false
        fun sound(now: Boolean) {
            if (sounding == now) return
            sounding = now
            playing(now)
        }
        try {
            while (true) {
                // Nothing asked for yet: what was played is heard out, and the voice is quiet until more comes.
                val audio = asked.tryReceive().getOrNull() ?: run {
                    if (sounding) player.drain()
                    sound(false)
                    asked.receiveCatching().getOrNull()
                } ?: break
                for (chunk in audio) {
                    sound(true)
                    player.play(chunk.pcm, chunk.rate)
                }
            }
        } finally {
            player.close()
            sound(false)
        }
    }

    private class Chunk(val pcm: ByteArray, val rate: Int)

    private companion object {
        /** iCode's cap on one request's text: inside every per-sentence provider's own. */
        const val LONGEST = 1_000
    }
}

/** One stream of 16-bit samples, cut anywhere: each chunk given in whole samples, a byte left over waiting for the next. */
internal class WholeSamples {
    private var odd: Byte? = null

    fun of(chunk: ByteArray): ByteArray {
        val bytes = odd?.let { byteArrayOf(it) + chunk } ?: chunk
        val whole = bytes.size - bytes.size % 2
        odd = if (whole < bytes.size) bytes.last() else null
        return if (whole == bytes.size) bytes else bytes.copyOf(whole)
    }
}

/**
 * A WAV file as it streams in, read by its chunks (`fmt `, then `data`), never by a fixed header length.
 * Only 16-bit mono PCM is played; a `data` length of 0 or all ones, as streaming servers write, runs to
 * the end.
 */
internal class WavStream {
    private var head = ByteArray(0)
    private var left = -1L

    /** The rate its `fmt ` chunk gives, once read. */
    var rate = 0
        private set

    /** The PCM in [bytes], once the header has passed; empty before. */
    fun pcm(bytes: ByteArray): ByteArray {
        if (left >= 0 || left == UNTIL_END) return take(bytes)
        head += bytes
        if (head.size < 12) return EMPTY
        if (head.text(0) != "RIFF" || head.text(8) != "WAVE") throw notPcm()
        var at = 12
        while (head.size >= at + 8) {
            val id = head.text(at)
            val size = head.int(at + 4)
            val body = at + 8
            if (id == "data") {
                if (rate == 0) throw notPcm()
                left = if (size == 0L || size == 0xFFFFFFFFL) UNTIL_END else size
                val rest = head.copyOfRange(body, head.size)
                head = EMPTY
                return take(rest)
            }
            if (head.size < body + size) return EMPTY
            if (id == "fmt ") format(body, size.toInt())
            at = body + size.toInt() + (size % 2).toInt()
        }
        return EMPTY
    }

    private fun format(at: Int, size: Int) {
        if (size < 16) throw notPcm()
        val encoding = head.short(at)
        val channels = head.short(at + 2)
        val bits = head.short(at + 14)
        if ((encoding != PCM && encoding != EXTENSIBLE) || channels != 1 || bits != 16) throw notPcm()
        rate = head.int(at + 4).toInt()
    }

    private fun take(bytes: ByteArray): ByteArray {
        if (left == UNTIL_END) return bytes
        val taken = bytes.size.toLong().coerceAtMost(left).toInt()
        left -= taken
        return if (taken == bytes.size) bytes else bytes.copyOf(taken)
    }

    private fun ByteArray.text(at: Int) = String(this, at, 4, Charsets.US_ASCII)

    private fun ByteArray.short(at: Int) = (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.int(at: Int): Long = (0 until 4).fold(0L) { value, index -> value or ((this[at + index].toLong() and 0xFF) shl (8 * index)) }

    private fun notPcm() = ProviderFailed(SpeechFailure.Failed, "Its audio is not 16-bit mono PCM")

    private companion object {
        val EMPTY = ByteArray(0)
        const val UNTIL_END = -2L
        const val PCM = 1
        const val EXTENSIBLE = 0xFFFE
    }
}
