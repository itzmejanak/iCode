package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/*
 * The option panel kit: a few choices a screen makes about how it shows its own list — a view, a
 * sort, what to include — drawn compactly as tiles, an icon over its label, the chosen one an accent
 * pill as a [SegmentedControl]'s is. One tap changes the choice and the panel stays, so several can
 * be made, each seen as it is.
 *
 * What it is for, and what it is not: an action is an [IcodeMenuAction], in an [IcodeActionSheet]; one
 * value out of many with names too long for a tile is an [IcodeSelect]; two to four words for one
 * value is a [SegmentedControl].
 *
 * The layers, so a screen takes the one it needs, and a change of look is made in one place:
 *  - [IcodeOption] and [IcodeOptionGroup]: what is offered, as plain data a screen's rules can build
 *    and test without any UI;
 *  - [IcodeOptionTile]: one tile;
 *  - [IcodeOptionGroups]: the titled rows of tiles, as content for a popup or a sheet;
 *  - [IcodeOptionPanel]: the groups in a popup on the popup surface ([IcodePopupSurface]), under
 *    what opened it and stuck to the screen's end edge;
 *  - [IcodeOptionPanelButton]: the icon button that opens that popup.
 */

/**
 * One choice: a tile of [icon] over [label], a pill while [chosen]. A tap does [onClick] — it makes
 * the choice, or flips it when it is a switch (Hidden files). [description] is what a screen reader
 * says where [label] is short for it ("Desc" for Descending).
 */
data class IcodeOption(
    val label: String,
    val icon: ImageVector,
    val chosen: Boolean,
    val onClick: () -> Unit,
    val description: String = label,
    val enabled: Boolean = true,
)

/**
 * A titled row of [options]. [aside] stands apart at the row's end: a switch beside the choices it
 * has nothing to do with, as Hidden files beside the views.
 */
data class IcodeOptionGroup(
    val title: String,
    val options: List<IcodeOption>,
    val aside: List<IcodeOption> = emptyList(),
)

/** How many tiles wide the widest of [groups] is: the panel is as wide as that many. */
internal fun List<IcodeOptionGroup>.columns(): Int = maxOfOrNull { it.options.size + it.aside.size } ?: 0

/**
 * One tile of [option]: its icon over its label. Chosen, it is an accent pill with the colours made
 * for the accent, [SegmentedControl]'s own; a [IcodeSpacing.Micro] of the tile's width is left round
 * the pill, so two chosen side by side stay two.
 */
@Composable
fun IcodeOptionTile(option: IcodeOption, modifier: Modifier = Modifier) {
    val icon = when {
        !option.enabled -> IcodeColors.TextMuted
        option.chosen -> IcodeColors.OnAccent
        else -> IcodeColors.TextPrimary
    }
    val label = when {
        !option.enabled -> IcodeColors.TextMuted
        option.chosen -> IcodeColors.OnAccent
        else -> IcodeColors.TextSecondary
    }
    Box(modifier.width(IcodeSizes.OptionTile).padding(horizontal = IcodeSpacing.Micro)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(IcodeShapes.Chip)
                .background(if (option.chosen) IcodeColors.Accent else Color.Transparent)
                .clickable(enabled = option.enabled, role = Role.Button, onClick = option.onClick)
                .semantics { contentDescription = option.description; selected = option.chosen }
                .padding(vertical = IcodeSpacing.Space1),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
        ) {
            Icon(option.icon, contentDescription = null, tint = icon, modifier = Modifier.size(IcodeSizes.IconLg))
            Text(
                option.label,
                style = IcodeType.Caption.copy(color = label),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * [groups] as content: each a [SectionLabel] over its row of tiles, the groups [IcodeSpacing.Space2]
 * apart. It fills the width it is given, so a sheet can hold it as a popup does ([IcodeOptionPanel]).
 */
@Composable
fun IcodeOptionGroups(groups: List<IcodeOptionGroup>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        groups.forEach { group ->
            Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro)) {
                // Where the first tile's pill begins, not its tile.
                SectionLabel(group.title, Modifier.padding(start = IcodeSpacing.Micro))
                Row(Modifier.fillMaxWidth()) {
                    group.options.forEach { IcodeOptionTile(it) }
                    Spacer(Modifier.weight(1f))
                    group.aside.forEach { IcodeOptionTile(it) }
                }
            }
        }
    }
}

/**
 * [groups] in a popup below what opened it, stuck to the screen's end edge — wherever the button that
 * opened it is — on the surface every popup has. A tap outside, or Back, closes it.
 */
@Composable
fun IcodeOptionPanel(expanded: Boolean, onDismiss: () -> Unit, groups: List<IcodeOptionGroup>) {
    if (!expanded) return
    val shown = remember { MutableTransitionState(false).apply { targetState = true } }
    Popup(
        popupPositionProvider = BelowAtEnd,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        // It grows from the corner it is stuck in.
        AnimatedVisibility(
            shown,
            enter = fadeIn() + scaleIn(initialScale = 0.9f, transformOrigin = TransformOrigin(1f, 0f)),
        ) {
            IcodePopupSurface {
                IcodeOptionGroups(
                    groups,
                    Modifier.padding(horizontal = IcodeSpacing.Space2, vertical = IcodeSpacing.Space2)
                        .width(IcodeSizes.OptionTile * groups.columns()),
                )
            }
        }
    }
}

/** Under the anchor, against the window's end edge — not the anchor's. */
private object BelowAtEnd : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) windowSize.width - popupContentSize.width else 0
        return IntOffset(x.coerceAtLeast(0), anchorBounds.bottom)
    }
}

/** An icon button in a top bar and the [IcodeOptionPanel] of [groups] it opens. */
@Composable
fun IcodeOptionPanelButton(
    icon: ImageVector,
    contentDescription: String,
    groups: List<IcodeOptionGroup>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(icon, contentDescription = contentDescription, tint = IcodeColors.TextPrimary)
        }
        IcodeOptionPanel(expanded, { expanded = false }, groups)
    }
}
