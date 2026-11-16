package dev.icode.mobile.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardTab
import androidx.compose.material.icons.automirrored.outlined.LastPage
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.FirstPage
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.domain.EditorKey
import dev.icode.mobile.domain.TerminalExtraKey

/**
 * A key of the editor's keys row (docs/design-and-navigation.md): an editing key as its icon, spoken by its
 * name; a symbol as what it types.
 */
@Composable
internal fun EditorKeyChip(key: EditorKey, modifier: Modifier = Modifier, active: Boolean = false, compact: Boolean = false, onClick: (() -> Unit)?) {
    val face = faceOf(key)
    LeftToRight {
        if (face == null) KeyChip(checkNotNull(key.text), modifier, active, compact, onClick = onClick)
        else KeyChip(face.first, face.second, modifier, active, compact, onClick)
    }
}

/** A key of the terminal's extra keys row: ESC, CTRL and ALT by name; Tab and the arrows as the editor's. */
@Composable
internal fun TerminalKeyChip(key: TerminalExtraKey, modifier: Modifier = Modifier, active: Boolean = false, compact: Boolean = false, onClick: (() -> Unit)?) {
    val editing = when (key) {
        TerminalExtraKey.Escape -> return KeyChip("ESC", modifier, active, compact, "Escape", onClick)
        TerminalExtraKey.Ctrl -> return KeyChip("CTRL", modifier, active, compact, "Control", onClick)
        TerminalExtraKey.Alt -> return KeyChip("ALT", modifier, active, compact, "Alt", onClick)
        TerminalExtraKey.Tab -> EditorKey.Tab
        TerminalExtraKey.Left -> EditorKey.Left
        TerminalExtraKey.Up -> EditorKey.Up
        TerminalExtraKey.Down -> EditorKey.Down
        TerminalExtraKey.Right -> EditorKey.Right
    }
    EditorKeyChip(editing, modifier, active, compact, onClick)
}

/**
 * Code and the terminal are drawn left to right in every locale, so their keys are too: an arrow
 * points the way the caret moves, and the keys keep a keyboard's order.
 */
@Composable
internal fun LeftToRight(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)

/** An editing key's icon and spoken name; null for a symbol, drawn as what it types. */
private fun faceOf(key: EditorKey): Pair<ImageVector, String>? = when (key) {
    EditorKey.Tab -> Icons.AutoMirrored.Outlined.KeyboardTab to "Tab"
    EditorKey.Outdent -> Icons.AutoMirrored.Outlined.FormatIndentDecrease to "Outdent"
    EditorKey.Left -> Icons.AutoMirrored.Outlined.KeyboardArrowLeft to "Left"
    EditorKey.Up -> Icons.Outlined.KeyboardArrowUp to "Up"
    EditorKey.Down -> Icons.Outlined.KeyboardArrowDown to "Down"
    EditorKey.Right -> Icons.AutoMirrored.Outlined.KeyboardArrowRight to "Right"
    EditorKey.Home -> Icons.Outlined.FirstPage to "Home"
    EditorKey.End -> Icons.AutoMirrored.Outlined.LastPage to "End"
    EditorKey.Undo -> Icons.AutoMirrored.Outlined.Undo to "Undo"
    EditorKey.Redo -> Icons.AutoMirrored.Outlined.Redo to "Redo"
    else -> null
}
