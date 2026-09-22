package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.KeyChipRow
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.navigation.DemoActions

private val demoExtraKeys = listOf("Esc", "Tab", "Ctrl", "Alt", "Fn", "◀", "▲", "▼", "▶")
private val cursorStyles = listOf("Block", "Underline", "Bar")

/** Terminal shell, typography, history and extra-key settings. */
@Composable
fun TerminalSettingsScreen(actions: DemoActions) {
    var fontSize by rememberSaveable { mutableIntStateOf(13) }
    var cursor by rememberSaveable { mutableStateOf(cursorStyles.first()) }
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Terminal Settings", subtitle = "Shell, extra keys, scrollback", onBack = actions.back)
        Column(
            modifier = Modifier.padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space4 + IcodeSpacing.Micro),
        ) {
            SectionLabel("Shell Path")
            SettingRow("/bin/bash", Modifier.fillMaxWidth()) {
                Text("EDIT", style = IcodeType.Label.copy(color = IcodeColors.Accent))
            }
            SectionLabel("Typography")
            SettingRow("Font Family", Modifier.fillMaxWidth()) {
                MonoLine("IBM Plex Mono", style = IcodeType.MonoSmall)
            }
            SettingRow("Font Size", Modifier.fillMaxWidth()) {
                Stepper(fontSize, { fontSize-- }, { fontSize++ })
            }
            SectionLabel("History")
            SettingRow("Scrollback Limit", Modifier.fillMaxWidth()) {
                MonoLine("10000 lines", color = IcodeColors.Accent)
            }
            SectionLabel("Extra Keys Row")
            KeyChipRow(
                keys = demoExtraKeys,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                activeKeys = setOf("Ctrl", "Alt"),
            )
            SectionLabel("Cursor Style")
            SettingRow("Cursor Type", Modifier.fillMaxWidth()) {
                SegmentedControl(cursorStyles, cursor) { cursor = it }
            }
        }
    }
}
