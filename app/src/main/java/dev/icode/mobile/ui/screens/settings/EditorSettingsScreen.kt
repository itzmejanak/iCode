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
import dev.icode.mobile.designsystem.IcodeDropdown
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.MonoLine
import dev.icode.mobile.designsystem.SectionLabel
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository

/** Application editor preferences. Values come from the same store as workspace overrides. */
@Composable
fun EditorSettingsScreen(actions: NavigationActions) {
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val saver = rememberSettingSaver(preferences, "Could not save setting")

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Editor Settings", onBack = actions.back)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(IcodeSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space3),
        ) {
            when {
                preferences.loadError != null -> Text(
                    "Settings unavailable: ${preferences.loadError}",
                    style = IcodeType.Caption.copy(color = IcodeColors.Error),
                )
                !preferences.loaded -> Text("Loading settings…", style = IcodeType.Caption)
                else -> {
                    val editor = preferences.editor
                    SectionLabel("General")
                    saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
                    SettingRow("Font Size", Modifier.fillMaxWidth()) {
                        Stepper(
                            value = editor.fontSizeSp,
                            onDecrement = {
                                if (editor.fontSizeSp > SettingLimits.fontSizesSp.first) saver.save { repository.setEditorFontSize(editor.fontSizeSp - 1) }
                            },
                            onIncrement = {
                                if (editor.fontSizeSp < SettingLimits.fontSizesSp.last) saver.save { repository.setEditorFontSize(editor.fontSizeSp + 1) }
                            },
                        )
                    }
                    IcodeDropdown(
                        label = "Font Family",
                        options = EditorFontFamily.entries,
                        selected = editor.fontFamily,
                        optionLabel = { if (it == EditorFontFamily.PlexMono) "IBM Plex Mono" else "System Monospace" },
                        onSelect = { family -> saver.save { repository.setEditorFontFamily(family) } },
                    )
                    SettingRow("Tab Size", Modifier.fillMaxWidth()) {
                        SegmentedControl(listOf("2", "4"), preferences.globalIndentSize.toString()) { selected ->
                            saver.save { repository.setGlobalIndentSize(selected.toInt()) }
                        }
                    }
                    SettingRow("Word Wrap", Modifier.fillMaxWidth()) {
                        IcodeToggle(editor.wordWrap) { saver.save { repository.setWordWrap(it) } }
                    }
                    SectionLabel("Save", Modifier.padding(top = IcodeSpacing.Space2))
                    IcodeDropdown(
                        label = "Auto Save",
                        options = AutoSaveMode.entries,
                        selected = editor.autoSave,
                        optionLabel = {
                            when (it) {
                                AutoSaveMode.Off -> "Off"
                                AutoSaveMode.AfterOneSecond -> "After 1 second"
                                AutoSaveMode.AfterFiveSeconds -> "After 5 seconds"
                            }
                        },
                        onSelect = { mode -> saver.save { repository.setAutoSave(mode) } },
                    )
                    SettingRow(
                        title = "Format On Save",
                        modifier = Modifier.fillMaxWidth(),
                        subtitle = "Available when a real formatter is connected",
                    ) {
                        MonoLine("Unavailable", color = IcodeColors.TextMuted, style = IcodeType.MonoSmall)
                    }
                    SectionLabel("Display", Modifier.padding(top = IcodeSpacing.Space2))
                    SettingRow("Line Numbers", Modifier.fillMaxWidth()) {
                        IcodeToggle(editor.lineNumbers) { saver.save { repository.setLineNumbers(it) } }
                    }
                    SettingRow("Minimap", Modifier.fillMaxWidth(), subtitle = "Experimental editor feature") {
                        IcodeToggle(editor.minimap) { saver.save { repository.setMinimap(it) } }
                    }
                    SettingRow("Bracket Matching", Modifier.fillMaxWidth()) {
                        IcodeToggle(editor.bracketMatching) { saver.save { repository.setBracketMatching(it) } }
                    }
                }
            }
        }
    }
}
