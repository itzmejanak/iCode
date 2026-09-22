package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.navigation.DemoActions

/** Editor behavior settings with saveable local state. */
@Composable
fun EditorSettingsScreen(actions: DemoActions) {
    val state = rememberEditorSettingsState()
    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Editor Settings", onBack = actions.back)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            SectionLabel("General")
            SettingRow("Font Size", Modifier.fillMaxWidth()) {
                Stepper(
                    value = state.fontSize,
                    onDecrement = { if (state.fontSize > 9) state.fontSize-- },
                    onIncrement = { if (state.fontSize < 30) state.fontSize++ },
                )
            }
            SettingRow("Font Family", Modifier.fillMaxWidth()) {
                MonoLine("IBM Plex Mono", style = IcodeType.MonoSmall)
            }
            SettingRow("Tab Size", Modifier.fillMaxWidth()) {
                SegmentedControl(listOf("2", "4"), state.tabSize.toString()) { state.tabSize = it.toInt() }
            }
            SettingRow("Word Wrap", Modifier.fillMaxWidth()) {
                IcodeToggle(state.wordWrap) { state.wordWrap = it }
            }
            SectionLabel("Save", Modifier.padding(top = IcodeSpacing.Space2))
            SettingRow("Auto Save", Modifier.fillMaxWidth()) {
                Text("After 1 second⌄", style = IcodeType.Caption.copy(color = IcodeColors.Accent))
            }
            SettingRow("Format On Save", Modifier.fillMaxWidth()) {
                IcodeToggle(state.formatOnSave) { state.formatOnSave = it }
            }
            SectionLabel("Display", Modifier.padding(top = IcodeSpacing.Space2))
            SettingRow("Line Numbers", Modifier.fillMaxWidth()) {
                IcodeToggle(state.lineNumbers) { state.lineNumbers = it }
            }
            SettingRow("Minimap", Modifier.fillMaxWidth()) {
                IcodeToggle(state.minimap) { state.minimap = it }
            }
            SettingRow("Bracket Matching", Modifier.fillMaxWidth()) {
                IcodeToggle(state.brackets) { state.brackets = it }
            }
        }
    }
}
