package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.icode.mobile.designsystem.BuildEnvironment
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
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.navigation.Route
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository

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
    val themeAccent = AccentColorRules.themeAccent(appearance.theme, BuildEnvironment)

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
                        ThemeSwatch(
                            sample = IcodePalette.of(option, appearance.borderLines),
                            name = option.name,
                            selected = appearance.theme == option,
                            onClick = { saver.save { repository.setTheme(option) } },
                            modifier = Modifier.weight(1f),
                        )
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

/** A theme to choose, its page shown as it will be drawn ([sample]). */
@Composable
private fun ThemeSwatch(sample: IcodePalette, name: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IcodeCard(
        modifier = modifier.semantics { this.selected = selected },
        borderColor = if (selected) IcodeColors.Accent else null,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().height(IcodeSizes.Swatch).background(sample.background))
        Text(
            name,
            modifier = Modifier.padding(top = IcodeSpacing.Space2),
            style = IcodeType.Body,
            color = if (selected) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
        )
    }
}
