package dev.icode.mobile.domain.phone

/**
 * Shizuku as iCode finds it (docs/phone-agent-plan.md §5.11), read live: each state names the one
 * step it needs, for its page in Settings.
 */
enum class ShizukuState(val label: String, val step: String) {
    NotInstalled("Not installed", "Install it from Google Play or GitHub"),
    NotRunning("Not running", "Start it with Wireless debugging, after each restart"),
    TooOld("Too old", "iCode needs Shizuku 11 or later"),
    NotAllowed("Not allowed", "Allow iCode when Shizuku asks"),
    Ready("Ready", "iCode can use it"),
}

/** What iCode may do on the phone right now: read from the system each time it may have changed, never stored. */
data class PhoneAccess(
    /** iCode's accessibility service is on and connected: phone control is on. */
    val service: Boolean = false,
    val shizuku: ShizukuState = ShizukuState.NotInstalled,
    /** The bubble is out over other apps: phone control is on and the user has not put it away. */
    val bubble: Boolean = false,
    /**
     * Android's own accessibility button still names iCode — an earlier build asked for it — and shows
     * a second icon whose tap turns phone control off. iCode's bubble replaces it; the user removes it.
     */
    val systemButton: Boolean = false,
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuState.Ready
}

/**
 * What the agent is never let do on the phone (docs/phone-agent-plan.md §5.5): act on iCode's own
 * windows — its approvals are the user's alone — on Android's permission dialogs, the installer,
 * or the accessibility settings that say which services read the screen. It hands those over.
 */
object PhoneRefusals {
    private val PERMISSIONS = setOf("com.google.android.permissioncontroller", "com.android.permissioncontroller")
    private val INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
    private const val SETTINGS = "com.android.settings"
    private const val ACCESSIBILITY = "accessibility"

    /** Why acting on [element] of [screen] is refused; null when it is not. [own] is iCode's package. */
    fun refusal(screen: PhoneScreen, element: PhoneElement?, own: String): String? {
        val app = element?.app ?: screen.app
        return when {
            app == own -> "That is iCode itself: what it asks is the user's to answer."
            app in PERMISSIONS -> "That is Android's permission dialog: only the user answers it. Hand it over with phone_hand_over."
            app in INSTALLERS -> "Installing and removing apps is the user's to do. Hand it over with phone_hand_over."
            app == SETTINGS && inAccessibility(screen, element) ->
                "Accessibility settings are the user's alone: they say which apps read the screen."
            else -> null
        }
    }

    /**
     * Settings' Accessibility page, or its row: the element itself, or the page's title — a heading that
     * does nothing, among its first lines (the main page's row of that name is one that taps).
     */
    private fun inAccessibility(screen: PhoneScreen, element: PhoneElement?): Boolean =
        element?.label?.contains(ACCESSIBILITY, ignoreCase = true) == true ||
            screen.title?.contains(ACCESSIBILITY, ignoreCase = true) == true ||
            screen.elements.take(TITLE_LINES).any { it.abilities.isEmpty() && it.label.equals(ACCESSIBILITY, ignoreCase = true) }

    /** A page's title is among its first lines, after Back. */
    private const val TITLE_LINES = 4
}
