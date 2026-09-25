package dev.icode.mobile.demo

import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.EditorSettings
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalCursorStyle
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.domain.TerminalSettingRules
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/** Frame-40 fixture remains separate from production storage until capture cleanup. */
class DemoWorkspacePreferencesRepository : WorkspacePreferencesRepository {
    private val values = MutableStateFlow<Map<String, WorkspacePreferences>>(emptyMap())
    private val globalIndent = MutableStateFlow(4)
    private val appearance = MutableStateFlow(AppearanceSettings())
    private val editor = MutableStateFlow(EditorSettings())
    private val terminal = MutableStateFlow(TerminalSettings())

    override fun values(workspaceLocation: String?): Flow<WorkspacePreferences> =
        combine(values, globalIndent, appearance, editor, terminal) { all, global, appAppearance, appEditor, appTerminal ->
        val base = if (workspaceLocation == null) WorkspacePreferences()
        else all[workspaceLocation] ?: WorkspacePreferences(
            workspaceLocation = workspaceLocation,
            indentSizeOverride = 2,
            autoFetch = true,
            watcherExcludes = listOf("**/node_modules/**", "**/build/**", "**/.git/**"),
        )
        base.copy(globalIndentSize = global, appearance = appAppearance, editor = appEditor, terminal = appTerminal)
    }

    override suspend fun setTheme(theme: AppTheme) { appearance.update { it.copy(theme = theme) } }

    override suspend fun setUiTextScalePercent(percent: Int) {
        require(percent in 100..150 && percent % 5 == 0)
        appearance.update { it.copy(uiTextScalePercent = percent) }
    }

    override suspend fun setCompactLayout(compact: Boolean) {
        appearance.update { it.copy(compactLayout = compact) }
    }

    override suspend fun setEditorFontSize(sizeSp: Int) {
        require(sizeSp in 9..30)
        editor.update { it.copy(fontSizeSp = sizeSp) }
    }

    override suspend fun setEditorFontFamily(family: EditorFontFamily) {
        editor.update { it.copy(fontFamily = family) }
    }

    override suspend fun setWordWrap(enabled: Boolean) { editor.update { it.copy(wordWrap = enabled) } }

    override suspend fun setAutoSave(mode: AutoSaveMode) { editor.update { it.copy(autoSave = mode) } }

    override suspend fun setLineNumbers(enabled: Boolean) { editor.update { it.copy(lineNumbers = enabled) } }

    override suspend fun setMinimap(enabled: Boolean) { editor.update { it.copy(minimap = enabled) } }

    override suspend fun setBracketMatching(enabled: Boolean) {
        editor.update { it.copy(bracketMatching = enabled) }
    }

    override suspend fun setTerminalShellPath(path: String) {
        require(TerminalSettingRules.validShellPath(path))
        terminal.update { it.copy(shellPath = path) }
    }

    override suspend fun setTerminalFontSize(sizeSp: Int) {
        require(sizeSp in 9..30)
        terminal.update { it.copy(fontSizeSp = sizeSp) }
    }

    override suspend fun setTerminalFontFamily(family: EditorFontFamily) {
        terminal.update { it.copy(fontFamily = family) }
    }

    override suspend fun setTerminalScrollbackLimit(lines: Int) {
        require(lines in TerminalSettingRules.scrollbackChoices)
        terminal.update { it.copy(scrollbackLines = lines) }
    }

    override suspend fun setTerminalExtraKeys(keys: List<TerminalExtraKey>) {
        require(keys.distinct().size == keys.size)
        terminal.update { it.copy(extraKeys = keys) }
    }

    override suspend fun setTerminalCursorStyle(style: TerminalCursorStyle) {
        terminal.update { it.copy(cursorStyle = style) }
    }

    override suspend fun setTerminalBellAlerts(enabled: Boolean) {
        terminal.update { it.copy(bellAlerts = enabled) }
    }

    override suspend fun setTerminalFinishAlerts(enabled: Boolean) {
        terminal.update { it.copy(finishAlerts = enabled) }
    }

    override suspend fun setGlobalIndentSize(size: Int) {
        require(size in 1..8)
        globalIndent.value = size
        values.update { all -> all.mapValues { (_, value) -> value.copy(globalIndentSize = size) } }
    }

    override suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?) {
        require(size == null || size in 1..8)
        update(workspaceLocation) { it.copy(indentSizeOverride = size) }
    }

    override suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?) {
        update(workspaceLocation) { it.copy(indentTypeOverride = type) }
    }

    override suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean) {
        update(workspaceLocation) { it.copy(autoFetch = enabled) }
    }

    private fun update(location: String, change: (WorkspacePreferences) -> WorkspacePreferences) {
        values.update { all ->
            val current = all[location] ?: WorkspacePreferences(
                workspaceLocation = location,
                globalIndentSize = globalIndent.value,
                indentSizeOverride = 2,
                autoFetch = true,
                watcherExcludes = listOf("**/node_modules/**", "**/build/**", "**/.git/**"),
            )
            all + (location to change(current))
        }
    }
}
