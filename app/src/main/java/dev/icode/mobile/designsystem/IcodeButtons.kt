package dev.icode.mobile.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Filled primary action. Labels are uppercase by design; [icon] leads the label. [compact]: as tall
 * as a bar's choice, among other compact actions; Material keeps its touch target whole.
 *
 * [busy] (docs/design-and-navigation.md): what it started is running. It takes no tap from then on, keeps
 * its colour a little dimmed — working, not unavailable — and its label, and shows the spinner in
 * place of its icon once the wait is worth seeing. Every button in iCode says it is working this way.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(buttonHeight(compact)).busySemantics(busy),
        shape = IcodeShapes.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = IcodeColors.Accent,
            contentColor = IcodeColors.OnAccent,
            disabledContainerColor = if (busy) IcodeColors.Accent.copy(alpha = BUSY_ALPHA) else IcodeColors.SurfaceElevated,
            disabledContentColor = if (busy) IcodeColors.OnAccent else IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) IcodeSpacing.Space3 else IcodeSpacing.Space4),
    ) {
        Leading(busy, icon)
        Text(text.uppercase(), style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

/** A button's leading place: the spinner while its work is seen running, else its icon if it has one. */
@Composable
private fun Leading(busy: Boolean, icon: ImageVector?) {
    val spinning = rememberShownWhileBusy(busy)
    if (!spinning && icon == null) return
    if (spinning) IcodeSpinner(color = LocalContentColor.current)
    else icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(IcodeSizes.IconSm)) }
    Spacer(Modifier.width(IcodeSpacing.Space2))
}


/**
 * A toolbar's icon action — Save, Refresh, + — with [busy] as [PrimaryButton]'s: no tap from then on,
 * and the spinner in the icon's place once the wait is worth seeing.
 */
@Composable
fun IcodeIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    tint: Color = IcodeColors.TextPrimary,
    /** The icon's size; Material's 24 dp when null. */
    iconSize: Dp? = null,
) {
    IconButton(onClick = onClick, enabled = enabled && !busy, modifier = modifier.busySemantics(busy)) {
        if (rememberShownWhileBusy(busy)) {
            IcodeSpinner(color = tint)
        } else {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = if (enabled || busy) tint else IcodeColors.TextMuted,
                modifier = if (iconSize == null) Modifier else Modifier.size(iconSize),
            )
        }
    }
}

/** A busy filled button's container: its own colour, dimmed. */
private const val BUSY_ALPHA = 0.6f

/** A quiet action inside content — Show Details: the label, after its [icon] if any, in the accent, with a full touch target. [busy] as [PrimaryButton]'s. */
@Composable
fun IcodeTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    busy: Boolean = false,
) {
    val color = if (enabled || busy) IcodeColors.Accent else IcodeColors.TextMuted
    TextButton(
        onClick = onClick,
        modifier = modifier.busySemantics(busy),
        enabled = enabled && !busy,
        contentPadding = PaddingValues(horizontal = IcodeSpacing.Space2),
    ) {
        CompositionLocalProvider(LocalContentColor provides color) { Leading(busy, icon) }
        Text(text, style = IcodeType.Label.copy(color = color))
    }
}

