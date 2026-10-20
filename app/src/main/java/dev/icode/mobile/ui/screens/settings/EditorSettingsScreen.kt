package dev.icode.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.icode.mobile.designsystem.IcodeColors
import dev.icode.mobile.designsystem.IcodePageColumn
import dev.icode.mobile.designsystem.IcodeSection
import dev.icode.mobile.designsystem.IcodeSelect
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.IcodeSpacing
import dev.icode.mobile.designsystem.IcodeToggle
import dev.icode.mobile.designsystem.IcodeTopBar
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.designsystem.SegmentedControl
import dev.icode.mobile.designsystem.SettingRow
import dev.icode.mobile.designsystem.Stepper
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.EditorKey
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.navigation.NavigationActions
import dev.icode.mobile.ui.EditorKeyChip
import dev.icode.mobile.ui.LocalWorkspacePreferences
import dev.icode.mobile.ui.LocalWorkspacePreferencesRepository

/** Application editor preferences. Values come from the same store as workspace overrides. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorSettingsScreen(actions: NavigationActions) {
    val preferences = LocalWorkspacePreferences.current
    val repository = LocalWorkspacePreferencesRepository.current
    val saver = rememberSettingSaver(preferences, "Could not save setting")

    Column(Modifier.fillMaxSize()) {
        IcodeTopBar("Editor Settings", onBack = actions.back)
        IcodePageColumn {
            when {
                preferences.loadError != null -> Text(
                    "Settings unavailable: ${preferences.loadError}",
                    style = IcodeType.Caption.copy(color = IcodeColors.Error),
                )
                !preferences.loaded -> Text("Loading settings…", style = IcodeType.Caption)
                else -> {
                    val editor = preferences.editor
                    saver.error?.let { Text(it, style = IcodeType.Caption.copy(color = IcodeColors.Error)) }
                    IcodeSection("General") {
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
                        IcodeSelect(
                            options = EditorFontFamily.entries,
                            selected = editor.fontFamily,
                            optionLabel = { if (it == EditorFontFamily.PlexMono) "IBM Plex Mono" else "System Monospace" },
                            onSelect = { family -> saver.save { repository.setEditorFontFamily(family) } },
                            label = "Font Family",
                        )
                        SettingRow("Tab Size", Modifier.fillMaxWidth()) {
                            SegmentedControl(listOf("2", "4"), preferences.globalIndentSize.toString()) { selected ->
                                saver.save { repository.setGlobalIndentSize(selected.toInt()) }
                            }
                        }
                        SettingRow("Word Wrap", Modifier.fillMaxWidth()) {
                            IcodeToggle(editor.wordWrap) { saver.save { repository.setWordWrap(it) } }
                        }
                    }
                    IcodeSection("Editing") {
                        SettingRow("Auto-Close Brackets", Modifier.fillMaxWidth(), subtitle = "Typing ( [ { \" ' adds the closing one") {
                            IcodeToggle(editor.autoCloseBrackets) { saver.save { repository.setAutoCloseBrackets(it) } }
                        }
                        SettingRow(
                            "Keyboard Suggestions",
                            Modifier.fillMaxWidth(),
                            subtitle = if (editor.keyboardSuggestions) "The keyboard suggests and may autocorrect words" else "Off: the keyboard does not autocorrect code",
                        ) {
                            IcodeToggle(editor.keyboardSuggestions) { saver.save { repository.setKeyboardSuggestions(it) } }
                        }
                    }
                    IcodeSection("Keys Row") {
                        Text("Tap a key to show or hide it above the keyboard.", style = IcodeType.Caption)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1), verticalArrangement = Arrangement.spacedBy(IcodeSpacing.Space1)) {
                            EditorKey.entries.forEach { key ->
                                EditorKeyChip(key, Modifier.heightIn(min = IcodeSizes.TouchTarget), active = key in editor.keys, onClick = {
                                    val chosen = EditorKey.entries.filter { if (it == key) it !in editor.keys else it in editor.keys }
                                    saver.save { repository.setEditorKeys(chosen) }
                                })
                            }
                        }
                    }
                    IcodeSection("Save") {
                        IcodeSelect(
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
                            label = "Auto Save",
                        )
                    }
                    IcodeSection("Display") {
                        SettingRow("Line Numbers", Modifier.fillMaxWidth()) {
                            IcodeToggle(editor.lineNumbers) { saver.save { repository.setLineNumbers(it) } }
                        }
                        SettingRow("Minimap", Modifier.fillMaxWidth(), subtitle = "Experimental editor feature") {
                            IcodeToggle(editor.minimap) { saver.save { repository.setMinimap(it) } }
                        }
                        SettingRow("Bracket Matching", Modifier.fillMaxWidth()) {
                            IcodeToggle(editor.bracketMatching) { saver.save { repository.setBracketMatching(it) } }
                        }
                        SettingRow("Git Change Markers", Modifier.fillMaxWidth(), subtitle = "Changed lines marked beside the line numbers") {
                            IcodeToggle(editor.changeMarkers) { saver.save { repository.setChangeMarkers(it) } }
                        }
                    }
                }
            }
        }
    }
}
