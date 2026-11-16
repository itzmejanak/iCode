package dev.icode.mobile.data.voice.speech

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/** Where an engine's audio comes from: 16-bit mono PCM, little-endian, at the rate it asks. */
internal fun interface SpeechAudio {
    /** The microphone's sound, in frames of [FRAME_MILLIS], until the collection stops. */
    fun frames(rate: Int): Flow<ByteArray>

    companion object {
        const val FRAME_MILLIS = 100
    }
}

/**
 * The phone's microphone for every engine but Android's (docs/voice-and-phone-control.md): `AudioRecord`,
 * tuned for speech, at the rate the engine takes — or, where the phone refuses that rate, at 48 kHz
 * brought down to it. Nothing is kept: each frame goes to the engine as it comes. The voice coordinator
 * checks the permission, and the service that holds the microphone away from iCode, before any engine starts.
 */
internal class SpeechMicrophone : SpeechAudio {
    @SuppressLint("MissingPermission")
    override fun frames(rate: Int): Flow<ByteArray> = flow {
        val recordRate = if (AudioRecord.getMinBufferSize(rate, MONO, PCM16) > 0) rate else FALLBACK_RATE
        val step = recordRate / rate
        val frameBytes = recordRate * SpeechAudio.FRAME_MILLIS / 1000 * 2
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, recordRate, MONO, PCM16,
            maxOf(AudioRecord.getMinBufferSize(recordRate, MONO, PCM16), frameBytes * 4),
        )
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IOException("The microphone could not be opened")
            record.startRecording()
            val buffer = ByteArray(frameBytes)
            while (currentCoroutineContext().isActive) {
                var filled = 0
                while (filled < frameBytes) {
                    val read = record.read(buffer, filled, frameBytes - filled)
                    if (read < 0) throw IOException("The microphone could not be read ($read)")
                    filled += read
                }
                emit(if (step == 1) buffer.copyOf() else downsample(buffer, step))
            }
        } finally {
            if (record.state == AudioRecord.STATE_INITIALIZED) record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.IO)

    internal companion object {
        private const val MONO = AudioFormat.CHANNEL_IN_MONO
        private const val PCM16 = AudioFormat.ENCODING_PCM_16BIT
        private const val FALLBACK_RATE = 48_000

        /** 16-bit samples [step] to one, each the mean of those it stands for: 48 kHz to 16 kHz is 3. */
        fun downsample(pcm: ByteArray, step: Int): ByteArray {
            val samples = pcm.size / 2
            val out = ByteArray(samples / step * 2)
            for (index in 0 until samples / step) {
                var sum = 0
                for (offset in 0 until step) {
                    val at = (index * step + offset) * 2
                    sum += (pcm[at].toInt() and 0xFF) or (pcm[at + 1].toInt() shl 8)
                }
                val mean = sum / step
                out[index * 2] = mean.toByte()
                out[index * 2 + 1] = (mean shr 8).toByte()
            }
            return out
        }
    }
}
