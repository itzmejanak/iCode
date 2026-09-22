package dev.icode.mobile.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Filled primary action. Labels are uppercase by design. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(IcodeSizes.TouchTarget),
        shape = IcodeShapes.Small,
        colors = ButtonDefaults.buttonColors(
            containerColor = IcodeColors.Accent,
            contentColor = IcodeColors.OnAccent,
            disabledContainerColor = IcodeColors.SurfaceElevated,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = IcodeSpacing.Space4),
    ) {
        Text(text.uppercase(), style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

/** Outlined secondary action. Labels keep the Figma casing. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = IcodeColors.TextPrimary,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(IcodeSizes.TouchTarget),
        shape = IcodeShapes.Small,
        border = BorderStroke(1.dp, IcodeColors.Outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IcodeColors.Surface,
            contentColor = color,
            disabledContentColor = IcodeColors.TextMuted,
        ),
        contentPadding = PaddingValues(horizontal = IcodeSpacing.Space4),
    ) {
        Text(text, style = IcodeType.Label.copy(color = LocalContentColor.current))
    }
}

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

/**
 * Compact technical chip: keyboard keys, shortcuts and small metadata.
 *
 * Pass [onClick] to make it an interactive chip; interactive chips render the
 * active state and a 48 dp touch target through [Modifier.clickable].
 */
@Composable
fun KeyChip(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Box(
        modifier = clickableModifier
            .background(if (active) IcodeColors.Background else IcodeColors.SurfaceElevated, IcodeShapes.Small)
            .border(1.dp, if (active) IcodeColors.Accent else Color.Transparent, IcodeShapes.Small)
            .padding(horizontal = if (compact) 4.dp else 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = IcodeType.MonoSmall.copy(
                color = if (active) IcodeColors.Accent else IcodeColors.TextPrimary,
                fontSize = if (compact) 8.sp else IcodeType.MonoSmall.fontSize,
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
    activeKeys: Set<String> = emptySet(),
    onKey: ((String) -> Unit)? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
        keys.forEach { key ->
            KeyChip(key, active = key in activeKeys, onClick = onKey?.let { handler -> { handler(key) } })
        }
    }
}
