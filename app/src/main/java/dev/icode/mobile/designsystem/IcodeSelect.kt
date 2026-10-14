package dev.icode.mobile.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp

/**
 * One select primitive — same tokens, same menu, two variants.
 *
 * Variant Card: labeled `IcodeCard` (settings). Variant Chip: compact row with caret (composer/toolbars).
 * Both use one menu with identical selected state (Accent + Check).
 */
enum class IcodeSelectVariant { Card, Chip }

@Composable
fun <T> IcodeSelect(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    variant: IcodeSelectVariant = IcodeSelectVariant.Card,
    optionDescription: ((T) -> String?)? = null,
    chipIcon: ImageVector? = null,
    chipRole: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val dismiss = { expanded = false }
    when (variant) {
        IcodeSelectVariant.Card -> {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2)) {
                label?.let { SectionLabel(it) }
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
                                Text(optionLabel(selected), style = IcodeType.Body.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                optionDescription?.invoke(selected)?.let { Text(it, style = IcodeType.Caption, maxLines = 1) }
                            }
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose", tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconMd))
                        }
                    }
                    SelectMenu(expanded, dismiss, options, selected, optionLabel, onSelect, optionDescription)
                }
            }
        }
        IcodeSelectVariant.Chip -> {
            Box(modifier = modifier) {
                Row(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(IcodeShapes.Small)
                        .clickable(onClickLabel = chipRole?.let { "Choose $it" } ?: "Choose ${label ?: optionLabel(selected)}") { expanded = true }
                        .semantics { if (chipRole != null) contentDescription = "$chipRole: ${optionLabel(selected)}" }
                        .padding(horizontal = IcodeSpacing.Space1, vertical = IcodeSpacing.Micro),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Micro),
                ) {
                    if (chipIcon != null) Icon(chipIcon, contentDescription = null, tint = IcodeColors.TextSecondary, modifier = Modifier.size(IcodeSizes.IconSm))
                    // Chip shows the selected value (not the generic label) — composer must read "claude-fable-5.1", not "Model".
                    Text(optionLabel(selected), style = IcodeType.Caption.copy(color = IcodeColors.TextSecondary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, tint = IcodeColors.TextMuted, modifier = Modifier.size(IcodeSizes.IconSm))
                }
                SelectMenu(expanded, dismiss, options, selected, optionLabel, onSelect, optionDescription)
            }
        }
    }
}

@Composable
private fun <T> SelectMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    optionDescription: ((T) -> String?)?,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(IcodeColors.SurfaceElevated, IcodeShapes.Small)
            .border(1.dp, IcodeColors.Outline, IcodeShapes.Small),
        containerColor = IcodeColors.SurfaceElevated,
        shape = IcodeShapes.Small,
        tonalElevation = 0.dp,
    ) {
        options.forEach { option ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(
                            optionLabel(option),
                            style = IcodeType.Body.copy(color = if (option == selected) IcodeColors.Accent else IcodeColors.TextPrimary),
                        )
                        optionDescription?.invoke(option)?.let { Text(it, style = IcodeType.Caption) }
                    }
                },
                trailingIcon = {
                    if (option == selected) Icon(Icons.Outlined.Check, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconMd))
                },
                onClick = { onDismiss(); onSelect(option) },
                modifier = Modifier.padding(horizontal = IcodeSpacing.Space1),
            )
        }
    }
}
