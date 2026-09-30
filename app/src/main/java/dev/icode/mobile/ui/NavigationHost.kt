package dev.icode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.navigation.Route

/**
 * Draws the top of the history. Each entry keeps what its screen holds — a typed commit message,
 * a search — while another is opened over it, and forgets it once it leaves the history. A sheet
 * route is drawn over the live screen below it, blurred and dimmed: a tap outside it, or Back,
 * closes it, and the screen below is as it was.
 */
@Composable
internal fun NavigationHost(navigation: NavigationState, screen: @Composable (Route) -> Unit) {
    val kept = rememberSaveableStateHolder()
    val keys = navigation.entryKeys
    val top = navigation.current
    // Under a sheet, the entry below it; a sheet with nothing below is drawn over the background alone.
    val base = keys.getOrNull(if (top.sheet) keys.lastIndex - 1 else keys.lastIndex)
    Box(Modifier.fillMaxSize()) {
        base?.let {
            key(it) {
                Box(Modifier.fillMaxSize().then(if (top.sheet) Modifier.blur(IcodeSizes.SheetBackdropBlur) else Modifier)) {
                    Entry(kept, it, navigation) { screen(routeOf(it)) }
                }
            }
        }
        if (top.sheet) {
            Box(
                Modifier.fillMaxSize().background(IcodeColors.SheetScrim).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = navigation.actions.back,
                ),
            )
            key(keys.last()) { Entry(kept, keys.last(), navigation) { screen(top) } }
            // Composed last, so Back closes the sheet before any handler of the screen below.
            BackHandler { navigation.actions.back() }
        }
    }
}

/** One entry's screen, with what it keeps; forgotten when the entry has left the history. */
@Composable
private fun Entry(kept: SaveableStateHolder, entry: String, navigation: NavigationState, content: @Composable () -> Unit) {
    kept.SaveableStateProvider(entry, content)
    DisposableEffect(entry) {
        onDispose { if (entry !in navigation.entryKeys) kept.removeState(entry) }
    }
}

private fun routeOf(entry: String): Route = Route.valueOf(entry.substringAfter(':'))
