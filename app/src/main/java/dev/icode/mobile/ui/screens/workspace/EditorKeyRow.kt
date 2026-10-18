package dev.icode.mobile.ui.screens.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.KeyChip
import dev.icode.mobile.domain.EditorKey

/**
 * The editor's keys row (docs/editor-plan.md §4): above the keyboard while it is open, the keys a
 * phone keyboard makes slow — Tab, the arrows, Undo, Redo, then the symbols — scrolling sideways.
 * The terminal's row is the same `KeyChip`s.
 */
@Composable
internal fun EditorKeyRow(keys: List<EditorKey>, onKey: (EditorKey) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(IcodeColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = IcodeSpacing.Space2, vertical = IcodeSpacing.Space2),
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1),
    ) {
        keys.forEach { key ->
            KeyChip(
                text = key.label,
                modifier = Modifier.heightIn(min = IcodeSizes.TouchTarget).widthIn(min = IcodeSizes.TouchTarget),
                compact = true,
                onClick = { onKey(key) },
            )
        }
    }
}
