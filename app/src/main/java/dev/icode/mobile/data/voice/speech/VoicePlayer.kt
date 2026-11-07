package dev.icode.mobile.data.voice.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import dev.icode.mobile.domain.voice.SpeechFailure
import kotlinx.coroutines.delay

/** Where a cloud voice's audio plays: 16-bit mono PCM, in the order it is given (docs/tts-plan.md §4.4). */
internal interface VoiceOutput {
    /** Plays [pcm] — whole samples ([WholeSamples]) — at [rate] after what came before; returns once it is queued. */
    suspend fun play(pcm: ByteArray, rate: Int)

    /** Returns once everything queued has been heard. */
    suspend fun drain()

    /** Silence at once, and what is queued goes; the output is not used again. */
    fun close()
}

/**
 * Android's `AudioTrack`, streaming, with the attributes Android's own voice speaks with. Writes never
 * block, so a session that is hushed stops at once.
 */
internal class VoicePlayer : VoiceOutput {
    private var track: AudioTrack? = null
    private var rate = 0

    /** Frames written since the track last started, and so not yet known to be heard. */
    private var written = 0L

    override suspend fun play(pcm: ByteArray, rate: Int) {
        if (pcm.isEmpty()) return
        val track = trackAt(rate)
        var offset = 0
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
        while (offset < pcm.size) {
            val wrote = track.write(pcm, offset, pcm.size - offset, AudioTrack.WRITE_NON_BLOCKING)
            if (wrote < 0) throw ProviderFailed(SpeechFailure.Failed, "The phone could not play the voice")
            offset += wrote
            if (wrote == 0) delay(WAIT_MILLIS)
        }
        written += pcm.size / 2
    }

    /**
     * Stopping a streaming track plays what was written to its end, and resets its position, so what is
     * left is waited for by time: the frames not yet played at the rate they play.
     */
    override suspend fun drain() {
        val track = track ?: return
        if (written == 0L) return
        val left = (written - (track.playbackHeadPosition.toLong() and 0xFFFFFFFFL)).coerceAtLeast(0)
        track.stop()
        written = 0
        delay(left * 1_000 / rate + TAIL_MILLIS)
    }

    override fun close() {
        track?.run {
            runCatching { pause() }
            runCatching { flush() }
            release()
        }
        track = null
    }

    private suspend fun trackAt(rate: Int): AudioTrack {
        track?.takeIf { this.rate == rate }?.let { return it }
        drain()
        close()
        val bytes = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bytes <= 0) throw ProviderFailed(SpeechFailure.Unavailable)
        this.rate = rate
        return AudioTrack.Builder()
            .setAudioAttributes(SPEECH)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bytes * 2)
            .build()
            .also { track = it }
    }

    companion object {
        /** The assistant speaking: the attributes Android's own voice speaks with here. */
        val SPEECH: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        private const val WAIT_MILLIS = 10L

        /** What the output path holds after the track's last frame. */
        private const val TAIL_MILLIS = 60L
    }
}

/**
 * While anything is read aloud, other sound is lowered (docs/tts-plan.md §9 D5): transient focus with
 * ducking, whichever engine speaks, given back once it stops.
 */
internal class VoiceFocus(context: Context) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(VoicePlayer.SPEECH).build()
    private var held = false

    @Synchronized
    fun hold(speaking: Boolean) {
        if (speaking == held) return
        held = speaking
        if (speaking) audio.requestAudioFocus(request) else audio.abandonAudioFocusRequest(request)
    }
}
