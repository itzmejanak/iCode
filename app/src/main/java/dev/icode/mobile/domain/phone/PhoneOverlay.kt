package dev.icode.mobile.domain.phone

import dev.icode.mobile.domain.AssistantState

/**
 * What iCode shows over other apps (docs/phone-agent-plan.md §5.6, §5.7): the bubble while phone
 * control is on and the user has not put it away — never over iCode itself — and the glow while a turn
 * acts on the phone, dimmed while it waits on the user. Put away, the bubble takes the glow with it.
 */
data class PhoneOverlay(
    val bubble: Boolean,
    val glow: Glow,
    /** A turn runs: a ring turns round the bubble. */
    val working: Boolean,
    /** A question waits for the user: a dot on the bubble, and the question in its panel. */
    val asking: Boolean,
    /** What the turn is doing now, in its own words. */
    val step: String?,
) {
    enum class Glow { Off, On, Dim }

    companion object {
        fun of(assistant: AssistantState, access: PhoneAccess, iCodeOnScreen: Boolean): PhoneOverlay {
            val turn = assistant.turn
            val asking = turn != null && (assistant.approval != null || assistant.pause != null)
            val glow = when {
                !access.bubble || turn?.onPhone != true -> Glow.Off
                asking -> Glow.Dim
                else -> Glow.On
            }
            return PhoneOverlay(bubble = access.bubble && !iCodeOnScreen, glow = glow, working = turn != null, asking = asking, step = turn?.step)
        }
    }
}
