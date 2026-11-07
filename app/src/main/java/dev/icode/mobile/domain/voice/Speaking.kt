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
    /** What its page says of how it reads, where it differs from the others. */
    val note: String? = null,
) {
    companion object {
        /** The speeds offered where an API takes one: each inside every such API's range. */
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

        /** OpenAI's realtime voices, as its spec lists them: `marin` first, which it recommends with `cedar` "for best quality". */
        val OPENAI_VOICES = listOf("marin", "cedar", "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse")

        /** AssemblyAI's eleven built-in voices, `aai-jane` its default; each speaks its six languages. */
        val ASSEMBLYAI_VOICES = listOf(
            "aai-jane", "aai-alba", "aai-anna", "aai-charles", "aai-eve", "aai-george", "aai-jean", "aai-mary", "aai-michael", "aai-paul", "aai-vera",
        )

        /**
         * Deepgram's voices, as its API spec lists them (2026-11-05): each name ends in its language. Its
         * own default, `aura-asteria-en`, first; then the 90 Aura-2 voices; then the older Aura ones.
         */
        val DEEPGRAM_VOICES = listOf(
        "aura-asteria-en", "aura-2-amalthea-en", "aura-2-andromeda-en", "aura-2-apollo-en", "aura-2-arcas-en", "aura-2-aries-en",
        "aura-2-asteria-en", "aura-2-athena-en", "aura-2-atlas-en", "aura-2-aurora-en", "aura-2-callista-en", "aura-2-cora-en", "aura-2-cordelia-en",
        "aura-2-delia-en", "aura-2-draco-en", "aura-2-electra-en", "aura-2-harmonia-en", "aura-2-helena-en", "aura-2-hera-en", "aura-2-hermes-en",
        "aura-2-hyperion-en", "aura-2-iris-en", "aura-2-janus-en", "aura-2-juno-en", "aura-2-jupiter-en", "aura-2-luna-en", "aura-2-mars-en",
        "aura-2-minerva-en", "aura-2-neptune-en", "aura-2-odysseus-en", "aura-2-ophelia-en", "aura-2-orion-en", "aura-2-orpheus-en",
        "aura-2-pandora-en", "aura-2-phoebe-en", "aura-2-pluto-en", "aura-2-saturn-en", "aura-2-selene-en", "aura-2-thalia-en", "aura-2-theia-en",
        "aura-2-vesta-en", "aura-2-zeus-en", "aura-2-agustina-es", "aura-2-alvaro-es", "aura-2-antonia-es", "aura-2-aquila-es", "aura-2-carina-es",
        "aura-2-celeste-es", "aura-2-diana-es", "aura-2-estrella-es", "aura-2-gloria-es", "aura-2-javier-es", "aura-2-luciano-es",
        "aura-2-nestor-es", "aura-2-olivia-es", "aura-2-selena-es", "aura-2-silvia-es", "aura-2-sirio-es", "aura-2-valerio-es", "aura-2-aurelia-de",
        "aura-2-elara-de", "aura-2-fabian-de", "aura-2-julius-de", "aura-2-kara-de", "aura-2-lara-de", "aura-2-viktoria-de", "aura-2-beatrix-nl",
        "aura-2-cornelia-nl", "aura-2-daphne-nl", "aura-2-hestia-nl", "aura-2-lars-nl", "aura-2-leda-nl", "aura-2-rhea-nl", "aura-2-roman-nl",
        "aura-2-sander-nl", "aura-2-agathe-fr", "aura-2-hector-fr", "aura-2-cesare-it", "aura-2-cinzia-it", "aura-2-demetra-it",
        "aura-2-dionisio-it", "aura-2-elio-it", "aura-2-flavio-it", "aura-2-livia-it", "aura-2-maia-it", "aura-2-melia-it", "aura-2-ama-ja",
        "aura-2-ebisu-ja", "aura-2-fujin-ja", "aura-2-izanami-ja", "aura-2-uzume-ja", "aura-angus-en", "aura-arcas-en", "aura-athena-en",
        "aura-helios-en", "aura-hera-en", "aura-luna-en", "aura-orion-en", "aura-orpheus-en", "aura-perseus-en", "aura-stella-en", "aura-zeus-en",
        )
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

    /**
     * The ones its spec fixes, the first its default. [own]: an account's own voices are named too.
     * [suffixed]: each name ends in its language (`-en`), and only the provider's language's are offered.
     */
    data class Fixed(val names: List<String>, val own: Boolean = false, val suffixed: Boolean = false) : Voices
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

/** What reading aloud allows (docs/tts-plan.md §4.5): the speaking fields a kind takes, its region, its voices. */
object VoiceRules {
    /** Why [provider]'s region or speaking fields cannot be saved, said with its [name]; null when they can. */
    fun problem(provider: SpeechProvider, name: String): String? = regionProblem(provider, name) ?: fieldsProblem(provider, name)

    /** Why [provider]'s speaking fields cannot be saved: a field its kind does not take, said in its words. */
    private fun fieldsProblem(provider: SpeechProvider, name: String): String? {
        val speaking = provider.kind.speaking
        val speaks = provider.voice != null || provider.voiceModel != null || provider.speed != null
        return when {
            speaks && speaking == null -> "$name does not read aloud"
            speaks && speaking?.voices == Voices.Android -> "Android's voice is chosen in Android's settings"
            provider.voice != null && (speaking?.voices as? Voices.Fixed)?.let { !it.own && provider.voice !in it.names } == true ->
                "$name has no voice ${provider.voice}"
            provider.voiceModel != null && speaking != null && speaking.models.isEmpty() && !speaking.listsModels && !provider.kind.asksAddress ->
                "$name has no models to choose for reading aloud"
            provider.speed != null && speaking?.speeds?.contains(provider.speed) == false ->
                speaking.speeds.takeIf { it.isNotEmpty() }?.let { "$name reads at ${it.joinToString { speed -> speedLabel(speed) }}" } ?: "$name reads at one speed"
            else -> null
        }
    }

    /** Why [provider]'s region cannot be saved. */
    private fun regionProblem(provider: SpeechProvider, name: String): String? {
        val region = provider.region?.trim().orEmpty()
        return when (val regions = provider.kind.speaking?.regions) {
            null -> "$name has one place: it takes no region".takeIf { region.isNotEmpty() }
            is Regions.Typed -> when {
                region.isEmpty() -> "Enter $name's region: ${regions.example}, for one"
                !REGION.matches(region) -> "Write the region in lower case, as ${regions.example} is"
                else -> null
            }
            is Regions.Fixed -> "$name's servers are ${regions.hosts.joinToString { it.second }}".takeIf { region.isNotEmpty() && regions.hosts.none { it.second == region } }
        }
    }

    /** Where [provider] reads aloud: its host, in its region. */
    fun host(provider: SpeechProvider): String {
        val speaking = provider.kind.speaking ?: return ""
        val host = speaking.host.orEmpty()
        return when (val regions = speaking.regions) {
            null -> host
            is Regions.Typed -> provider.region?.let { host.replace("{region}", it) }.orEmpty()
            is Regions.Fixed -> provider.region?.takeIf { region -> regions.hosts.any { it.second == region } } ?: host
        }
    }

    /**
     * The voices [provider]'s kind fixes that it offers: for a kind whose names end in their language,
     * those of its language — the phone's, [phone], when none is set.
     */
    fun voices(provider: SpeechProvider, phone: String): List<String> {
        val fixed = provider.kind.speaking?.voices as? Voices.Fixed ?: return emptyList()
        if (!fixed.suffixed) return fixed.names
        val code = SpeechRules.languageCode(provider.language ?: phone)
        return fixed.names.filter { it.substringAfterLast('-') == code }
    }

    /** The voice a speech session asks for: the one chosen, else the first its kind offers; null: the provider's own choice. */
    fun voiceUsed(provider: SpeechProvider, phone: String): String? = provider.voice?.takeIf { it.isNotBlank() } ?: voices(provider, phone).firstOrNull()

    /** A speed as the pages say it: 1 is Normal. */
    fun speedLabel(speed: Float): String = if (speed == 1f) "Normal" else "${speed.toString().removeSuffix(".0")}×"

    private val REGION = Regex("^[a-z0-9]+$")
}
