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

/**
 * A sheet over the screen it opens from: the screen dimmed — blur it where it is drawn — and a tap
 * outside the sheet, or Back, closes it. Sheet routes are drawn this way, and a screen's own sheets
 * that work on what the screen holds (a message's draft) are too.
 */
@Composable
fun IcodeSheetOverlay(onClose: () -> Unit, sheet: @Composable BoxScope.() -> Unit) {
    // Composed after the screen below, so Back closes the sheet before any handler of that screen;
    // and before the sheet, so one of the sheet's own — a page within it going back — comes first.
    BackHandler(onBack = onClose)
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