/** Outlined secondary action. Labels keep the design casing. [compact] and [busy] as [PrimaryButton]'s. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = IcodeColors.TextPrimary,
    compact: Boolean = false,
    busy: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(buttonHeight(compact)).busySemantics(busy),
        shape = IcodeShapes.Small,
        border = BorderStroke(1.dp, IcodeColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IcodeColors.Surface,
            contentColor = color,
            disabledContentColor = if (busy) color else IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) IcodeSpacing.Space3 else IcodeSpacing.Space4),
    ) {
        Leading(busy, icon = null)
        Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

private fun buttonHeight(compact: Boolean) = if (compact) IcodeSizes.BarChoice else IcodeSizes.TouchTarget

/** Destructive action, filled for confirmation and outlined elsewhere. [enabled] and [busy] as [PrimaryButton]'s. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val active = enabled && !busy
    if (filled) {
        Button(
            onClick = onClick,
            enabled = active,
            modifier = modifier.height(IcodeSizes.TouchTarget).busySemantics(busy),
            shape = IcodeShapes.Small,
            colors = ButtonDefaults.buttonColors(
                containerColor = IcodeColors.Error,
                contentColor = IcodeColors.OnAccent,
                disabledContainerColor = if (busy) IcodeColors.Error.copy(alpha = BUSY_ALPHA) else IcodeColors.SurfaceElevated,
                disabledContentColor = if (busy) IcodeColors.OnAccent else IcodeColors.TextMuted,
            ),
        ) {
            Leading(busy, icon = null)
            Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = active,
            modifier = modifier.height(IcodeSizes.TouchTarget).busySemantics(busy),
            shape = IcodeShapes.Small,
            border = BorderStroke(1.dp, if (enabled || busy) IcodeColors.Error else IcodeColors.Outline),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = IcodeColors.Error,
                disabledContentColor = if (busy) IcodeColors.Error else IcodeColors.TextMuted,
            ),
        ) {
            Leading(busy, icon = null)
            Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
        }
    }
}

/** A compact, full-touch-target link between related workspace tools. */
@Composable
fun IcodeNavigationLink(
    label: String,
    detail: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = IcodeSizes.TouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space2, vertical = IcodeSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        Icon(icon, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
        Column(Modifier.weight(1f)) {
            Text(label, style = IcodeType.Caption.copy(color = IcodeColors.Accent), maxLines = 1)
            Text(detail, style = IcodeType.MonoSmall.copy(color = IcodeColors.TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
    }
}

/**
 * Compact technical chip: keyboard keys, shortcuts and small metadata. [description] is spoken in
 * place of a symbol ("Match case" for "Aa").
 *
 * Pass [onClick] to make it interactive; without it the chip is drawn muted, a key that does nothing
 * now. Callers with dense controls can supply an explicit touch-target size without changing every
 * chip surface.
 */
@Composable
fun KeyChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    compact: Boolean = false,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) = KeyChipSurface(modifier, active, compact, description, onClick) { color ->
    Text(text, style = IcodeType.MonoSmall.copy(color = color), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** A [KeyChip] showing [icon], spoken as [description]. */
@Composable
fun KeyChip(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) = KeyChipSurface(modifier, active, compact, description, onClick) { color ->
    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(IcodeSizes.IconSm))
}

@Composable
private fun KeyChipSurface(
    modifier: Modifier,
    active: Boolean,
    compact: Boolean,
    description: String?,
    onClick: (() -> Unit)?,
    content: @Composable (color: Color) -> Unit,
) {
    // A key that toggles (Match case, a latched Ctrl) says whether it is on, as the bottom bar's toggles do.
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick).semantics { selected = active }
    Box(
        modifier = clickableModifier
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)
            .background(if (active) IcodeColors.Background else IcodeColors.SurfaceElevated, IcodeShapes.Small)
            .border(1.dp, if (active) IcodeColors.Accent else Color.Transparent, IcodeShapes.Small)
            .padding(horizontal = if (compact) IcodeSpacing.Space1 else 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        content(
            when {
                onClick == null -> IcodeColors.TextMuted
                active -> IcodeColors.Accent
                else -> IcodeColors.TextPrimary
            },
        )
    }
}

/**
 * ↓ over what scrolls, at its end: back to the latest — the terminal's output, the chat's reply.
 * Shown, fading in and out, only while [visible]: the user scrolled back from the end.
 */
@Composable
fun JumpToLatestButton(visible: Boolean, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(IcodeSizes.TouchTarget).background(IcodeColors.SurfaceElevated, IcodeShapes.Pill),
        ) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = contentDescription, tint = IcodeColors.Green)
        }
    }
}

/**
 * The floating + of a list (docs/workspaces-and-files.md): what can be made where the list is,
 * from a menu anchored on it ([menu]). The floating stack stands above it.
 */
@Composable
fun IcodeFab(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, menu: @Composable () -> Unit = {}) {
    Box(modifier.floor()) {
        FloatingActionButton(
            onClick = onClick,
            shape = IcodeShapes.Pill,
            containerColor = IcodeColors.Accent,
            contentColor = IcodeColors.OnAccent,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = contentDescription)
        }
        menu()
    }
}
