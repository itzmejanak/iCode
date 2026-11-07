package dev.icode.mobile.domain.voice

// Speech recognition and reading aloud with any provider (docs/speech-plan.md, docs/tts-plan.md): what
// kinds of provider there are, a provider as the user set it up, and what a hearing or a speech
// session that failed says, the same for every one.

/** A kind of provider: what its form asks for, what it does with the audio, and how it reads aloud. */
enum class SpeechProviderKind(
    val label: String,
    /** Words come while the user speaks; else after they stop. */
    val live: Boolean,
    /** A request without a key is refused. */
    val needsKey: Boolean,
    /** The form asks for the server's address. */
    val asksAddress: Boolean,
    /** The models it suggests, the first its default; the user may type another. Empty: none to choose. */
    val models: List<String>,
    /** Where the audio goes, said on its page; null: this phone, or the address given. */
    val host: String?,
    /** How it reads aloud; null: it does not. */
    val speaking: SpeakingKind? = null,
    /** It hears the user; else it only reads aloud. */
    val hears: Boolean = true,
) {
    /** Android's own recognizer: on the phone where Android offers it, else the phone's recognition service. */
    Android(
        "Android", live = true, needsKey = false, asksAddress = false, models = emptyList(), host = null,
        speaking = SpeakingKind(host = null, voices = Voices.Android),
    ),

    /** AssemblyAI's streaming speech-to-text (v3); its own examples stream with `universal-3-5-pro`. */
    AssemblyAI(
        "AssemblyAI", live = true, needsKey = true, asksAddress = false,
        models = listOf(
            "universal-3-5-pro", "universal-3-6-pro", "universal-3-6", "u3-rt-pro",
            "universal-streaming-multilingual", "universal-streaming-english", "whisper-rt",
        ),
        host = "streaming.assemblyai.com",
    ),

    /**
     * OpenAI's realtime transcription. `gpt-live-transcribe` is its realtime model; `gpt-realtime-whisper`
     * is left out: its sessions take no turn detection, and the hearing ends by it.
     */
    OpenAI("OpenAI", live = true, needsKey = true, asksAddress = false, models = listOf("gpt-live-transcribe", "gpt-transcribe"), host = "api.openai.com"),

    /** A server that speaks OpenAI's `/audio/transcriptions`: on this phone, or one the user names. */
    OpenAICompatible("OpenAI-compatible", live = false, needsKey = false, asksAddress = true, models = emptyList(), host = null),

    /** Deepgram's live speech-to-text. */
    Deepgram("Deepgram", live = true, needsKey = true, asksAddress = false, models = listOf("nova-3", "nova-2"), host = "api.deepgram.com"),

    /** Google Cloud Speech-to-Text v1, which takes an API key: v2 takes none. */
    Google("Google Cloud", live = false, needsKey = true, asksAddress = false, models = listOf("latest_short", "latest_long"), host = "speech.googleapis.com"),
    ;

    /** It serves [way]. */
    fun serves(way: SpeechWay): Boolean = when (way) {
        SpeechWay.Listening -> hears
        SpeechWay.Speaking -> speaking != null
    }

    companion object {
        fun of(name: String?): SpeechProviderKind? = entries.firstOrNull { it.name == name }
    }
}

/** How a kind reads aloud (docs/tts-plan.md §4.1). */
class SpeakingKind(
    /** Where what is read aloud goes, said on its page; null: this phone, or the address given. */
    val host: String?,
    val voices: Voices,
    /** The speaking models it suggests, the first its default; the user may type another. Empty: none to choose. */
    val models: List<String> = emptyList(),
    /** The speeds it takes, 1 its voice's own pace. Empty: it takes none. */
    val speeds: List<Float> = emptyList(),
)

/** How a kind's voices are chosen. */
sealed interface Voices {
    /** In Android's settings: iCode chooses none. */
    data object Android : Voices
}

/**
 * A provider as the user set it up; its key is never here, only whether one is set. [model] and
 * [language] null: the kind's default model, and the language the phone speaks.
 */
