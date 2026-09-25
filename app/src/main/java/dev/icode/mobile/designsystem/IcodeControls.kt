package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Single binary setting control.
 *
 * [onCheckedChange] is declared last so `IcodeToggle(value) { … }` reads
 * naturally at call sites.
 */
@Composable
fun IcodeToggle(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = IcodeColors.TextPrimary,
            checkedTrackColor = IcodeColors.Accent,
            uncheckedThumbColor = IcodeColors.TextPrimary,
            uncheckedTrackColor = IcodeColors.SurfaceTrack,
            uncheckedBorderColor = IcodeColors.Outline,
        ),
    )
}

/** Compact two-to-four option switch used inside settings rows. */
@Composable
fun SegmentedControl(
    values: List<String>,
    active: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(modifier = modifier.background(IcodeColors.SurfaceElevated, IcodeShapes.Small)) {
        values.forEach { value ->
            val selected = value == active
            Box(
                modifier = Modifier
                    .background(if (selected) IcodeColors.Outline else Color.Transparent, IcodeShapes.Chip)
                    .clickable { onSelect(value) }
                    .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space1 + IcodeSpacing.Micro),
            ) {
                Text(
                    value,
                    style = IcodeType.Caption.copy(color = if (selected) IcodeColors.OnAccent else IcodeColors.TextMuted),
                )
            }
        }
    }
}

/** Inline filter/toggle chip (search filters, quick toggles). */
@Composable
fun ChoiceChip(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(if (active) IcodeColors.Accent else IcodeColors.Background, IcodeShapes.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = IcodeSpacing.Space3, vertical = IcodeSpacing.Space2),
    ) {
        Text(
            text,
            style = IcodeType.Caption.copy(color = if (active) IcodeColors.OnAccent else IcodeColors.TextSecondary),
        )
    }
}

/** Numeric preference slider (font scale, transparency, density). */
@Composable
fun IcodeSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = IcodeColors.Accent,
            activeTrackColor = IcodeColors.Accent,
            inactiveTrackColor = IcodeColors.SurfaceTrack,
        ),
    )
}

/** Numeric stepper used by editor and terminal font settings. */
@Composable
fun Stepper(
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        KeyChip("−", onClick = onDecrement)
        MonoLine(value.toString(), color = IcodeColors.Accent)
        KeyChip("+", onClick = onIncrement)
    }
}
