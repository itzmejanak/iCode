package dev.icode.mobile.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur

/**
 * The screen's own content while a sheet is [shown] over it: blurred, as under every sheet. Put it
 * on the content the [IcodeSheetOverlay] is a sibling of, never on a parent of the sheet.
 */
fun Modifier.behindSheet(shown: Boolean): Modifier = if (shown) blur(IcodeSizes.SheetBackdropBlur) else this

/**
 * A sheet over the screen it opens from: the screen dimmed — blur it with [behindSheet] — and a tap
 * outside the sheet, or Back, closes it; the floating stack steps aside while it is open. Sheet routes
 * are drawn this way, and a screen's own sheets that work on what the screen holds (a message's draft)
 * are too.
 */
@Composable
fun IcodeSheetOverlay(onClose: () -> Unit, sheet: @Composable BoxScope.() -> Unit) {
    // Composed after the screen below, so Back closes the sheet before any handler of that screen;
    // and before the sheet, so one of the sheet's own — a page within it going back — comes first.
    BackHandler(onBack = onClose)
    SheetOpen()
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(IcodeColors.SheetScrim).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        )
        sheet()
    }
}
