package dev.icode.mobile.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * One row of a popup list: [label], a line of [detail] under it and one of [warning] in amber, [icon]
 * before it in [tint], [trailing] after it. A tap does [onClick]; a row without one is only read.
 */
data class IcodePopupItem(
    val label: String,
    val detail: String? = null,
    val warning: String? = null,
    val icon: ImageVector? = null,
    val tint: Color? = null,
    val trailing: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * [items] in a popup above what it is placed in, at its end, on the surface every popup has: the "/"
 * commands as they are typed over the message box, an agent's to-do list over the jump-to-latest
 * button. Long, it scrolls. Not [focusable], it leaves the keyboard and typing where they are; a
 * focusable one closes with a tap outside, or Back.
 */
@Composable
fun IcodePopupList(expanded: Boolean, onDismiss: () -> Unit, items: List<IcodePopupItem>, title: String? = null, focusable: Boolean = true) {
    if (!expanded || items.isEmpty()) return
    Popup(popupPositionProvider = AboveAtEnd, onDismissRequest = onDismiss, properties = PopupProperties(focusable = focusable)) {
        IcodePopupSurface(Modifier.widthIn(max = IcodeSizes.PopupListWidth)) {
            Column(Modifier.heightIn(max = IcodeSizes.PopupListHeight).verticalScroll(rememberScrollState()).padding(vertical = IcodeSpacing.Space1)) {
                title?.let { SectionLabel(it, Modifier.padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1)) }
                items.forEach { item -> PopupRow(item) }
            }
        }
    }
}

@Composable
private fun PopupRow(item: IcodePopupItem) {
    Row(
        Modifier.fillMaxWidth()
            .then(item.onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        item.icon?.let { Icon(it, contentDescription = null, tint = item.tint ?: IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm)) }
        Column(Modifier.weight(1f)) {
            Text(item.label, style = IcodeType.Body.copy(fontWeight = FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
            item.detail?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 2, overflow = TextOverflow.Ellipsis) }
            item.warning?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Amber)) }
        }
        item.trailing?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.TextMuted), maxLines = 1) }
    }
}

/** Over the anchor, its end against the anchor's end; kept inside the window. */
private object AboveAtEnd : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
        val y = anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceAtLeast(0),
        )
    }
}
