package dev.icode.mobile.domain.voice

// Reading aloud with any provider (docs/tts-plan.md §4.1): how a kind speaks — its voices, models,
// speeds and servers — and what its API lists for a provider's page.

/** How a kind reads aloud (docs/tts-plan.md §4.1). */
class SpeakingKind(
    /** Where what is read aloud goes, said on its page — `{region}` the provider's region; null: this phone, or the address given. */
    val host: String?,
    val voices: Voices,
    /** The speaking models it suggests, the first its default; the user may type another. Empty: none to choose. */
    val models: List<String> = emptyList(),
    /** Its API lists its speaking models, as it lists its voices. */
    val listsModels: Boolean = false,
    /** The speeds it takes, 1 its voice's own pace. Empty: it takes none. */
    val speeds: List<Float> = emptyList(),
    /** Where its servers are, when the account chooses; null: one place. */
    val regions: Regions? = null,
) {
    companion object {
        /** The speeds offered where an API takes one: each inside every such API's range. */
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)
    }
}

/** How a kind's voices are chosen. */
sealed interface Voices {
    /** In Android's settings: iCode chooses none. */
    data object Android : Voices

    /** Listed by the provider's API, with the provider's key, for its language; the first is its default. */
    data object Listed : Voices

    /** Typed by name, as [example] is: the server's own. */
    data class Typed(val example: String) : Voices
}

/** A voice or a model a provider's API listed: its ID, and the name it shows. */
data class VoiceOption(val id: String, val label: String)

/** What a provider's API listed for its page — its voices, and its models where it lists them — or why it could not. */
data class VoiceChoices(val voices: List<VoiceOption> = emptyList(), val models: List<VoiceOption> = emptyList(), val problem: String? = null)

/** Where a kind's servers are. */
sealed interface Regions {
    /** Typed by the name the provider gives it, as [example] is. */
    data class Typed(val example: String) : Regions

    /** One of these servers, each by its name and host; the first is the default. */
    data class Fixed(val hosts: List<Pair<String, String>>) : Regions
}
