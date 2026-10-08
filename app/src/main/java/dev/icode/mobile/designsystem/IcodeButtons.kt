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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Filled primary action. Labels are uppercase by design. [compact]: as tall as a bar's choice,
 * among other compact actions; Material keeps its touch target whole.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(buttonHeight(compact)),
        shape = IcodeShapes.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = IcodeColors.Accent,
            contentColor = IcodeColors.OnAccent,
            disabledContainerColor = IcodeColors.SurfaceElevated,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) IcodeSpacing.Space3 else IcodeSpacing.Space4),
    ) {
        Text(text.uppercase(), style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

/** Outlined secondary action. Labels keep the design casing. [compact] as [PrimaryButton]'s. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = IcodeColors.TextPrimary,
    compact: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(buttonHeight(compact)),
        shape = IcodeShapes.Small,
        border = BorderStroke(1.dp, IcodeColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IcodeColors.Surface,
            contentColor = color,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = if (compact) IcodeSpacing.Space3 else IcodeSpacing.Space4),
    ) {
        Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

private fun buttonHeight(compact: Boolean) = if (compact) IcodeSizes.BarChoice else IcodeSizes.TouchTarget

/** Destructive action, filled for confirmation and outlined elsewhere. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false) {
    if (filled) {
        Button(
            onClick = onClick,
            modifier = modifier.height(IcodeSizes.TouchTarget),
            shape = IcodeShapes.Small,
            colors = ButtonDefaults.buttonColors(containerColor = IcodeColors.Error, contentColor = IcodeColors.OnAccent),
        ) { Text(text, style = IcodeType.Label.copy(color = IcodeColors.OnAccent)) }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(IcodeSizes.TouchTarget),
            shape = IcodeShapes.Small,
            border = BorderStroke(1.dp, IcodeColors.Error),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = IcodeColors.Error),
        ) { Text(text, style = IcodeType.Label.copy(color = IcodeColors.Error)) }
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
 * Compact technical chip: keyboard keys, shortcuts and small metadata.
 *
 * Pass [onClick] to make it interactive. Callers with dense controls can
 * supply an explicit touch-target size without changing every chip surface.
 */
@Composable
fun KeyChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    // A key that toggles (Match case, a latched Ctrl) says whether it is on, as the bottom bar's toggles do.
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick).semantics { selected = active }
    Box(
        modifier = clickableModifier
            .background(if (active) IcodeColors.Background else IcodeColors.SurfaceElevated, IcodeShapes.Small)
            .border(1.dp, if (active) IcodeColors.Accent else Color.Transparent, IcodeShapes.Small)
            .padding(horizontal = if (compact) IcodeSpacing.Space1 else 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IcodeType.MonoSmall.copy(
                color = if (active) IcodeColors.Accent else IcodeColors.TextPrimary,
                fontSize = IcodeType.MonoSmall.fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Inline keyboard hint row used by terminal and editor surfaces. */
@Composable
fun KeyChipRow(
    keys: List<String>,
    modifier: Modifier = Modifier,
    chipModifier: Modifier = Modifier,
    activeKeys: Set<String> = emptySet(),
    onKey: ((String) -> Unit)? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        keys.forEach { key ->
            KeyChip(key, modifier = chipModifier, active = key in activeKeys, onClick = onKey?.let { handler -> { handler(key) } })
        }
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
