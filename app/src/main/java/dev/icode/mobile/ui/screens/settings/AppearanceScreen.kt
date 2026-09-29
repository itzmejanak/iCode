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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSlider
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
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
    val themeAccent = AccentColorRules.themeAccent(appearance.theme)

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Appearance", subtitle = settingsSubtitle(Route.AppearanceSettings), onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            if (!preferences.loaded) Text("Loading settings…", style = IcodeType.Caption)
            preferences.loadError?.let {
                Text("Settings unavailable: $it", style = IcodeType.Caption.copy(color = IcodeColors.Error))
            }
            saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
            SectionLabel("Color Theme")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
            ) {
                AppTheme.entries.forEach { option ->
                    ThemeSwatch(
                        option = option,
                        selected = appearance.theme == option,
                        onClick = { saver.save { repository.setTheme(option) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SectionLabel("Font Size & Scale")
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
            SectionLabel("UI Density")
            SettingRow("Compact Layout", Modifier.fillMaxWidth()) {
                IcodeToggle(appearance.compactLayout) {
                    saver.save { repository.setCompactLayout(it) }
                }
            }
            SectionLabel("Accent Color")
            SettingRow(
                if (appearance.accentColor == null) "Theme accent" else "${AccentColorRules.nameOf(accentArgb)} accent",
                Modifier.fillMaxWidth(),
                onClick = { if (saver.ready) pickingAccent = true },
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
    if (pickingAccent) {
        AccentColorDialog(
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

@Composable
private fun ThemeSwatch(option: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val sample = when (option) {
        AppTheme.Dark -> IcodePalette.Dark
        AppTheme.Light -> IcodePalette.Light
        AppTheme.Amoled -> IcodePalette.Amoled
    }
    IcodeCard(
        modifier = modifier.semantics { this.selected = selected },
        borderColor = if (selected) IcodeColors.Accent else IcodeColors.Outline,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().height(IcodeSizes.Swatch).background(sample.background))
        Text(
            option.name,
            modifier = Modifier.padding(top = IcodeSpacing.Space2),
            style = IcodeType.Body,
            color = if (selected) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
        )
    }
}
