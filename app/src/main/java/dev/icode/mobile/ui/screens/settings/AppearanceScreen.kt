package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSlider
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.ThemePreview
import dev.icode.mobile.designsystem.ThemeVariants
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Application appearance; editor and terminal font sizes remain independent. */
@Composable
fun AppearanceScreen(actions: NavigationActions) {
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val appearance = preferences.appearance
    val saver = rememberSettingSaver(preferences, "Could not save appearance")
    var pendingScale by remember(appearance.uiTextScalePercent) {
        mutableFloatStateOf(SettingLimits.uiTextScalePosition(appearance.uiTextScalePercent))
    }
    val accent = IcodeColors.Accent
    val accentArgb = accent.toArgb()
    var pickingAccent by remember { mutableStateOf(false) }
    var variantsOf by remember { mutableStateOf<AppTheme?>(null) }
    // With Match Phone on, the phone's light or dark mode chooses the theme (docs/design-and-navigation.md).
    val shown = appearance.shown(isSystemInDarkTheme())
    val variant = ThemeVariants.find(shown, appearance.variantOf(shown))
    // The variant's own accent, before the user's: what "Use theme default" returns to.
    val themeAccent = IcodePalette.of(shown, variant.id, appearance.borderLines).accent.toArgb()
    // Every variant made once, off the main thread, before its sheet is opened.
    LaunchedEffect(appearance.borderLines) {
        withContext(Dispatchers.Default) {
            AppTheme.entries.forEach { theme -> ThemeVariants.of(theme).forEach { IcodePalette.of(theme, it.id, appearance.borderLines) } }
        }
    }

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Appearance", subtitle = settingsSubtitle(Route.AppearanceSettings), onBack = actions.back)
        IcodePageColumn {
            if (!settingsShown(preferences, saver)) return@IcodePageColumn
            IcodeSection("Color Theme") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.ItemGap),
                ) {
                    AppTheme.entries.forEach { option ->
                        val own = ThemeVariants.find(option, appearance.variantOf(option))
                        val role = appearance.autoRole(option)
                        ThemeSwatch(
                            sample = IcodePalette.of(option, own.id, appearance.borderLines),
                            name = option.label,
                            variant = listOfNotNull(own.name, role).joinToString(" · "),
                            selected = shown == option,
                            // The theme in use, or one Match Phone shows, tapped opens its variants; the other
                            // dark one becomes the theme for nights; without Match Phone, another is switched to.
                            onClick = {
                                when {
                                    shown == option || role != null -> variantsOf = option
                                    appearance.followSystem -> saver.save { repository.setNightTheme(option) }
                                    else -> saver.save { repository.setTheme(option) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SettingRow(
                    "Match Phone",
                    Modifier.fillMaxWidth(),
                    subtitle = if (appearance.followSystem) "Light by day, ${appearance.nightTheme.label} by night" else "Light or dark, as the phone is",
                ) {
                    IcodeToggle(appearance.followSystem) { saver.save { repository.setFollowSystem(shown, it) } }
                }
                SettingRow("${shown.label} variant", Modifier.fillMaxWidth(), onClick = { variantsOf = shown }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                        Text(variant.name, style = IcodeType.Body, color = IcodeColors.TextSecondary)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = IcodeColors.TextSecondary)
                    }
                }
            }
            IcodeSection("Font Size & Scale") {
                IcodeCard(Modifier.fillMaxWidth()) {
                    // The slider's thumb sits on the ends of its track: without room it covered the labels.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
                    ) {
                        Text("Aa", style = IcodeType.Caption)
                        IcodeSlider(
                            value = pendingScale,
                            onValueChange = { pendingScale = it },
                            modifier = Modifier.weight(1f),
                            onValueChangeFinished = {
                                val percent = SettingLimits.uiTextScaleAt(pendingScale)
                                saver.save { repository.setUiTextScalePercent(percent) }
                            },
                        )
                        Text("Aa", style = IcodeType.Body)
                    }
                    Text("${SettingLimits.uiTextScaleAt(pendingScale)}% UI text", style = IcodeType.Caption)
                    Column(Modifier.padding(top = IcodeSpacing.Space2)) {
                        CodeSurface(Modifier.fillMaxWidth()) { MonoLine("const ide = iCode.init();") }
                    }
                }
            }
            IcodeSection("UI Density") {
                SettingRow("Compact Layout", Modifier.fillMaxWidth()) {
                    IcodeToggle(appearance.compactLayout) {
                        saver.save { repository.setCompactLayout(it) }
                    }
                }
                SettingRow("Border Lines", Modifier.fillMaxWidth(), subtitle = "Lines around cards, menus and bars") {
                    IcodeToggle(appearance.borderLines) {
                        saver.save { repository.setBorderLines(it) }
                    }
                }
            }
            IcodeSection("Accent Color") {
                SettingRow(
                    if (appearance.accentColor == null) "Theme accent" else "${AccentColorRules.nameOf(accentArgb)} accent",
                    Modifier.fillMaxWidth(),
                    onClick = { pickingAccent = true },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                    ) {
                        Box(Modifier.size(IcodeSizes.IconSm).background(accent, IcodeShapes.Pill))
                        MonoLine(AccentColorRules.hex(accentArgb), color = accent, style = IcodeType.MonoSmall)
                    }
                }
            }
        }
    }
    variantsOf?.let { theme ->
        ThemeVariantSheet(
            theme = theme,
            appearance = appearance,
            onPick = { picked -> saver.save { repository.setThemeVariant(theme, picked.id) } },
            onClose = { variantsOf = null },
        )
    }
    if (pickingAccent) {
        AccentColorSheet(
            current = accentArgb,
            themeAccent = themeAccent,
            onApply = { argb ->
                pickingAccent = false
                saver.save { repository.setAccentColor(argb) }
            },
            onDismiss = { pickingAccent = false },
        )
    }
}

/** What Match Phone shows [theme] for, when it is on: by day or by night; null otherwise. */
private fun AppearanceSettings.autoRole(theme: AppTheme): String? = when {
    !followSystem -> null
    theme == AppTheme.Light -> "Day"
    theme == nightTheme -> "Night"
    else -> null
}

/** A theme to choose, drawn in the variant it keeps ([sample], named [variant]) as it will be drawn. */
@Composable
private fun ThemeSwatch(sample: IcodePalette, name: String, variant: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IcodeCard(
        modifier = modifier.semantics(mergeDescendants = true) { this.selected = selected },
        borderColor = if (selected) IcodeColors.Accent else null,
        padding = PaddingValues(IcodeSpacing.Space2),
        onClick = onClick,
    ) {
        ThemePreview(sample)
        Text(
            name,
            modifier = Modifier.padding(top = IcodeSpacing.Space2),
            style = IcodeType.Body,
            color = if (selected) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
        )
        Text(variant, style = IcodeType.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
