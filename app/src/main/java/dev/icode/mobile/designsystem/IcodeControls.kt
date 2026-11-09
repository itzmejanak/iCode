package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight

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

/**
 * A tick for choosing items of a list — the runtime's tools. [onCheckedChange] is null when
 * the row around it takes the tap (and says it is a checkbox to a screen reader).
 */
@Composable
fun IcodeCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Checkbox(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled, colors = tickColors())
}

/**
 * [IcodeCheckbox] for a row with rows beneath it: [ToggleableState.Indeterminate] while only some of
 * them are ticked — the NDK (Side by side) with one of its revisions.
 */
@Composable
fun IcodeTriStateCheckbox(
    state: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TriStateCheckbox(state = state, onClick = onClick, modifier = modifier, enabled = enabled, colors = tickColors())
}

@Composable
private fun tickColors() = CheckboxDefaults.colors(
    checkedColor = IcodeColors.Accent,
    uncheckedColor = IcodeColors.Outline,
    checkmarkColor = IcodeColors.OnAccent,
    disabledCheckedColor = IcodeColors.TextMuted,
    disabledUncheckedColor = IcodeColors.SurfaceTrack,
)

/**
 * Compact two-to-four option switch used inside settings rows. The chosen option
 * is an accent pill with the text colour made for the accent (owner, 2026-09-29),
 * and a screen reader hears the options as one group of radio buttons.
 */
@Composable
fun SegmentedControl(
    values: List<String>,
    active: String,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    Row(modifier = modifier.background(IcodeColors.SurfaceElevated, IcodeShapes.Small).selectableGroup()) {
        values.forEach { value ->
            val selected = value == active
            Box(
                modifier = Modifier
                    .background(if (selected) IcodeColors.Accent else Color.Transparent, IcodeShapes.Chip)
                    .selectable(selected = selected, role = Role.RadioButton) { onSelect(value) }
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
        KeyChip(Icons.Outlined.Remove, "Decrease", onClick = onDecrement)
        MonoLine(value.toString(), color = IcodeColors.Accent)
        KeyChip(Icons.Outlined.Add, "Increase", onClick = onIncrement)
    }
}

/**
 * One choice of a few, as a setup wizard asks it (Android Studio's Install Type): a radio — or, when
 * several may be chosen ([multiple]), a tick — the choice's name, what it means when it says, and what
 * it costs at the end when it has a cost. The whole row takes the tap, said as a radio button or a
 * checkbox; the chosen one sits on the elevated surface. Single choices go in a `selectableGroup`.
 */
@Composable
fun IcodeOptionRow(
    title: String,
    description: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    multiple: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(IcodeShapes.Small)
            .background(if (selected) IcodeColors.SurfaceElevated else Color.Transparent)
            .then(
                if (multiple) Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onSelect() })
                else Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
            )
            .padding(vertical = IcodeSpacing.Space2, horizontal = IcodeSpacing.Space1),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
    ) {
        if (multiple) {
            IcodeCheckbox(selected, onCheckedChange = null)
        } else {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(selectedColor = IcodeColors.Accent, unselectedColor = IcodeColors.Outline),
            )
        }
        Column(Modifier.weight(1f).padding(top = IcodeSpacing.Micro)) {
            Text(title, style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold))
            description?.takeIf { it.isNotBlank() }?.let { Text(it, style = IcodeType.Caption) }
        }
        if (trailing != null) Text(trailing, style = IcodeType.Caption, modifier = Modifier.padding(top = IcodeSpacing.Micro))
    }
}
