package dev.icode.mobile.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Single-choice selector.
 *
 * Collapses a list of options to one row, so a screen offering several related
 * choices shows one control rather than every option at once.
 */
@Composable
fun <T> IcodeDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionDescription: ((T) -> String?)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
        SectionLabel(label)
        Box {
            IcodeCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (expanded) IcodeColors.Accent else IcodeColors.Outline,
                onClick = { expanded = true },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            optionLabel(selected),
                            style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                        )
                        optionDescription?.invoke(selected)?.let { description ->
                            Text(description, style = IcodeType.Caption, maxLines = 1)
                        }
                    }
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "Choose",
                        tint = IcodeColors.TextSecondary,
                        modifier = Modifier.size(IcodeSizes.IconMd),
                    )
                }
            }
            IcodeChoiceMenu(expanded, { expanded = false }, options, selected, optionLabel, onSelect, optionDescription)
        }
    }
}

/**
 * The options of a choice, the current one marked: [IcodeDropdown]'s menu, or one anchored to any
 * trigger placed in the same box — an [IcodeChoiceChip] on a toolbar.
 */
@Composable
fun <T> IcodeChoiceMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    optionDescription: ((T) -> String?)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            optionLabel(option),
                            style = IcodeType.Body.copy(
                                color = if (option == selected) IcodeColors.Accent else IcodeColors.TextPrimary,
                            ),
                        )
                        optionDescription?.invoke(option)?.let { description ->
                            Text(description, style = IcodeType.Caption)
                        }
                    }
                },
                trailingIcon = {
                    // The current choice is marked, not only coloured.
                    if (option == selected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = IcodeColors.Accent,
                            modifier = Modifier.size(IcodeSizes.IconMd),
                        )
                    }
                },
                onClick = {
                    onDismiss()
                    onSelect(option)
                },
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space1),
            )
        }
    }
}

/**
 * A compact choice on a toolbar — what is chosen, and a caret — that opens its menu: a chat's mode,
 * model or effort. Small to see, a full touch target to tap.
 */
@Composable
fun IcodeChoiceChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** What the chip chooses, for a screen reader: "Model". */
    role: String? = null,
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(IcodeShapes.Small)
            .clickable(onClickLabel = role?.let { "Choose $it" }, onClick = onClick)
            .semantics { if (role != null) contentDescription = "$role: $label" }
            .padding(horizontal = IcodeSpacing.Space1, vertical = IcodeSpacing.Micro),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
        Text(label, style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
    }
}
