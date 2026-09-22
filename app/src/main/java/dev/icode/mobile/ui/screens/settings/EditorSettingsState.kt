package dev.icode.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Editable state of the editor settings page.
 *
 * Survives configuration changes through [Saver]; Phase 3 replaces it with the
 * settings repository while keeping the same call site.
 */
@Stable
internal class EditorSettingsState(
    fontSize: Int = 14,
    tabSize: Int = 2,
    wordWrap: Boolean = true,
    formatOnSave: Boolean = true,
    lineNumbers: Boolean = true,
    minimap: Boolean = false,
    brackets: Boolean = true,
) {
    var fontSize by mutableIntStateOf(fontSize)
    var tabSize by mutableIntStateOf(tabSize)
    var wordWrap by mutableStateOf(wordWrap)
    var formatOnSave by mutableStateOf(formatOnSave)
    var lineNumbers by mutableStateOf(lineNumbers)
    var minimap by mutableStateOf(minimap)
    var brackets by mutableStateOf(brackets)

    companion object {
        fun saver(): Saver<EditorSettingsState, Any> = listSaver(
            save = { state ->
                listOf(
                    state.fontSize,
                    state.tabSize,
                    state.wordWrap,
                    state.formatOnSave,
                    state.lineNumbers,
                    state.minimap,
                    state.brackets,
                )
            },
            restore = { saved ->
                EditorSettingsState(
                    fontSize = saved[0] as Int,
                    tabSize = saved[1] as Int,
                    wordWrap = saved[2] as Boolean,
                    formatOnSave = saved[3] as Boolean,
                    lineNumbers = saved[4] as Boolean,
                    minimap = saved[5] as Boolean,
                    brackets = saved[6] as Boolean,
                )
            },
        )
    }
}

@Composable
internal fun rememberEditorSettingsState(): EditorSettingsState =
    rememberSaveable(saver = EditorSettingsState.saver()) { EditorSettingsState() }
