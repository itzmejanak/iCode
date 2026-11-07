package dev.icode.mobile.domain.voice

// Speech recognition with any provider (docs/speech-plan.md): what kinds of provider there are, a
// provider as the user set it up, and what a hearing that failed says, the same for every one.

/** A kind of provider: what its form asks for, and what it does with the audio. */
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
) {
    /** Android's own recognizer: on the phone where Android offers it, else the phone's recognition service. */
    Android("Android", live = true, needsKey = false, asksAddress = false, models = emptyList(), host = null),

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

    companion object {
        fun of(name: String?): SpeechProviderKind? = entries.firstOrNull { it.name == name }
    }
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
) {
    /** The model a hearing asks for. */
    val modelUsed: String? get() = model?.takeIf { it.isNotBlank() } ?: kind.models.firstOrNull()

    companion object {
        /** Android's recognizer: always there, and the one used until the user chooses another. */
        const val ANDROID = "android"

        val Android = SpeechProvider(ANDROID, SpeechProviderKind.Android, "Android")
    }
}

/** Why a hearing heard nothing, the same for every provider (docs/speech-plan.md §4.6). */
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
    /** The phone has no recognizer of this kind. */
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
            else -> null
        }
    }

    /** An address iCode sends audio to: https, or plain http only to this phone. */
    fun addressAllowed(address: String): Boolean {
        val lower = address.trim().lowercase()
        return lower.startsWith("https://") || LOCAL.any { lower.startsWith(it) }
    }

    /** What a page and the agent say of [provider]: its kind, and where its audio goes. */
    fun where(provider: SpeechProvider): String = when {
        provider.kind == SpeechProviderKind.Android -> "On this phone, or its recognition service"
        provider.kind.asksAddress -> provider.address?.substringAfter("://")?.substringBefore('/').orEmpty()
        else -> provider.kind.host.orEmpty()
    }

    /** The language's tag as an ISO-639-1 code: `en-US` is `en`. */
    fun languageCode(tag: String): String = tag.substringBefore('-').lowercase()

    private val LANGUAGE = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    private val LOCAL = listOf("http://127.0.0.1", "http://localhost")

    /**
     * What [failure] says, naming [provider]: Android's as "Speech recognition", as it always has.
     * [detail] is the provider's own words where it gave some — for a network failure, iCode's.
     */
    fun words(failure: SpeechFailure, provider: SpeechProvider, detail: String? = null): String {
        val who = if (provider.kind == SpeechProviderKind.Android) "Speech recognition" else provider.name
        return when (failure) {
            SpeechFailure.NoKey -> "Enter $who's key in $PAGE"
            SpeechFailure.KeyRefused -> "$who refused the key: check it in $PAGE"
            SpeechFailure.NoCredit -> "$who says the account has no credit left"
            SpeechFailure.Busy -> "$who is busy: try again"
            SpeechFailure.Unreachable -> detail ?: "$who needs the network, and it is not there"
            SpeechFailure.Language -> "$who cannot hear this language"
            SpeechFailure.TooLong -> "That was longer than $who takes at once"
            SpeechFailure.Server -> "$who did not answer: try again"
            SpeechFailure.NotAllowed -> "iCode may not use the microphone"
            SpeechFailure.Microphone -> "The microphone could not be read"
            SpeechFailure.Unavailable -> if (provider.kind == SpeechProviderKind.Android) "This phone has no speech recognition" else "$who is not available on this phone"
            SpeechFailure.NothingHeard -> "Nothing was heard"
            SpeechFailure.Failed -> listOfNotNull("$who failed", detail?.take(DETAIL_CHARS)).joinToString(": ").let { if (detail == null) "$it: try again" else it }
        }
    }

    /** A provider's own words are shown up to this long. */
    private const val DETAIL_CHARS = 160
}
