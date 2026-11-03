package dev.icode.mobile.domain

/** Phone control (docs/phone-agent-plan.md §5.9, §5.11): iCode's accessibility service, Shizuku, the bubble. */
sealed interface PhoneIntent : ICodeIntent {
    /** What iCode may do on the phone, read again: Settings' page opened, or iCode came back to the front. */
    data object Refresh : PhoneIntent

    /** iCode's service turned on through Shizuku, restricted settings allowed first: one tap for Android's walk. */
    data object TurnOnWithShizuku : PhoneIntent

    /** Phone control off: the service turns itself off; Android's page turns it on again. */
    data object TurnOff : PhoneIntent

    /** Shizuku asks the user to allow iCode. */
    data object RequestShizuku : PhoneIntent

    /** The bubble out again, or put away with the glow (the owner's word, §5.7). */
    data class ShowBubble(val shown: Boolean) : PhoneIntent
}
