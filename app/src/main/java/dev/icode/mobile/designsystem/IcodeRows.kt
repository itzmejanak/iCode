package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Row primitive for lists, pickers, trees and cards.
 *
 * Use the slot form ([content], [leading], [trailing]) for rich rows, or pass
 * [title]/[subtitle] for simple ones. [card] wraps the row in an [IcodeCard],
 * [indent] shifts tree rows, and [selected] renders the shared selection state
 * so no screen invents its own highlight.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IcodeListRow(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    subtitleStyle: TextStyle = IcodeType.MonoSmall,
    titleStyle: TextStyle = IcodeType.Body,
    titleColor: Color = IcodeColors.TextPrimary,
    selected: Boolean = false,
    selectionBackground: Color = IcodeColors.SurfaceElevated,
    rowShape: Shape = IcodeShapes.Small,
    card: Boolean = false,
    cardPadding: PaddingValues = PaddingValues(IcodeSpacing.CardPadding),
    indent: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    /** Secondary, usually destructive action for the row. */
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    /**
     * A row in a dense list whose own buttons are its height (Source Control's tree): no padding
     * above and below, so rows sit as tight as their touch targets allow.
     */
    compact: Boolean = false,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    // What the selection draws, a screen reader hears too.
    val announced = modifier.then(if (selected) Modifier.semantics { this.selected = true } else Modifier)
    val rowContent: @Composable RowScope.() -> Unit = {
        leading?.invoke()
        if (content != null) {
            content()
        } else {
            Column(Modifier.weight(1f)) {
                Text(
                    title.orEmpty(),
                    style = titleStyle.copy(color = titleColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) MonoLine(subtitle, style = subtitleStyle)
            }
        }
        trailing?.invoke(this)
    }

    if (card) {
        IcodeCard(
            modifier = if (onLongClick == null) {
                announced
            } else {
                announced.combinedClickable(
                    role = Role.Button,
                    onClick = onClick ?: {},
                    onLongClick = onLongClick,
                )
            },
            borderColor = if (selected) IcodeColors.Accent else IcodeColors.Outline,
            padding = cardPadding,
            // The card would otherwise install a second click handler that
            // swallows the long press.
            onClick = if (onLongClick == null) onClick else null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                content = rowContent,
            )
        }
    } else {
        Row(
            modifier = announced
                .fillMaxWidth()
                .padding(start = indent)
                .clip(rowShape)
                .background(if (selected) selectionBackground else Color.Transparent)
                .then(
                    when {
                        onLongClick != null -> Modifier.combinedClickable(
                            role = Role.Button,
                            onClick = onClick ?: {},
                            onLongClick = onLongClick,
                        )
                        onClick != null -> Modifier.clickable(role = Role.Button, onClick = onClick)
                        else -> Modifier
                    },
                )
                .padding(horizontal = IcodeSpacing.Space2, vertical = if (compact) 0.dp else IcodeSpacing.Space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
            content = rowContent,
        )
    }
}

/** Settings row: leading icon, title, optional subtitle and a trailing control. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    IcodeCard(modifier = modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3)) {
            if (leadingIcon != null) {
                Box(
                    modifier = Modifier
                        .size(IcodeSizes.BadgeSm)
                        .background(IcodeColors.SurfaceElevated, IcodeShapes.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(leadingIcon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(20.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
                if (subtitle != null) Text(subtitle, style = IcodeType.Caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
    }
}

/** Label/value pair used by detail, runtime, build and recovery sections; [valueIcon] leads the value, in its colour. */
@Composable
fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = IcodeColors.TextPrimary,
    valueStyle: TextStyle = IcodeType.Mono,
    valueIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = IcodeSpacing.Space1),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = IcodeType.Caption)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
            valueIcon?.let { Icon(it, contentDescription = null, tint = valueColor, modifier = Modifier.size(IcodeSizes.IconSm)) }
            MonoLine(value, color = valueColor, style = valueStyle)
        }
    }
}

/** Square badge holding an icon, used as a row or header leading element. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = IcodeSizes.BadgeMd,
    background: Color = IcodeColors.SurfaceElevated,
    iconSize: Dp = IcodeSizes.IconMd,
    shape: Shape = IcodeShapes.Small,
) {
    Box(
        modifier = modifier.size(size).background(background, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Round badge holding a short text mark (initials, numbering, logomark). */
@Composable
fun IconBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = IcodeSizes.BadgeMd,
    background: Color = IcodeColors.SurfaceElevated,
    style: TextStyle = IcodeType.Label,
) {
    Box(
        modifier = modifier.size(size).background(background, IcodeShapes.Pill),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = style.copy(color = tint))
    }
}

/**
 * One choice in a sheet — a branch, a repository: its name in mono, the chosen one in the accent
 * with a tick. [otherwise] is what an unchosen one carries at its end (the branch's Delete), if anything.
 */
@Composable
fun IcodeChoiceRow(
    title: String,
    chosen: Boolean,
    onChoose: () -> Unit,
    otherwise: (@Composable RowScope.() -> Unit)? = null,
) {
    IcodeListRow(
        title = title,
        selected = chosen,
        onClick = onChoose,
        titleStyle = IcodeType.Mono,
        titleColor = if (chosen) IcodeColors.Accent else IcodeColors.TextPrimary,
        trailing = {
            if (chosen) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
            } else {
                otherwise?.invoke(this)
            }
        },
    )
}
