package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.IcodeBottomSheet
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeModalSheet
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.ThemePreview
import dev.icode.mobile.designsystem.ThemeVariant
import dev.icode.mobile.designsystem.ThemeVariants
import dev.icode.mobile.designsystem.VariantGroup
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings

/**
 * [theme]'s variants as cards of themselves (docs/design-and-navigation.md), each drawn as the app
 * would draw it, with the current Border Lines and accent. A tap uses one at once: the app and this
 * sheet take it while the sheet stays open, so variants can be compared; the one before is a tap away.
 */
@Composable
internal fun ThemeVariantSheet(theme: AppTheme, appearance: AppearanceSettings, onPick: (ThemeVariant) -> Unit, onClose: () -> Unit) {
    val variants = ThemeVariants.of(theme)
    val chosen = ThemeVariants.find(theme, appearance.variantOf(theme)).id
    IcodeModalSheet(onClose = onClose) {
        IcodeBottomSheet(
            title = "${theme.label} variants",
            subtitle = "${variants.size} · tap one to use it",
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (appearance.accentColor != null) {
                Text("Your accent replaces each variant's own. Reset it in Accent Color.", style = IcodeType.Caption)
            }
            VariantGroup.entries.forEach { group ->
                val listed = variants.filter { it.group == group }
                if (listed.isEmpty()) return@forEach
                SectionLabel(group.label, Modifier.padding(top = IcodeSpacing.Space2))
                VariantGrid(listed) { variant, modifier ->
                    VariantCard(variant, appearance, picked = variant.id == chosen, modifier = modifier) { onPick(variant) }
                }
            }
        }
    }
}

/** Two cards to a phone's width, more where the sheet is wider. */
@Composable
private fun VariantGrid(variants: List<ThemeVariant>, card: @Composable (ThemeVariant, Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = (maxWidth / IcodeSizes.ThemeCardMin).toInt().coerceAtLeast(2)
        Column(verticalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
            variants.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap)) {
                    row.forEach { card(it, Modifier.weight(1f)) }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun VariantCard(variant: ThemeVariant, appearance: AppearanceSettings, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val palette = IcodePalette.of(variant.theme, variant.id, appearance.borderLines).withAccent(appearance.accentColor)
    IcodeCard(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = variant.name
            selected = picked
        },
        borderColor = if (picked) IcodeColors.Accent else null,
        padding = PaddingValues(IcodeSpacing.Space2),
        onClick = onClick,
    ) {
        ThemePreview(palette)
        Row(Modifier.fillMaxWidth().padding(top = IcodeSpacing.Space2), verticalAlignment = Alignment.CenterVertically) {
            Text(
                variant.name,
                modifier = Modifier.weight(1f),
                style = IcodeType.Body,
                color = if (picked) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (picked) Icon(Icons.Outlined.Check, contentDescription = null, tint = IcodeColors.Accent, modifier = Modifier.size(IcodeSizes.IconSm))
        }
    }
}

/** How a theme is named on screen. */
internal val AppTheme.label: String
    get() = when (this) {
        AppTheme.Dark -> "Dark"
        AppTheme.Light -> "Light"
        AppTheme.Amoled -> "AMOLED"
    }

private val VariantGroup.label: String
    get() = when (this) {
        VariantGroup.Icode -> "Default"
        VariantGroup.Classic -> "Editor classics"
        VariantGroup.More -> "More"
    }
