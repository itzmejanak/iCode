package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.domain.voice.SpeechFailure
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.takeWhile

/** What a provider that answers once the user stopped does with the audio: its words, or why not. */
internal fun interface SpeechUpload {
    /** [pcm]: 16-bit mono at [rate], all the hearing heard. */
    suspend fun transcribe(pcm: ByteArray, rate: Int, request: SpeechRequest): SpeechEvent
}

/**
 * A hearing for a provider that answers once the user stopped (docs/speech-plan.md §4.3, §4.4): the
 * microphone is recorded, in memory and nowhere else, until the user stops talking ([SpeechQuiet]),
 * says nothing at all, or taps done; then [upload] sends it, once, and its words are final.
 */
internal class UploadSpeech(private val audio: SpeechAudio, private val upload: SpeechUpload) : SpeechEngine {
    override fun hear(request: SpeechRequest, done: Deferred<Unit>): Flow<SpeechEvent> = channelFlow {
        val recorded = ByteArrayOutputStream()
        val quiet = SpeechQuiet()
        try {
            audio.frames(RATE).takeWhile { frame ->
                recorded.write(frame)
                !quiet.ended(frame) && !done.isCompleted
            }.collect()
        } catch (stopped: CancellationException) {
            throw stopped
        } catch (_: Exception) {
            send(SpeechEvent.Failed(SpeechFailure.Microphone))
            return@channelFlow
        }
        val answer = when {
            !quiet.spoke -> SpeechEvent.Words("", final = true)
            else -> try {
                upload.transcribe(recorded.toByteArray(), RATE, request)
            } catch (unreached: UploadFailed) {
                unreached.event
            }
        }
        send(answer)
    }

    internal companion object {
        const val RATE = 16_000

        /** [pcm] as a WAV file: 16-bit mono at [rate]. */
        fun wav(pcm: ByteArray, rate: Int): ByteArray = ByteBuffer.allocate(WAV_HEADER + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()).putInt(WAV_HEADER - 8 + pcm.size).put("WAVE".toByteArray())
            put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            put("data".toByteArray()).putInt(pcm.size).put(pcm)
        }.array()

        private const val WAV_HEADER = 44
    }
}

/**
 * When a hearing without a provider's own turn detection ends (docs/speech-plan.md §4.4): after
 * [QUIET_MILLIS] of quiet that follows speech, after [SILENT_MILLIS] with no speech at all, or at
 * [LONGEST_MILLIS]. Speech is a frame louder than [LEVEL], and than [ROOM_FACTOR] times the room's own
 * sound: the quietest frame heard so far, so a hearing that starts with speech finds the room at its
 * first pause.
 */
internal class SpeechQuiet {
    /** The user said something. */
    var spoke = false
        private set
    private var elapsed = 0
    private var quietFor = 0
    private var room = Double.MAX_VALUE

    /** [frame] heard: whether the hearing ends with it. */
    fun ended(frame: ByteArray): Boolean {
        val level = level(frame)
        room = minOf(room, level)
        elapsed += SpeechAudio.FRAME_MILLIS
        if (level > maxOf(LEVEL, room * ROOM_FACTOR)) {
            spoke = true
            quietFor = 0
        } else {
            quietFor += SpeechAudio.FRAME_MILLIS
        }
        return (spoke && quietFor >= QUIET_MILLIS) || (!spoke && elapsed >= SILENT_MILLIS) || elapsed >= LONGEST_MILLIS
    }

    internal companion object {
        const val QUIET_MILLIS = 1_500
        const val SILENT_MILLIS = 8_000

        /** The longest hearing, for every provider (§9 D6). */
        const val LONGEST_MILLIS = 120_000

        /** The root-mean-square level, of 32 767, above which a frame is speech. */
        const val LEVEL = 500.0
        const val ROOM_FACTOR = 3.0

        fun level(pcm: ByteArray): Double {
            val samples = pcm.size / 2
            if (samples == 0) return 0.0
            var sum = 0.0
            for (index in 0 until samples) {
                val sample = (pcm[index * 2].toInt() and 0xFF) or (pcm[index * 2 + 1].toInt() shl 8)
                sum += sample.toDouble() * sample
            }
            return sqrt(sum / samples)
        }
    }
}
