package dev.icode.mobile.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Deprecated: use [IcodeSelect] with [IcodeSelectVariant.Card].
 * Kept as a shim for the C1 migration so existing branches stay green.
 */
@Deprecated("Use IcodeSelect(variant=Card)", ReplaceWith("IcodeSelect(options, selected, optionLabel, onSelect, modifier, label, optionDescription=optionDescription)"))
@Composable
fun <T> IcodeDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionDescription: ((T) -> String?)? = null,
) = IcodeSelect(options, selected, optionLabel, onSelect, modifier, label, IcodeSelectVariant.Card, optionDescription)

/** Deprecated: use [IcodeSelect] — its menu is the single owner. */
@Deprecated("Use IcodeSelect")
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
    // No-op shim — IcodeSelect owns the menu now. Kept to keep old call sites compiling during the migration.
}

@Deprecated("Use IcodeSelect(variant=Chip)")
@Composable
fun IcodeChoiceChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    role: String? = null,
) = IcodeSelect(listOf(label), label, { it }, { onClick() }, modifier, label, IcodeSelectVariant.Chip, chipIcon = icon, chipRole = role)
