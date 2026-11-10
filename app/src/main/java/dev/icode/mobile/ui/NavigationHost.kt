package dev.icode.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeSheetOverlay
import dev.icode.mobile.designsystem.behindSheet
import dev.icode.mobile.navigation.Navigation
import dev.icode.mobile.navigation.Route

/**
 * Draws the top of the history on screen. Each entry keeps what its screen holds — a typed commit
 * message, a search, a scroll position — while another page or another tool is shown, and forgets it
 * once no history holds it: a page closed, a workspace's tools started anew (docs/dock-navigation-plan.md
 * D5). A sheet route is drawn over the live screen below it, blurred and dimmed: a tap outside it, or
 * Back, closes it, and the screen below is as it was.
 */
@Composable
internal fun NavigationHost(navigation: NavigationState, screen: @Composable (Route) -> Unit) {
    val kept = rememberSaveableStateHolder()
    val model = navigation.model
    val shown = model.shown
    val top = shown.last()
    // Under a sheet, the entry below it; a sheet with nothing below is drawn over the background alone.
    val base = if (top.route.sheet) shown.getOrNull(shown.lastIndex - 1) else top
    ForgetClosed(model) { kept.removeState(it) }
    Box(Modifier.fillMaxSize()) {
        base?.let { entry ->
            key(entry.key) {
                Box(Modifier.fillMaxSize().behindSheet(top.route.sheet)) {
                    kept.SaveableStateProvider(entry.key) { screen(entry.route) }
                }
            }
        }
        if (top.route.sheet) {
            IcodeSheetOverlay(onClose = navigation.actions.back) {
                key(top.key) { kept.SaveableStateProvider(top.key) { screen(top.route) } }
            }
        }
    }
}

/** Forgets, through [forget], what an entry kept once no history holds it, shown or not. */
@Composable
private fun ForgetClosed(model: Navigation, forget: (String) -> Unit) {
    val known = remember { mutableSetOf<String>() }
    val live = model.keys.toSet()
    LaunchedEffect(live) {
        (known - live).forEach(forget)
        known.retainAll(live)
        known += live
    }
}
