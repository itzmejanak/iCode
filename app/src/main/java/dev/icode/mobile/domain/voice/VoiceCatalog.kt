package dev.icode.mobile.domain.voice

/**
 * A model on sherpa-onnx's releases (docs/phone-agent-plan.md §3.4): what it is, where it is, how big,
 * its pin, and what of it is kept — the rest of its archive is never unpacked.
 */
data class VoiceModel(
    val id: String,
    val name: String,
    val detail: String,
    /** Under [VoiceCatalog.RELEASES]: an archive (`.tar.bz2`), or one file. */
    val path: String,
    val bytes: Long,
    val sha256: String,
    /** The folder its archive unpacks to; the file's own folder for one file. */
    val folder: String,
    /** Its files read, and its folders whole (ending in `/`), from [folder]. */
    val keep: List<String>,
    val licence: String,
) {
    val archive: Boolean get() = path.endsWith(".tar.bz2")
}

/** A voice that speaks: a model, a speaker in it, and its language. */
data class VoiceChoice(
    val id: String,
    val model: VoiceModel,
    val speaker: Int,
    val name: String,
    val language: String,
    /** espeak-ng's code for its language; "" where the model's own lexicons read it ([lexicons]). */
    val lang: String = "",
    /** The lexicons, in [VoiceModel.folder], that read its words: Kokoro's English and Mandarin. */
    val lexicons: List<String> = emptyList(),
)

/**
 * What iCode offers on the phone (docs/phone-agent-plan.md §3.4, §5.8), from sherpa-onnx v1.13.8's
 * releases: two listeners and one voice activity detector; Kokoro for eight languages and a Piper
 * voice for each of 32 more. Sizes and SHA-256 were read from the files themselves.
 */
object VoiceCatalog {
    const val RELEASES = "https://github.com/k2-fsa/sherpa-onnx/releases/download/"

    /** When the user stops talking (Silero VAD, MIT). */
    val VAD = VoiceModel(
        "silero-vad", "Silero VAD", "Hears when you stop talking", "asr-models/silero_vad.onnx", 643_854,
        "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6", "silero-vad", listOf("silero_vad.onnx"), "MIT",
    )

    val WHISPER_BASE = VoiceModel(
        "whisper-base", "Whisper base", "99 languages, found by itself", "asr-models/sherpa-onnx-whisper-base.tar.bz2", 207_557_382,
        "911b2083efd7c0dca2ac3b358b75222660dc09fb716d64fbfc417ba6c99ff3de", "sherpa-onnx-whisper-base", listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt"), "MIT (OpenAI)",
    )

    val SENSE_VOICE = VoiceModel(
        "sense-voice", "SenseVoice", "Chinese, English, Japanese, Korean, Cantonese: faster and closer in those",
        "asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09.tar.bz2", 165_783_878,
        "7305f7905bfcf77fa0b39388a313f3da35c68d971661a65475b56fb2162c8e63", "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09", listOf("model.int8.onnx", "tokens.txt"), "FunAudioLLM model licence",
    )

    val LISTENERS = listOf(WHISPER_BASE, SENSE_VOICE)

    val KOKORO = VoiceModel(
        "kokoro", "Kokoro", "English, Mandarin, Spanish, French, Hindi, Italian, Portuguese: 49 natural voices",
        "tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2", 132_353_410, "72c347230107ef8a31098ec4d468a605244beef5f1ed00db97efa13518800653", "kokoro-int8-multi-lang-v1_0",
        listOf(
            "model.int8.onnx", "voices.bin", "tokens.txt", "lexicon-us-en.txt", "lexicon-gb-en.txt", "lexicon-zh.txt",
            "phone-zh.fst", "date-zh.fst", "number-zh.fst", "espeak-ng-data/",
        ),
        "Apache-2.0",
    )

