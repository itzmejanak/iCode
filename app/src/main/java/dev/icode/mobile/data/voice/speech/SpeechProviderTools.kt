package dev.icode.mobile.data.voice.speech

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.assistant.tools.textOrNull
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantToolName
import dev.icode.mobile.domain.voice.SpeechProvider
import dev.icode.mobile.domain.voice.SpeechProviderKind
import dev.icode.mobile.domain.voice.SpeechRules
import dev.icode.mobile.domain.voice.SpeechWay
import dev.icode.mobile.domain.voice.VoiceRules
import dev.icode.mobile.domain.voice.Voices
import java.util.Locale
import org.json.JSONObject

// The agent's hold on the speech providers (docs/voice-and-phone-control.md): what the Speech
// Providers pages show and do, through SpeechProviders' own operations, for listening and for reading
// aloud. A key is given, never read back; every change is asked about, in every mode: a provider
// outlasts the chat, and decides where the user's voice and the answers go.

/** The tools a chat has over [providers]. */
internal fun speechProviderTools(providers: SpeechProviders): List<AssistantTool> =
    listOf(ListSpeechProvidersTool(providers), SaveSpeechProviderTool(providers), RemoveSpeechProviderTool(providers))

/** The speech providers, which one listens and which one speaks, and how each did: read-only. */
internal class ListSpeechProvidersTool(private val providers: SpeechProviders) : AssistantTool {
    override val name = AssistantToolName.ListSpeechProviders.id
    override val description = "Lists the speech providers that hear the user's microphone, in the bubble and the chat, and that read the " +
        "answers aloud — Android's own and the ones the user added: each one's id, name, kind, model, language, whether its API key is " +
        "set (never the key), where its audio goes, how it reads aloud (voice, voice model, speed, where the text goes), its last " +
        "failure, and which one listens and which one speaks now. save_speech_provider and remove_speech_provider change them."
    override val parameters = emptyList<AssistantParameter>()
    override val kind = AssistantToolKind.Read

    override fun summary(arguments: JSONObject): String = "List the speech providers"

    override suspend fun run(arguments: JSONObject): String {
        val inUse = providers.inUse()
        val speaker = providers.speaker()
        val failures = providers.failures()
        return providers.list().joinToString("\n\n") { provider ->
            buildString {
                append(provider.id).append(" · ").append(provider.name).append(" · ").append(provider.kind.name)
                if (provider.id == inUse) append(" · listens now")
                if (provider.id == speaker) append(" · speaks now")
                val details = listOfNotNull(
                    provider.modelUsed?.let { "model: $it" },
                    "language: ${provider.language ?: "automatic"}",
                    provider.address?.let { "address: $it" },
                    when {
                        provider.keyFrom != null -> "key: the assistant's ${providers.assistantKeys().firstOrNull { it.first == provider.keyFrom }?.second ?: "provider"} (${provider.keyFrom})"
                        provider.kind.needsKey || provider.hasKey -> "key: ${if (provider.hasKey) "set" else "not set"}"
                        else -> null
                    },
                )
                append("\n").append(details.joinToString(" · "))
                append("\n").append(if (provider.kind.hears) "audio: ${SpeechRules.where(provider)}" else "listens: no, it only reads aloud")
                append("\n").append(reading(provider))
                failures[provider.id]?.let { append("\nlast failure: ").append(it) }
            }
        }
    }
}

/** What a provider does in [this] way, as the model is told it. */
private val SpeechWay.task: String
    get() = when (this) {
        SpeechWay.Listening -> "hears the microphone"
        SpeechWay.Speaking -> "reads the answers aloud"
    }

/** How [provider] reads aloud, as the agent is told it. */
private fun reading(provider: SpeechProvider): String {
    val speaking = provider.kind.speaking ?: return "reads aloud: no, it only listens"
    if (speaking.voices == Voices.Android) return "reads aloud: with the voice and language chosen in Android's settings"
    return "reads aloud: " + listOfNotNull(
        "voice: ${VoiceRules.voiceUsed(provider, Locale.getDefault().toLanguageTag()) ?: "its default"}",
        provider.voiceModelUsed?.let { "voice model: $it" },
        provider.speed?.let { "speed: ${VoiceRules.speedLabel(it)}" },
        "text to ${SpeechRules.where(provider, SpeechWay.Speaking).ifEmpty { "its server" }}",
    ).joinToString(" · ")
}

