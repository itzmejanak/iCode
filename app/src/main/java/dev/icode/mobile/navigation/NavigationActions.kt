package dev.icode.mobile.navigation

/**
 * Navigation actions every screen receives.
 *
 * Screens depend on this contract instead of the composition root, so route
 * wiring can be replaced by real navigation without touching screen code.
 */
data class NavigationActions(
    val navigate: (Route) -> Unit,
    /** Switch a sibling tool without stacking duplicate search pages. */
    val replace: (Route) -> Unit,
    /** Selects a view of the one tools panel; opening another tab never pushes a route. */
    val openPanel: (ToolPanelTab) -> Unit,
    val panelTab: ToolPanelTab,
    val back: () -> Unit,
    /**
     * Whether anything pushed this screen.
     *
     * A screen reached during onboarding has nothing behind it and must route
     * onward itself; the same screen opened from the Terminal must return to
     * it. This is the only thing that tells them apart.
     */
    val canGoBack: Boolean,
    /**
     * Drawn from capture fixtures (debug builds): a screen that moves on by
     * itself after a delay stays put, so the screenshot is stable.
     */
    val captureMode: Boolean,
)