    /** Kokoro v1.0's speakers in `voices.bin`, by number (sherpa-onnx's scripts/kokoro/v1.0); Japanese's left out, espeak-ng reading it poorly. */
    private val KOKORO_SPEAKERS = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis",
        "ef_dora", "em_alex", "ff_siwis", "hf_alpha", "hf_beta", "hm_omega", "hm_psi", "if_sara", "im_nicola",
        "jf_alpha", "jf_gongitsune", "jf_nezumi", "jf_tebukuro", "jm_kumo", "pf_dora", "pm_alex", "pm_santa",
        "zf_xiaobei", "zf_xiaoni", "zf_xiaoxiao", "zf_xiaoyi", "zm_yunjian", "zm_yunxi", "zm_yunxia", "zm_yunyang", "em_santa",
    )

    /** Kokoro's language letter: its name, espeak-ng's code ("" for a lexicon's), and its lexicons. */
    private val KOKORO_LANGUAGES = mapOf(
        'a' to Triple("English (US)", "", listOf("lexicon-us-en.txt", "lexicon-zh.txt")),
        'b' to Triple("English (UK)", "", listOf("lexicon-gb-en.txt", "lexicon-zh.txt")),
        'z' to Triple("Mandarin", "", listOf("lexicon-us-en.txt", "lexicon-zh.txt")),
        'e' to Triple("Spanish", "es", emptyList()),
        'f' to Triple("French", "fr", emptyList()),
        'h' to Triple("Hindi", "hi", emptyList()),
        'i' to Triple("Italian", "it", emptyList()),
        'p' to Triple("Portuguese (Brazil)", "pt-br", emptyList()),
    )

    private val kokoroVoices = KOKORO_SPEAKERS.mapIndexedNotNull { number, speaker ->
        val (language, lang, lexicons) = KOKORO_LANGUAGES[speaker[0]] ?: return@mapIndexedNotNull null
        val name = speaker.substringAfter('_').replaceFirstChar { it.uppercase() }
        val who = if (speaker[1] == 'f') "female" else "male"
        VoiceChoice("kokoro/$speaker", KOKORO, number, "$name ($who)", language, lang, lexicons)
    }

    /** A Piper voice (VITS; each voice its dataset's own licence), read by its model with espeak-ng. */
    private fun piper(name: String, language: String, bytes: Long, sha256: String): VoiceChoice {
        val folder = "vits-piper-$name"
        val model = VoiceModel(
            "piper-$name", "$language voice", "Piper: ${name.substringAfter('-').substringBeforeLast('-')}",
            "tts-models/$folder.tar.bz2", bytes, sha256, folder, listOf("$name.onnx", "tokens.txt", "espeak-ng-data/"), "Its dataset's licence (MODEL_CARD)",
        )
        return VoiceChoice("piper/$name", model, 0, name.substringAfter('-').substringBeforeLast('-').replaceFirstChar { it.uppercase() }, language)
    }

    private val piperVoices = listOf(
        piper("ar_JO-kareem-medium", "Arabic", 67_177_830, "9ebbcea30e0fbd588f7b2cb45ee897d6aeb1bf5791cbc037a7b5a3f641e3dbce"),
        piper("ca_ES-upc_ona-medium", "Catalan", 67_223_188, "b6c50f22e13cfc72ce51c496d22b987adf6704047f6d342968197b4b0b3127a5"),
        piper("cs_CZ-jirka-medium", "Czech", 67_212_775, "321aafd83dd89b3d5b2c36c5898e0710d4f472f3c191cb7f73901a89b7f8539f"),
        piper("cy_GB-gwryw_gogleddol-medium", "Welsh", 67_230_234, "f31f90afc4f28e9a768309f0b1c87272ef178936b32dd181aa1aa3a0eeddd187"),
        piper("da_DK-talesyntese-medium", "Danish", 67_185_257, "f2e2cbcdb2b21e76f93635b35105d07b37997ace434d7289d04b689a97c19bd0"),
        piper("de_DE-thorsten-medium", "German", 67_214_254, "50487d9c95fdf2191f31d2588569381063ba1591dcd4c7d4bdd30f12b2191714"),
        piper("el_GR-rapunzelina-low", "Greek", 67_105_521, "bb868070a9ba0d7220b3d7c9291a998c2f60accdb73525bd63b8751b86dd4839"),
        piper("fa_IR-amir-medium", "Persian", 67_180_373, "46af65c1cdd86ec300db3998f2bf31a68d9e38da930c0655c1ff6f610e84f864"),
        piper("fi_FI-harri-medium", "Finnish", 67_205_198, "a7fd608e73983c366a5e0bcd13382e6529a6749f67efc406b5a7f9021e3a8b1d"),
        piper("hu_HU-anna-medium", "Hungarian", 67_167_701, "c67b13e57b71eca19098fe499afc35ebce8f63a0b524017c786af2025821216a"),
        piper("id_ID-news_tts-medium", "Indonesian", 67_243_848, "634b0169fe41c368e9fafe128f30647f9e0301e852ce503cd78146db524f1f12"),
        piper("is_IS-bui-medium", "Icelandic", 67_271_654, "404a4de4b2a49dafb5a9f67ccf5366ab19d97e146aab602d348d8ba39dfb89d9"),
        piper("ka_GE-natia-medium", "Georgian", 67_209_457, "18e34c601f4be13e5132ec772e2cbcfa47ca6a4aaaf995dd5baefe014f5a0256"),
        piper("kk_KZ-issai-high", "Kazakh", 129_278_805, "6c59955f7f5e3fc50b130ced59a430eb4ef62d69155af4ce836db0dde4a42682"),
        piper("lb_LU-marylux-medium", "Luxembourgish", 67_184_319, "f11117fc121db0d5c4dc46e07eac7b1d8ea719b04f0b381836a23050310ea5a4"),
        piper("lv_LV-aivars-medium", "Latvian", 67_196_173, "a0df58c9924761e201ef75d82473b90bde32c5372267e9b4c628fdd267cf2508"),
        piper("ml_IN-meera-medium", "Malayalam", 67_195_010, "c8d870441e60870b7e8781c26eb22104aff51587b63bee94aba5e5f6c9fbb71f"),
        piper("ne_NP-google-medium", "Nepali", 80_242_801, "73c1e093451867779ec2b55cc3666de2db058019a2e7760e8c8f294a42caca2a"),
        piper("nl_BE-nathalie-medium", "Dutch", 67_226_472, "c7484313abea395243e872196bc75181c07aad8120b8a61e0f6f4b3863b6a7c8"),
        piper("no_NO-talesyntese-medium", "Norwegian", 67_194_829, "dcb7c36b7222e290cca1fd4cdecb474e7fd651dc5b0d3d42c33d416a8e62018b"),
        piper("pl_PL-gosia-medium", "Polish", 67_211_182, "75bd34dcbdc4dd98d763954756b4b34b4208100497c836381542e4d73dcefa9c"),
        piper("pt_PT-tugao-medium", "Portuguese (Portugal)", 67_195_014, "6b4f0a7bfc957bef26d6ac0703a8fa1ba659c8ab2df8b294f20206551859070c"),
        piper("ro_RO-mihai-medium", "Romanian", 67_182_137, "cfbee3cdc25cf063339f69e189f4d93cc3be6610d267567537323ced3cf87f4d"),
        piper("ru_RU-irina-medium", "Russian", 67_153_308, "1fc0f54e5e084fe287c07909f2f6e0ba6d857864cf800e3ab80286a4e8233008"),
        piper("sk_SK-lili-medium", "Slovak", 67_183_092, "a00dfbca5d34733862a59d9e309f4e94a92fccc9337c2b124de9a241b604fc0d"),
        piper("sl_SI-artur-medium", "Slovenian", 67_025_668, "df805f31c4513864edebf7e287cf0b54d72788fc661ed73f88dc4e5aabc5efd5"),
        piper("sr_RS-serbski_institut-medium", "Serbian", 80_298_557, "b8c100e5ff885d11ff96ad92a953f4b82e1cd649f9ac60a77ae4553d37507b2b"),
        piper("sv_SE-nst-medium", "Swedish", 67_144_884, "5aed0f5ab75135fc3ba6a4f3567e171d72df753305b08a01ca42f79411587f67"),
        piper("sw_CD-lanfrica-medium", "Swahili", 67_217_230, "93196704a0ae0c984806059480775d69dcc2419d0d545cddd0903d244dc66a72"),
        piper("tr_TR-dfki-medium", "Turkish", 67_201_221, "b63babaefcdd63202c7079aba25b72a2013825ad5895e2aeff03d1ae5a5b3bbf"),
        piper("uk_UA-ukrainian_tts-medium", "Ukrainian", 80_355_083, "92be6ff21ebb1cbcc0c71121a6437db8d060cee5785287aaa6a19a2548f59c03"),
        piper("vi_VN-vais1000-medium", "Vietnamese", 67_154_040, "fa1367710767d36ed5cf13b4a449e20c35ffd12791c2e47c2e64142bfa55551a"),
    )

    /** Every open-source voice, Kokoro's first. */
    val voices: List<VoiceChoice> = kokoroVoices + piperVoices

    /** Every model a voice or a listener needs, the detector among them. */
    val models: List<VoiceModel> = (listOf(VAD) + LISTENERS + voices.map { it.model }).distinctBy { it.id }

    const val DEFAULT_VOICE = "kokoro/af_heart"

    fun voice(id: String): VoiceChoice = voices.firstOrNull { it.id == id } ?: voices.first { it.id == DEFAULT_VOICE }

    fun model(id: String): VoiceModel? = models.firstOrNull { it.id == id }

    fun listener(id: String): VoiceModel = LISTENERS.firstOrNull { it.id == id } ?: WHISPER_BASE

    /** Every language a voice speaks, by its name. */
    val languages: List<String> = voices.map { it.language }.distinct().sorted()

    /** [language]'s voices, as the catalogue lists them. */
    fun voicesIn(language: String): List<VoiceChoice> = voices.filter { it.language == language }
}