data class SpeechProvider(
    val id: String,
    val kind: SpeechProviderKind,
    val name: String,
    val address: String? = null,
    val model: String? = null,
    /** A BCP-47 tag (`en-US`). */
    val language: String? = null,
    val hasKey: Boolean = false,
    /** The assistant's OpenAI provider whose key this one uses, by its ID (OpenAI only): linked, never copied. */
    val keyFrom: String? = null,
    /** The voice that reads aloud; null: its kind's default. */
    val voice: String? = null,
    /** The model that reads aloud; null: its kind's default. */
    val voiceModel: String? = null,
    /** How fast it reads, 1 its voice's own pace; null: 1. */
    val speed: Float? = null,
) {
    /** The model a hearing asks for. */
    val modelUsed: String? get() = model?.takeIf { it.isNotBlank() } ?: kind.models.firstOrNull()

    /** The model a speech session asks for. */
    val voiceModelUsed: String? get() = voiceModel?.takeIf { it.isNotBlank() } ?: kind.speaking?.models?.firstOrNull()

    companion object {
        /** Android's recognizer: always there, and the one used until the user chooses another. */
        const val ANDROID = "android"

        val Android = SpeechProvider(ANDROID, SpeechProviderKind.Android, "Android")
    }
}

/** The two ways a provider serves: it hears the user, or it reads the answers aloud (docs/tts-plan.md). */
enum class SpeechWay { Listening, Speaking }

/** Why a hearing heard nothing, or a speech session said nothing, the same for every provider (docs/speech-plan.md §4.6). */
enum class SpeechFailure {
    NoKey,
    KeyRefused,
    NoCredit,
    Busy,
    Unreachable,
    Language,
    TooLong,
    Server,
    /** The microphone may not be used. */
    NotAllowed,
    Microphone,
    /** The phone has no recognizer, or no voice, of this kind. */
    Unavailable,
    NothingHeard,
    /** Anything else: the provider's own words follow. */
    Failed,
}

object SpeechRules {
    /** Where the user sets the providers up, named in what a failure says. */
    const val PAGE = "Settings › Assistant › Voice"

    /**
     * Why [provider] cannot be saved as it is, among [others]; null when it can. [keyGiven]: a key
     * comes with it; [keySaved]: one is kept already.
     */
    fun problem(provider: SpeechProvider, others: List<SpeechProvider>, keyGiven: Boolean, keySaved: Boolean): String? {
        val name = provider.name.trim()
        val address = provider.address?.trim().orEmpty()
        val language = provider.language?.trim().orEmpty()
        return when {
            provider.kind == SpeechProviderKind.Android && provider.id != SpeechProvider.ANDROID -> "Android's recognizer is already there"
            name.isEmpty() -> "Name the provider"
            others.any { it.id != provider.id && it.name.trim().equals(name, ignoreCase = true) } -> "Another provider is called $name"
            provider.keyFrom != null && provider.kind != SpeechProviderKind.OpenAI -> "Only OpenAI can use the assistant's key"
            provider.kind.needsKey && !keyGiven && !keySaved && provider.keyFrom == null -> "Enter $name's API key"
            provider.kind.asksAddress && address.isEmpty() -> "Enter the server's address"
            provider.kind.asksAddress && !addressAllowed(address) -> "Use https:// (http only to this phone)"
            language.isNotEmpty() && !LANGUAGE.matches(language) -> "Write the language as a tag: en, or en-US"
            else -> speakingProblem(provider, name)
        }
    }

    /** Why [provider]'s speaking fields cannot be saved: a field its kind does not take, said in its words. */
    private fun speakingProblem(provider: SpeechProvider, name: String): String? {
        val speaking = provider.kind.speaking
        val speaks = provider.voice != null || provider.voiceModel != null || provider.speed != null
        return when {
            speaks && speaking == null -> "$name does not read aloud"
            speaks && speaking?.voices == Voices.Android -> "Android's voice is chosen in Android's settings"
            provider.voiceModel != null && speaking?.models?.isEmpty() == true -> "$name has no models to choose for reading aloud"
            provider.speed != null && speaking?.speeds?.contains(provider.speed) == false ->
                speaking.speeds.takeIf { it.isNotEmpty() }?.let { "$name reads at ${it.joinToString { speed -> speedLabel(speed) }}" } ?: "$name reads at one speed"
            else -> null
        }
    }

