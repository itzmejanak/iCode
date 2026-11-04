package dev.icode.mobile.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * The sheets opened from anywhere — a confirmation, a question, work in hand — that iCode once drew
 * as dialogs (owner's request, 2026-11-04: one kind of modal, the sheet, and no dialogs). Opened by
 * [IcodeModalSheet] wherever a screen is in its layout, they are drawn by the [IcodeSheetHost] over
 * the whole app, newest on top.
 */
@Stable
class IcodeSheets internal constructor() {
    internal val open = mutableStateListOf<OpenSheet>()
}

/** One open sheet: what it shows, with the composition locals of where it was opened. */
internal class OpenSheet(locals: CompositionLocalContext, onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    var locals by mutableStateOf(locals)
    var onClose by mutableStateOf(onClose)
    var content by mutableStateOf(content)
}

internal val LocalIcodeSheets = staticCompositionLocalOf<IcodeSheets?> { null }

/** [content], with the sheets opened over it ([IcodeModalSheet]) drawn above all of it, which blurs under them. */
@Composable
fun IcodeSheetHost(content: @Composable () -> Unit) {
    val sheets = remember { IcodeSheets() }
    CompositionLocalProvider(LocalIcodeSheets provides sheets) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().behindSheet(sheets.open.isNotEmpty())) { content() }
            sheets.open.forEach { sheet ->
                key(sheet) {
                    CompositionLocalProvider(sheet.locals) {
                        IcodeSheetOverlay(onClose = sheet.onClose) { sheet.content(this) }
                    }
                }
            }
        }
    }
}

/**
 * A sheet opened from wherever this is called, while it is called: drawn by the [IcodeSheetHost] over
 * the whole app with the caller's composition locals, the screen dimmed and blurred under it. A tap
 * outside it, Back, × and a swipe down all [onClose]; [content] is the sheet itself, at the bottom
 * ([IcodeBottomSheet] aligned to [androidx.compose.ui.Alignment.BottomCenter]).
 */
@Composable
fun IcodeModalSheet(onClose: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    val sheets = checkNotNull(LocalIcodeSheets.current) { "IcodeModalSheet needs an IcodeSheetHost above it" }
    val locals = currentCompositionLocalContext
    val sheet = remember { OpenSheet(locals, onClose, content) }
    SideEffect {
        sheet.locals = locals
        sheet.onClose = onClose
        sheet.content = content
    }
    DisposableEffect(sheet) {
        sheets.open += sheet
        onDispose { sheets.open -= sheet }
    }
}
