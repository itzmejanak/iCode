package dev.icode.mobile.navigation

/**
 * Navigation actions every demo screen receives.
 *
 * Screens depend on this contract instead of the composition root, so route
 * wiring can be replaced by real navigation without touching screen code.
 */
data class DemoActions(
    val navigate: (DemoScreen) -> Unit,
    val back: () -> Unit,
    val staticDemo: Boolean,
)
