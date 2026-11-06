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
