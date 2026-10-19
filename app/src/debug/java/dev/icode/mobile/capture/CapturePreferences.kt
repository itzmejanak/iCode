package dev.icode.mobile.capture

import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AutoSaveMode
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.EditorKey
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.TerminalCursorStyle
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Default settings, and one workspace with its own overrides so Workspace Settings has some to show. */
class CapturePreferences : WorkspacePreferencesRepository {
    override fun values(workspaceLocation: String?): Flow<WorkspacePreferences> = flowOf(
        if (workspaceLocation == null) {
            WorkspacePreferences()
        } else {
            WorkspacePreferences(
                workspaceLocation = workspaceLocation,
                indentSizeOverride = 2,
                autoFetch = true,
            )
        },
    )

    // A capture shows fixed values; nothing is stored.
    override suspend fun setTheme(theme: AppTheme) = Unit
    override suspend fun setUiTextScalePercent(percent: Int) = Unit
    override suspend fun setCompactLayout(compact: Boolean) = Unit
    override suspend fun setCardBorders(shown: Boolean) = Unit
    override suspend fun setAccentColor(argb: Int?) = Unit
    override suspend fun setEditorFontSize(sizeSp: Int) = Unit
    override suspend fun setEditorFontFamily(family: EditorFontFamily) = Unit
    override suspend fun setWordWrap(enabled: Boolean) = Unit
    override suspend fun setAutoSave(mode: AutoSaveMode) = Unit
    override suspend fun setLineNumbers(enabled: Boolean) = Unit
    override suspend fun setMinimap(enabled: Boolean) = Unit
    override suspend fun setBracketMatching(enabled: Boolean) = Unit
    override suspend fun setEditorKeys(keys: List<EditorKey>) = Unit
    override suspend fun setKeyboardSuggestions(enabled: Boolean) = Unit
    override suspend fun setChangeMarkers(enabled: Boolean) = Unit
    override suspend fun setAutoCloseBrackets(enabled: Boolean) = Unit
    override suspend fun setTerminalShellPath(path: String) = Unit
    override suspend fun setTerminalFontSize(sizeSp: Int) = Unit
    override suspend fun setTerminalFontFamily(family: EditorFontFamily) = Unit
    override suspend fun setTerminalScrollbackLimit(lines: Int) = Unit
    override suspend fun setTerminalExtraKeys(keys: List<TerminalExtraKey>) = Unit
    override suspend fun setTerminalCursorStyle(style: TerminalCursorStyle) = Unit
    override suspend fun setTerminalBellAlerts(enabled: Boolean) = Unit
    override suspend fun setTerminalFinishAlerts(enabled: Boolean) = Unit
    override suspend fun setGlobalIndentSize(size: Int) = Unit
    override suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?) = Unit
    override suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?) = Unit
    override suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean) = Unit
}