/** A provider added, or one changed; asked about each time. */
internal class SaveSpeechProviderTool(private val providers: SpeechProviders) : AssistantTool {
    override val name = AssistantToolName.SaveSpeechProvider.id
    override val description = "Adds a speech provider or changes one, as the Speech Providers page does; the user is asked first, every " +
        "time. To add, give kind, then what it needs: key (an API key the user gave), address for a server of its own, region for Azure, " +
        "model, language. To read aloud: voice, voice_model, speed — only where its kind takes them. To change one, give its id " +
        "(list_speech_providers) and only what changes; Android's changes only its language. use: listening makes it the one that hears " +
        "the microphone, speaking the one that reads the answers aloud, both both. A key is saved and never shown again; an OpenAI " +
        "provider may instead use the key of the assistant's own OpenAI provider (key_from, its id in the assistant's providers). The " +
        "user can test it, and hear it, on its page."
    override val parameters = listOf(
        AssistantParameter("id", "A provider's id, to change it; absent to add", required = false),
        AssistantParameter("kind", "Its kind, to add one", AssistantParameterType.Choice(KINDS.map { it.name }), required = false),
        AssistantParameter("name", "Its name; its kind's by default", required = false),
        AssistantParameter("key", "Its API key, as the user gave it", required = false),
        AssistantParameter("key_from", "OpenAI only: the id of the assistant's OpenAI provider whose key it uses instead", required = false),
        AssistantParameter("address", "Its server's address, for a kind that asks one", required = false),
        AssistantParameter("model", "The model to ask for; its kind's default when absent", required = false),
        AssistantParameter("language", "The language spoken, as a tag (en, en-US); the phone's when absent", required = false),
        AssistantParameter("region", "Where its servers are: Azure's region (eastus), or AssemblyAI's or ElevenLabs' server host", required = false),
        AssistantParameter("voice", "The voice that reads aloud, by its provider's name for it; its kind's default when absent", required = false),
        AssistantParameter("voice_model", "The model that reads aloud, where its kind has one to choose", required = false),
        AssistantParameter("speed", "How fast it reads: 0.75, 1, 1.25 or 1.5, where its kind takes a speed", AssistantParameterType.Number, required = false),
        AssistantParameter("use", "What it does from now on: hears the microphone, reads the answers aloud, or both", AssistantParameterType.Choice(USES), required = false),
    )
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String =
        existing(arguments)?.let { "Change the speech provider ${it.name}" } ?: "Add the speech provider ${drafted(arguments)?.name ?: arguments.textOrNull("kind").orEmpty()}"

    /** What the user is asked about: the key named, never shown. */
    override fun detail(arguments: JSONObject): String {
        val provider = runCatching { existing(arguments)?.let { changed(it, arguments) } ?: drafted(arguments) }.getOrNull()
        return listOfNotNull(
            "Speech: ${if (existing(arguments) != null) "change" else "add"}",
            provider?.let { "“${it.name}” · ${it.kind.label} · audio to ${SpeechRules.where(it).ifEmpty { "its server" }}" },
            listOfNotNull(
                "key: given".takeIf { arguments.textOrNull("key") != null },
                provider?.keyFrom?.let { from -> "key: the assistant's ${providers.assistantKeys().firstOrNull { it.first == from }?.second ?: from}" },
                provider?.modelUsed?.let { "model: $it" },
                provider?.language?.let { "language: $it" },
                provider?.region?.let { "region: $it" },
                provider?.voice?.let { "voice: $it" },
                provider?.voiceModel?.let { "voice model: $it" },
                provider?.speed?.let { "speed: ${VoiceRules.speedLabel(it)}" },
            ).joinToString(" · ").ifEmpty { null },
            runCatching { uses(arguments) }.getOrDefault(emptyList()).joinToString("\n") { way -> "it ${way.task} from now on" }.ifEmpty { null },
        ).joinToString("\n")
    }

    override suspend fun run(arguments: JSONObject): String {
        val existing = arguments.textOrNull("id")?.let { id -> existing(arguments) ?: throw AssistantToolException("There is no speech provider $id: list_speech_providers lists them") }
        val provider = existing?.let { changed(it, arguments) } ?: drafted(arguments) ?: throw AssistantToolException("Give kind, to add one: ${KINDS.joinToString { it.name }}")
        val ways = uses(arguments)
        // A way its kind does not serve is refused before anything is saved.
        ways.firstNotNullOfOrNull { SpeechRules.wayProblem(provider, it) }?.let { throw AssistantToolException(it) }
        val kept = providers.save(provider, arguments.textOrNull("key")).getOrElse { throw AssistantToolException(it.message ?: "Not saved") }
        ways.forEach { way -> providers.use(kept.id, way).getOrElse { throw AssistantToolException(it.message ?: "Not chosen") } }
        val all = providers.list()
        val serving = SpeechWay.entries.filter { kept.kind.serves(it) }.joinToString("") { way ->
            val chosen = all.firstOrNull { it.id == if (way == SpeechWay.Listening) providers.inUse() else providers.speaker() }
            if (chosen?.id == kept.id) "; it ${way.task}" else "; ${chosen?.name ?: "Android"} still ${way.task}"
        }
        return "${if (existing == null) "Added" else "Changed"} ${kept.name} (${kept.id})" + (if (kept.hasKey) ", key set" else "") + serving + "."
    }

