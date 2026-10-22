package dev.icode.mobile.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

// A tree of ticks (Runtime Tools), as Android Studio's SDK Manager draws one: each row its expand
// arrow, its tick and what it is; the rows beneath it a step in, on a rail down from its arrow.

/**
 * One row. It starts with an [IcodeSizes.Disclosure]-wide slot: with rows beneath it ([open] not null),
 * the arrow iCode's trees use — down while open — which takes the tap the row's height down and is said
 * "Expand [name]" or "Collapse [name]"; otherwise nothing, so every row's tick lines up. The rest of the
 * row is its tick: it takes the tap, and a screen reader hears it as one checkbox.
 */
@Composable
fun IcodeTickRow(
    state: ToggleableState,
    onTick: () -> Unit,
    name: String,
    open: Boolean?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(IcodeSizes.Disclosure)
                .fillMaxHeight()
                .then(
                    if (open == null) Modifier
                    else Modifier.clip(IcodeShapes.Small).clickable(role = Role.Button, onClick = onOpen),
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (open != null) {
                // Level with the tick: the row's top padding, then half of what the tick is taller than the arrow.
                Icon(
                    if (open) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (open) "Collapse $name" else "Expand $name",
                    tint = IcodeColors.TextSecondary,
                    modifier = Modifier.padding(top = IcodeSpacing.Space2 + IcodeSpacing.Space1).size(IcodeSizes.IconSm),
                )
            }
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(IcodeShapes.Small)
                .triStateToggleable(state = state, role = Role.Checkbox, onClick = onTick)
                .padding(vertical = IcodeSpacing.Space2),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
        ) {
            IcodeTriStateCheckbox(state, onClick = null)
            Column(Modifier.weight(1f).padding(top = IcodeSpacing.Micro), content = content)
            trailing?.let { Box(Modifier.padding(top = IcodeSpacing.Micro)) { it() } }
        }
    }
}

/**
 * The rows beneath an open row: an [IcodeSpacing.IndentStep] in, a rail down from the middle of its
 * arrow's slot, so every level's rail meets its arrow.
 */
@Composable
fun IcodeTickBranch(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val rail = IcodeColors.Outline
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val x = (IcodeSizes.Disclosure / 2).toPx()
                drawLine(rail, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
            .padding(start = IcodeSpacing.IndentStep),
        content = content,
    )
}