    /** A speed as the pages say it: 1 is Normal. */
    fun speedLabel(speed: Float): String = if (speed == 1f) "Normal" else "${speed.toString().removeSuffix(".0")}×"

    /** [provider] can serve [way], or why not. */
    fun wayProblem(provider: SpeechProvider, way: SpeechWay): String? = when {
        provider.kind.serves(way) -> null
        way == SpeechWay.Listening -> "${provider.name} cannot listen yet: choose it for reading aloud"
        else -> "${provider.name} cannot read aloud yet: choose it for listening"
    }

    /** An address iCode sends audio to: https, or plain http only to this phone. */
    fun addressAllowed(address: String): Boolean {
        val lower = address.trim().lowercase()
        return lower.startsWith("https://") || LOCAL.any { lower.startsWith(it) }
    }

    /** What a page and the agent say of [provider]: where its audio goes, or what it reads aloud, for [way]. */
    fun where(provider: SpeechProvider, way: SpeechWay = SpeechWay.Listening): String = when {
        provider.kind == SpeechProviderKind.Android -> if (way == SpeechWay.Listening) "On this phone, or its recognition service" else "Android's text-to-speech"
        provider.kind.asksAddress -> provider.address?.substringAfter("://")?.substringBefore('/').orEmpty()
        way == SpeechWay.Listening -> provider.kind.host.orEmpty()
        else -> provider.kind.speaking?.host.orEmpty()
    }

    /** The language's tag as an ISO-639-1 code: `en-US` is `en`. */
    fun languageCode(tag: String): String = tag.substringBefore('-').lowercase()

    private val LANGUAGE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    private val LOCAL = listOf("http://127.0.0.1", "http://localhost")

    /**
     * What [failure] says, naming [provider] — Android's as "Speech recognition" or "Text-to-speech", as
     * they always have been — for the [way] it served. [detail] is the provider's own words where it gave
     * some; for a network failure, iCode's.
     */
    fun words(failure: SpeechFailure, provider: SpeechProvider, detail: String? = null, way: SpeechWay = SpeechWay.Listening): String {
        val android = provider.kind == SpeechProviderKind.Android
        val listening = way == SpeechWay.Listening
        val who = when {
            !android -> provider.name
            listening -> "Speech recognition"
            else -> "Text-to-speech"
        }
        return when (failure) {
            SpeechFailure.NoKey -> "Enter $who's key in $PAGE"
            SpeechFailure.KeyRefused -> "$who refused the key: check it in $PAGE"
            SpeechFailure.NoCredit -> "$who says the account has no credit left"
            SpeechFailure.Busy -> "$who is busy: try again"
            SpeechFailure.Unreachable -> detail ?: "$who needs the network, and it is not there"
            SpeechFailure.Language -> if (listening) "$who cannot hear this language" else "$who cannot speak this language"
            SpeechFailure.TooLong -> "That was longer than $who takes at once"
            SpeechFailure.Server -> "$who did not answer: try again"
            SpeechFailure.NotAllowed -> "iCode may not use the microphone"
            SpeechFailure.Microphone -> "The microphone could not be read"
            SpeechFailure.Unavailable -> when {
                !android -> "$who is not available on this phone"
                listening -> "This phone has no speech recognition"
                else -> "This phone has no text-to-speech engine"
            }
            SpeechFailure.NothingHeard -> "Nothing was heard"
            SpeechFailure.Failed -> listOfNotNull("$who failed", detail?.take(DETAIL_CHARS)).joinToString(": ").let { if (detail == null) "$it: try again" else it }
        }
    }

    /** A provider's own words are shown up to this long. */
    private const val DETAIL_CHARS = 160
}
