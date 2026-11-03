package dev.icode.mobile.data.voice

import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import dev.icode.mobile.domain.voice.VoiceChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * An open-source voice on this phone (docs/phone-agent-plan.md §5.8): Kokoro or Piper, through
 * sherpa-onnx. Each sentence plays as soon as it is made, while the next is being made.
 */
internal class ModelSpeaker(private val models: VoiceModels, private val voice: VoiceChoice) : VoiceSpeaker {
    private var engine: OfflineTts? = null

    private fun engine(): OfflineTts = engine ?: OfflineTts(config = SherpaConfigs.speaker(voice, models::file)).also { engine = it }

    override suspend fun speak(text: String) = coroutineScope<Unit> {
        val tts = withContext(Dispatchers.Default) { engine() }
        val rate = tts.sampleRate()
        val track = AudioTrack.Builder()
            .setAudioAttributes(SPEECH)
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT))
            .build()
        val sentences = Channel<FloatArray>(Channel.UNLIMITED)
        var frames = 0L
        try {
            track.play()
            val player = launch(Dispatchers.IO) {
                for (samples in sentences) {
                    track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
                    frames += samples.size
                }
            }
            withContext(Dispatchers.Default) {
                val making = coroutineContext.job
                // An object, not a lambda: sherpa's JNI looks up invoke([F)Ljava/lang/Integer;
                // on the callback's class, which Kotlin 2's indy lambda + desugaring no longer
                // provides (a NoSuchMethodError aborts the process when a sample is played).
                val onSamples = object : (FloatArray) -> Int {
                    // Returning 0 stops the model between sentences once the speech is stopped.
                    override fun invoke(samples: FloatArray): Int {
                        sentences.trySend(samples.copyOf())
                        return if (making.isActive) 1 else 0
                    }
                }
                tts.generateWithCallback(text, voice.speaker, SPEED, onSamples)
                ensureActive()
            }
            sentences.close()
            player.join()
            // The track plays out what it holds after the last write.
            while (isActive && (track.playbackHeadPosition.toLong() and FRAMES) < frames) delay(DRAIN_MILLIS)
        } finally {
            sentences.close()
            track.pause()
            track.flush()
            track.release()
        }
    }

    override fun release() {
        engine?.release()
        engine = null
    }

    private companion object {
        const val SPEED = 1f
        const val DRAIN_MILLIS = 20L

        /** The playback head, an unsigned 32-bit count of frames. */
        const val FRAMES = 0xFFFF_FFFFL
    }
}
