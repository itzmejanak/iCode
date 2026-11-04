package dev.icode.mobile.domain.voice

// Voice in and out (docs/phone-agent-plan.md §5.8), with Android's own speech recognition and text-to-speech:
// what the user chose, and what the screens show of it.

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
    /** When answers are read aloud: the user's one voice setting. */
    val spoken: SpokenReplies = SpokenReplies.DEFAULT,
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
}
