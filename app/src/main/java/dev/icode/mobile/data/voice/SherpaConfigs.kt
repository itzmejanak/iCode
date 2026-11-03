package dev.icode.mobile.data.voice

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import dev.icode.mobile.domain.voice.VoiceCatalog
import dev.icode.mobile.domain.voice.VoiceChoice
import dev.icode.mobile.domain.voice.VoiceModel

/**
 * sherpa-onnx v1.13.8's settings for each model iCode offers (docs/phone-agent-plan.md §3.4), as its
 * own examples set them. [file] gives a kept file's path in its model's folder.
 */
internal object SherpaConfigs {
    /** What every recognizer and the detector hear: 16 kHz mono. */
    const val SAMPLE_RATE = 16_000

    /** Silero's window at 16 kHz. */
    const val VAD_WINDOW = 512

    /** A speech segment the detector cuts at this length, so Whisper never hears more than its 30 seconds. */
    private const val MAX_SEGMENT_SECONDS = 20f

    /** A pause this long ends a segment: it is read while the user goes on. */
    private const val SEGMENT_PAUSE_SECONDS = 0.5f

    private const val LISTEN_THREADS = 2
    private const val KOKORO_THREADS = 4
    private const val PIPER_THREADS = 2

    /** Whisper's padding for its multilingual models, as sherpa-onnx recommends. */
    private const val WHISPER_TAIL = 300

    private val CHINESE_RULES = listOf("phone-zh.fst", "date-zh.fst", "number-zh.fst")

    fun vad(file: (VoiceModel, String) -> String) = VadModelConfig(
        sileroVadModelConfig = SileroVadModelConfig(
            model = file(VoiceCatalog.VAD, "silero_vad.onnx"),
            minSilenceDuration = SEGMENT_PAUSE_SECONDS,
            windowSize = VAD_WINDOW,
            maxSpeechDuration = MAX_SEGMENT_SECONDS,
        ),
        sampleRate = SAMPLE_RATE,
    )

    fun recognizer(model: VoiceModel, file: (VoiceModel, String) -> String): OfflineRecognizerConfig {
        fun at(name: String) = file(model, name)
        val config = when (model) {
            // Its language found by itself: "" for a multilingual model.
            VoiceCatalog.WHISPER_BASE -> OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(at("base-encoder.int8.onnx"), at("base-decoder.int8.onnx"), language = "", tailPaddings = WHISPER_TAIL),
                tokens = at("base-tokens.txt"),
                modelType = "whisper",
            )
            VoiceCatalog.SENSE_VOICE -> OfflineModelConfig(senseVoice = OfflineSenseVoiceModelConfig(at("model.int8.onnx")), tokens = at("tokens.txt"))
            else -> throw IllegalArgumentException("${model.name} is no listening model")
        }
        return OfflineRecognizerConfig(modelConfig = config.apply { numThreads = LISTEN_THREADS })
    }

    /** Kokoro reads English and Mandarin with its lexicons and Chinese rules, every other language with espeak-ng; Piper with espeak-ng. */
    fun speaker(voice: VoiceChoice, file: (VoiceModel, String) -> String): OfflineTtsConfig {
        val model = voice.model
        fun at(name: String) = file(model, name)
        if (model == VoiceCatalog.KOKORO) {
            val kokoro = OfflineTtsKokoroModelConfig(
                model = at("model.int8.onnx"),
                voices = at("voices.bin"),
                tokens = at("tokens.txt"),
                dataDir = at("espeak-ng-data"),
                lexicon = voice.lexicons.joinToString(",") { at(it) },
                lang = voice.lang,
            )
            val rules = if ("lexicon-zh.txt" in voice.lexicons) CHINESE_RULES.joinToString(",") { at(it) } else ""
            return OfflineTtsConfig(model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = KOKORO_THREADS), ruleFsts = rules)
        }
        val vits = OfflineTtsVitsModelConfig(model = at(model.keep.first { it.endsWith(".onnx") }), tokens = at("tokens.txt"), dataDir = at("espeak-ng-data"))
        return OfflineTtsConfig(model = OfflineTtsModelConfig(vits = vits, numThreads = PIPER_THREADS))
    }
}
