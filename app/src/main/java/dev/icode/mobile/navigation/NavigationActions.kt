package dev.icode.mobile.navigation

/**
 * Navigation actions every screen receives.
 *
 * Screens depend on this contract instead of the composition root. They name routes; what a route
 * does — switch to a tool, push a page, go back to one already open — is decided in one place,
 * [Navigation] (docs/dock-navigation-plan.md D3).
 */
data class NavigationActions(
    /** A tool's main screen switches to that tool, as it was left; any other route opens over this screen. */
    val navigate: (Route) -> Unit,
    /** This page closes, then [Route] is gone to: a sibling settings page, a palette's command. */
    val replace: (Route) -> Unit,
    /** Terminal, on that tab. */
    val openPanel: (ToolPanelTab) -> Unit,
    val panelTab: ToolPanelTab,
    /** Back (D4): this tool's page, then the tool used before, then the Launcher. */
    val back: () -> Unit,
    /**
     * Back has somewhere to go inside iCode.
     *
     * A screen reached during onboarding has nothing behind it and must route onward itself; the same
     * screen opened from a tool must return to it. This is the only thing that tells them apart.
     */
    val canGoBack: Boolean,
    /**
     * Drawn from capture fixtures (debug builds): a screen that moves on by
     * itself after a delay stays put, so the screenshot is stable.
     */
    val captureMode: Boolean,
)
