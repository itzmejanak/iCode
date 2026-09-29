package dev.icode.mobile.domain

import kotlinx.coroutines.flow.Flow

enum class IndentType { Spaces, Tabs }

/** Resolved from one app value and optional overrides for the active root. */
data class WorkspacePreferences(
    val workspaceLocation: String? = null,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val editor: EditorSettings = EditorSettings(),
    val terminal: TerminalSettings = TerminalSettings(),
    val globalIndentSize: Int = 4,
    val indentSizeOverride: Int? = null,
    val indentTypeOverride: IndentType? = null,
    val autoFetch: Boolean = false,
    val watcherExcludes: List<String> = emptyList(),
    val loaded: Boolean = true,
    val loadError: String? = null,
) {
    val effectiveIndentSize: Int get() = indentSizeOverride ?: globalIndentSize
    val effectiveIndentType: IndentType get() = indentTypeOverride ?: IndentType.Spaces
}

interface WorkspacePreferencesRepository {
    fun values(workspaceLocation: String?): Flow<WorkspacePreferences>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setUiTextScalePercent(percent: Int)
    suspend fun setCompactLayout(compact: Boolean)
    /** Null returns to the theme's own accent. */
    suspend fun setAccentColor(argb: Int?)
    suspend fun setEditorFontSize(sizeSp: Int)
    suspend fun setEditorFontFamily(family: EditorFontFamily)
    suspend fun setWordWrap(enabled: Boolean)
    suspend fun setAutoSave(mode: AutoSaveMode)
    suspend fun setLineNumbers(enabled: Boolean)
    suspend fun setMinimap(enabled: Boolean)
    suspend fun setBracketMatching(enabled: Boolean)
    suspend fun setTerminalShellPath(path: String)
    suspend fun setTerminalFontSize(sizeSp: Int)
    suspend fun setTerminalFontFamily(family: EditorFontFamily)
    suspend fun setTerminalScrollbackLimit(lines: Int)
    suspend fun setTerminalExtraKeys(keys: List<TerminalExtraKey>)
    suspend fun setTerminalCursorStyle(style: TerminalCursorStyle)
    suspend fun setTerminalBellAlerts(enabled: Boolean)
    suspend fun setTerminalFinishAlerts(enabled: Boolean)
    suspend fun setGlobalIndentSize(size: Int)
    suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?)
    suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?)
    suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean)
}
