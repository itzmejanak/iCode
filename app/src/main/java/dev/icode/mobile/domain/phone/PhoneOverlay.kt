package dev.icode.mobile.domain.phone

import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.McpQuestion
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState

/**
 * What iCode shows over other apps (docs/phone-agent-plan.md §5.6, §5.7): one bubble while phone control
 * is on and the user has not put it away — never over iCode itself — and the glow while a turn acts on
 * the phone, dimmed while it waits on the user. Put away, the bubble takes the glow with it.
 */
data class PhoneOverlay(
    val bubble: Boolean,
    val glow: Glow,
    /** What the bubble shows ([Look]). */
    val look: Look,
    /** A turn runs: Stop is offered, and dragging the bubble away stops it. */
    val working: Boolean,
    /** A question waits for the user: a dot on the bubble, and the question under its bar. */
    val asking: Boolean,
    /** What the turn is doing now, in its own words. */
    val step: String?,
) {
    enum class Glow { Off, On, Dim }

    /**
     * The bubble's six states (owner's design, 2026-11-03). The agent's activity shows before the
     * microphone's, so muting never hides that it works; the microphone shows as listening only while
     * it listens.
     */
    enum class Look {
        /** Nothing going on: the iCode mark, tucked at the edge after a while. */
        Idle,

        /** The microphone hears the user: a waveform in a ring. */
        Listening,

        /** A voice session the user muted: the microphone off, the text bar open. */
        Muted,

        /** A turn works, not on the phone: a ring turns round the mark. */
        Thinking,

        /** A turn acts on the phone: the glow is on, and a ring turns round a pointer. */
        Acting,

        /** An answer is read aloud: a waveform; a tap stops it. */
        Speaking,
    }

    companion object {
        /** [muted]: the bubble's voice session was muted, and its text bar shows. [question]: an MCP server asks the user. */
        fun of(assistant: AssistantState, voice: VoiceState, access: PhoneAccess, iCodeOnScreen: Boolean, muted: Boolean, question: McpQuestion? = null): PhoneOverlay {
            val turn = assistant.turn
            val asking = turn != null && (assistant.approval != null || assistant.pause != null || question != null)
            val glow = when {
                !access.bubble || turn?.onPhone != true -> Glow.Off
                asking -> Glow.Dim
                else -> Glow.On
            }
            val look = when {
                voice.listening(VoiceFrom.Bubble) -> Look.Listening
                voice.speaking -> Look.Speaking
                turn?.onPhone == true -> Look.Acting
                turn != null -> Look.Thinking
                muted -> Look.Muted
                else -> Look.Idle
            }
            return PhoneOverlay(bubble = access.bubble && !iCodeOnScreen, glow = glow, look = look, working = turn != null, asking = asking, step = turn?.step)
        }
    }
}