    /** The ways `use` chooses it for; `true`, as before, is listening. */
    private fun uses(arguments: JSONObject): List<SpeechWay> = when (val use = arguments.opt("use")) {
        null, false, "" -> emptyList()
        true, "listening" -> listOf(SpeechWay.Listening)
        "speaking" -> listOf(SpeechWay.Speaking)
        "both" -> SpeechWay.entries
        else -> throw AssistantToolException("use is ${USES.joinToString(", ")}, not $use")
    }

    private fun existing(arguments: JSONObject): SpeechProvider? = arguments.textOrNull("id")?.let { id -> providers.list().firstOrNull { it.id == id } }

    /** A new provider from the fields; null without a kind. */
    private fun drafted(arguments: JSONObject): SpeechProvider? {
        val kind = arguments.textOrNull("kind")?.let { name ->
            KINDS.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: throw AssistantToolException("$name is not a kind: ${KINDS.joinToString { it.name }}")
        } ?: return null
        return changed(SpeechProvider("", kind, kind.label), arguments).copy(name = arguments.textOrNull("name") ?: kind.label)
    }

    /** [provider] with what the fields change; its kind stays. */
    private fun changed(provider: SpeechProvider, arguments: JSONObject): SpeechProvider {
        arguments.textOrNull("kind")?.takeIf { existing(arguments) != null && !it.equals(provider.kind.name, ignoreCase = true) }?.let {
            throw AssistantToolException("${provider.name} is ${provider.kind.label}: remove it and add a provider of the other kind")
        }
        return provider.copy(
            name = arguments.textOrNull("name") ?: provider.name,
            address = arguments.textOrNull("address") ?: provider.address,
            model = arguments.textOrNull("model") ?: provider.model,
            language = arguments.textOrNull("language") ?: provider.language,
            keyFrom = arguments.textOrNull("key_from") ?: provider.keyFrom.takeIf { arguments.textOrNull("key") == null },
            region = arguments.textOrNull("region") ?: provider.region,
            voice = arguments.textOrNull("voice") ?: provider.voice,
            voiceModel = arguments.textOrNull("voice_model") ?: provider.voiceModel,
            speed = arguments.optDouble("speed").takeUnless { it.isNaN() }?.toFloat() ?: provider.speed,
        )
    }

    private companion object {
        /** The kinds the agent may add: Android's is always there. */
        val KINDS = SpeechProviderKind.entries.filter { it != SpeechProviderKind.Android }

        val USES = listOf("listening", "speaking", "both")
    }
}

/** One of the user's providers forgotten, its key with it; asked about each time. */
internal class RemoveSpeechProviderTool(private val providers: SpeechProviders) : AssistantTool {
    override val name = AssistantToolName.RemoveSpeechProvider.id
    override val description = "Removes one of the user's speech providers, by its id (list_speech_providers): its key is forgotten. " +
        "If it heard the microphone, Android's recognizer does from now on; if it read the answers aloud, Android's voice does. The user " +
        "is asked first, every time. Android's cannot be removed."
    override val parameters = listOf(AssistantParameter("id", "The provider's id"))
    override val kind = AssistantToolKind.Change
    override val asksAlways = true

    override fun summary(arguments: JSONObject): String = "Remove the speech provider ${named(arguments)}"

    override fun detail(arguments: JSONObject): String = "Speech: remove “${named(arguments)}” · its key is forgotten"

    override suspend fun run(arguments: JSONObject): String {
        val id = arguments.textOrNull("id") ?: throw AssistantToolException("Name the provider: its id, from list_speech_providers")
        val listened = providers.inUse() == id
        val spoke = providers.speaker() == id
        val removed = providers.remove(id).getOrElse { throw AssistantToolException(it.message ?: "Not removed") }
        return "Removed ${removed.name}." + (if (listened) " Android's recognizer hears the microphone from now on." else "") +
            if (spoke) " Android's voice reads the answers aloud from now on." else ""
    }

    private fun named(arguments: JSONObject): String {
        val id = arguments.textOrNull("id").orEmpty()
        return providers.list().firstOrNull { it.id == id }?.name ?: id
    }
}
