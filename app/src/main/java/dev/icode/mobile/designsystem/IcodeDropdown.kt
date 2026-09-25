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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

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
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
                            expanded = false
                            onSelect(option)
                        },
                        modifier = Modifier.padding(horizontal = IcodeSpacing.Space1),
                    )
                }
            }
        }
    }
}
