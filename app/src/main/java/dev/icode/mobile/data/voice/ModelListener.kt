package dev.icode.mobile.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import dev.icode.mobile.data.log.BackendLog
import dev.icode.mobile.domain.voice.VoiceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Listening with a model on this phone (docs/phone-agent-plan.md §5.8): recording starts at once, and
 * what is said while the models load is kept and read after. Silero cuts the speech at each pause, and
 * each piece is read by [model] as the user goes on; a longer silence after speech ends it.
 */
internal class ModelListener(
    private val models: VoiceModels,
    private val model: VoiceModel,
    private val scope: CoroutineScope,
) : VoiceListener {
    private var job: Job? = null

    @Volatile private var finishing = false

    @Volatile private var cancelled = false

    @SuppressLint("MissingPermission") // The coordinator listens only once Android granted the microphone.
    override fun start(heard: (String, Boolean) -> Unit, problem: (String) -> Unit) {
        job = scope.launch(Dispatchers.IO) {
            val record = runCatching { recorder() }.getOrNull()?.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                ?: return@launch problem("The microphone could not be opened")
            val audio = Channel<FloatArray>(Channel.UNLIMITED)
            record.startRecording()
            val reader = launch {
                val pcm = ShortArray(SherpaConfigs.VAD_WINDOW)
                while (isActive && !finishing) {
                    val count = record.read(pcm, 0, pcm.size)
                    if (count <= 0) break
                    audio.trySend(FloatArray(count) { pcm[it] / PCM_SCALE })
                }
                audio.close()
            }
            try {
                val text = Vad(config = SherpaConfigs.vad(models::file)).use { vad ->
                    OfflineRecognizer(config = SherpaConfigs.recognizer(model, models::file)).use { recognizer ->
                        hear(audio, vad, recognizer) { if (!cancelled) heard(it, false) }
                    }
                }
                if (!cancelled) heard(text, true)
            } catch (failed: Exception) {
                // Its type only: what the model heard is the user's.
                BackendLog.w(TAG, "Listening failed (${failed.javaClass.simpleName})")
                if (!cancelled) problem("${model.name} could not listen: download it again in Settings › Assistant › Voice")
            } finally {
                reader.cancel()
                record.stop()
                record.release()
            }
        }
    }

    /** Reads [audio] until the user stops talking, finishes, or cancels: each piece as it ends, the words so far to [partial]. */
    private suspend fun hear(audio: Channel<FloatArray>, vad: Vad, recognizer: OfflineRecognizer, partial: (String) -> Unit): String {
        var text = ""
        var samples = 0L
        var lastSpeech = 0L
        var spoke = false
        fun read() {
            while (!vad.empty()) {
                val piece = vad.front().samples
                vad.pop()
                val words = decode(recognizer, piece)
                if (words.isNotEmpty()) {
                    text = listOf(text, words).filter { it.isNotEmpty() }.joinToString(" ")
                    partial(text)
                }
            }
        }
        for (chunk in audio) {
            vad.acceptWaveform(chunk)
            samples += chunk.size
            if (vad.isSpeechDetected()) {
                spoke = true
                lastSpeech = samples
            }
            read()
            val quiet = samples - lastSpeech
            if (cancelled || (spoke && quiet > END_SILENCE) || (!spoke && quiet > NOTHING_SAID) || samples > LONGEST) break
        }
        vad.flush()
        read()
        return text
    }

    override fun finish() {
        finishing = true
    }

    override fun cancel() {
        cancelled = true
        job?.cancel()
    }

    @SuppressLint("MissingPermission")
    private fun recorder(): AudioRecord {
        val minimum = AudioRecord.getMinBufferSize(SherpaConfigs.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        return AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SherpaConfigs.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, SherpaConfigs.VAD_WINDOW * BYTES_PER_SAMPLE * BUFFER_WINDOWS),
        )
    }

    private inline fun <T> Vad.use(block: (Vad) -> T): T = try { block(this) } finally { release() }

    private inline fun <T> OfflineRecognizer.use(block: (OfflineRecognizer) -> T): T = try { block(this) } finally { release() }

    private fun decode(recognizer: OfflineRecognizer, samples: FloatArray): String {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SherpaConfigs.SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    private companion object {
        const val TAG = "ModelListener"
        const val PCM_SCALE = 32_768f
        const val BYTES_PER_SAMPLE = 2
        const val BUFFER_WINDOWS = 16

        /** Silence after speech that ends listening: 1.5 s. */
        const val END_SILENCE = SherpaConfigs.SAMPLE_RATE * 3L / 2

        /** Nothing said at all for 8 s ends it too. */
        const val NOTHING_SAID = SherpaConfigs.SAMPLE_RATE * 8L

        /** At most two minutes in one go. */
        const val LONGEST = SherpaConfigs.SAMPLE_RATE * 120L
    }
}
