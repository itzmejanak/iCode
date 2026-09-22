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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.icode.mobile.designsystem.CodeSurface
import dev.icode.mobile.designsystem.IcodeCard
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeShapes
import dev.icode.mobile.designsystem.IcodeSlider
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.navigation.DemoActions

private val themeOptions = listOf("Dark", "Light", "AMOLED")

/** Theme, font scale and density settings. */
@Composable
fun AppearanceScreen(actions: DemoActions) {
    var theme by rememberSaveable { mutableStateOf(themeOptions.first()) }
    var scale by rememberSaveable { mutableFloatStateOf(0.58f) }
    var compact by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Appearance", subtitle = "Theme, fonts, and display", onBack = actions.back)
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            SectionLabel("Color Theme")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2 + IcodeSpacing.Micro),
            ) {
                themeOptions.forEach { option ->
                    ThemeSwatch(
                        option = option,
                        selected = theme == option,
                        onClick = { theme = option },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            SectionLabel("Font Size & Scale")
            IcodeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Aa", style = IcodeType.Caption)
                    IcodeSlider(value = scale, onValueChange = { scale = it }, modifier = Modifier.weight(1f))
                    Text("Aa", style = IcodeType.Body)
                }
                Column(Modifier.padding(top = IcodeSpacing.Space2)) {
                    CodeSurface(Modifier.fillMaxWidth()) {
                        MonoLine("const ide = iCode.init();")
                    }
                }
            }
            SectionLabel("UI Density")
            SettingRow("Compact Layout", Modifier.fillMaxWidth()) {
                IcodeToggle(compact) { compact = it }
            }
            SectionLabel("Accent Color")
            SettingRow("Ember Accent", Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space2),
                ) {
                    Box(Modifier.size(IcodeSizes.IconSm).background(IcodeColors.Accent, IcodeShapes.Pill))
                    MonoLine("#FF6B2C", color = IcodeColors.Accent, style = IcodeType.MonoSmall)
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatch(option: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IcodeCard(
        modifier = modifier,
        borderColor = if (selected) IcodeColors.Accent else IcodeColors.Outline,
        onClick = onClick,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(IcodeSizes.Swatch)
                .background(if (option == "Light") IcodeColors.TextPrimary else Color.Black),
        )
        Text(
            option,
            modifier = Modifier.padding(top = IcodeSpacing.Space2),
            style = IcodeType.Body,
            color = if (selected) IcodeColors.TextPrimary else IcodeColors.TextSecondary,
        )
    }
}
