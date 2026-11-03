package dev.icode.mobile.domain.voice

// Voice in and out (docs/phone-agent-plan.md §5.8): what the user chose, and what the screens show of it.

/** Who hears or speaks: Android's own engine, or an open-source model on this phone. */
enum class VoiceEngine(val label: String) {
    Android("Android"),
    OnPhone("Open-source, on this phone"),
}

/** When the assistant's answers are read aloud. */
enum class SpokenReplies(val label: String, val description: String) {
    Off("Off", "Answers are only written"),
    InBubble("In the bubble", "Answers to the bubble or the microphone"),
    Always("Always", "Every answer"),
    ;

    /** Whether a turn's answer is spoken; [aloud] when its message was sent by voice or from the bubble. */
    fun speaks(aloud: Boolean): Boolean = when (this) {
        Off -> false
        InBubble -> aloud
        Always -> true
    }

    companion object {
        val DEFAULT = InBubble

        fun of(name: String?): SpokenReplies = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** What the user set for voice; models are named by their catalogue ids ([VoiceCatalog]). */
data class VoiceSettings(
    val listening: VoiceEngine = VoiceEngine.Android,
    val listenModel: String = VoiceCatalog.WHISPER_BASE.id,
    val speaking: VoiceEngine = VoiceEngine.Android,
    /** An open-source voice: its id in [VoiceCatalog.voices]. */
    val voice: String = VoiceCatalog.DEFAULT_VOICE,
    val spoken: SpokenReplies = SpokenReplies.DEFAULT,
) {
    /** What listening needs on this phone: nothing for Android's; the detector and the chosen model for one here. */
    val listenModels: List<VoiceModel>
        get() = if (listening == VoiceEngine.OnPhone) listOf(VoiceCatalog.VAD, VoiceCatalog.listener(listenModel)) else emptyList()

    /** What speaking needs on this phone: nothing for Android's; the chosen voice's model for one here. */
    val speakModels: List<VoiceModel>
        get() = if (speaking == VoiceEngine.OnPhone) listOf(VoiceCatalog.voice(voice).model) else emptyList()
}

/** A model's files on this phone. */
sealed interface ModelState {
    data object Absent : ModelState
    data class Downloading(val fraction: Float) : ModelState
    /** Downloaded and checked; its files are being taken out of the archive. */
    data object Unpacking : ModelState
    /** On the phone, taking [bytes]. */
    data class Ready(val bytes: Long) : ModelState
    data class Failed(val reason: String) : ModelState
}

/** Where the microphone was tapped: the words heard go back there. */
enum class VoiceFrom { Bubble, Chat }

/**
 * Words heard, as far as they have come; [final] once the user stopped talking, and then they are
 * sent. [problem] ends a hearing that heard nothing, said where the microphone was tapped. [id] tells
 * one hearing from the next.
 */
data class Heard(val id: Long, val from: VoiceFrom, val text: String, val final: Boolean, val problem: String? = null) {
    /** What is sent: the words, once the user stopped talking. */
    val sent: String? get() = text.takeIf { final && problem == null && it.isNotBlank() }
}

/** Voice as the screens show it. */
data class VoiceState(
    val settings: VoiceSettings = VoiceSettings(),
    val models: Map<String, ModelState> = emptyMap(),
    /** The microphone may be used: Android granted it. */
    val microphone: Boolean = false,
    /**
     * The microphone works while iCode is away: phone control's foreground service holds it, started
     * while iCode was on screen (docs/phone-agent-plan.md §5.8).
     */
    val away: Boolean = false,
    val heard: Heard? = null,
    val speaking: Boolean = false,
) {
    /** The microphone listens for [from]. */
    fun listening(from: VoiceFrom): Boolean = heard?.let { it.from == from && !it.final } == true

    /** Why the last hearing for [from] heard nothing. */
    fun problem(from: VoiceFrom): String? = heard?.takeIf { it.from == from }?.problem

    fun state(model: VoiceModel): ModelState = models[model.id] ?: ModelState.Absent
}
