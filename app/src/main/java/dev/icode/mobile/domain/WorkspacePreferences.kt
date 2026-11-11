package dev.icode.mobile.domain

import dev.icode.mobile.domain.files.ExplorerSettings
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FilesLook
import kotlinx.coroutines.flow.Flow

enum class IndentType { Spaces, Tabs }

/** Resolved from one app value and optional overrides for the active root. */
data class WorkspacePreferences(
    val workspaceLocation: String? = null,
    val appearance: AppearanceSettings = AppearanceSettings(),
    val editor: EditorSettings = EditorSettings(),
    val terminal: TerminalSettings = TerminalSettings(),
    val explorer: ExplorerSettings = ExplorerSettings(),
    val globalIndentSize: Int = 4,
    val indentSizeOverride: Int? = null,
    val indentTypeOverride: IndentType? = null,
    val autoFetch: Boolean = false,
    val loaded: Boolean = true,
    val loadError: String? = null,
) {
    val effectiveIndentSize: Int get() = indentSizeOverride ?: globalIndentSize
    /** There is no app-wide indent type: a workspace without an override indents with spaces. */
    val inheritedIndentType: IndentType get() = IndentType.Spaces
    val effectiveIndentType: IndentType get() = indentTypeOverride ?: inheritedIndentType
}

interface WorkspacePreferencesRepository {
    fun values(workspaceLocation: String?): Flow<WorkspacePreferences>
    suspend fun setTheme(theme: AppTheme)
    suspend fun setUiTextScalePercent(percent: Int)
    suspend fun setCompactLayout(compact: Boolean)
    suspend fun setBorderLines(shown: Boolean)
    /** Null returns to the theme's own accent. */
    suspend fun setAccentColor(argb: Int?)
    /** [theme]'s variant from now on; null, or [ThemeVariantRules.ICODE], returns it to iCode's own. */
    suspend fun setThemeVariant(theme: AppTheme, id: String?)
    /**
     * Auto on or off (docs/theme-variants-plan.md T6), [theme] being the one on screen as it is turned:
     * on, a dark one becomes the theme for nights; off, it stays the theme.
     */
    suspend fun setFollowSystem(theme: AppTheme, follow: Boolean)
    /** The dark theme Auto shows at night; Light is refused. */
    suspend fun setNightTheme(theme: AppTheme)
    /** [pill] kept at [place] from now on; null returns it to where it starts. */
    suspend fun setPillPlace(pill: FloatingPill, place: PillPlace?)
    suspend fun setEditorFontSize(sizeSp: Int)
    suspend fun setEditorFontFamily(family: EditorFontFamily)
    suspend fun setWordWrap(enabled: Boolean)
    suspend fun setAutoSave(mode: AutoSaveMode)
    suspend fun setLineNumbers(enabled: Boolean)
    suspend fun setMinimap(enabled: Boolean)
    suspend fun setBracketMatching(enabled: Boolean)
    suspend fun setEditorKeys(keys: List<EditorKey>)
    suspend fun setKeyboardSuggestions(enabled: Boolean)
    suspend fun setChangeMarkers(enabled: Boolean)
    suspend fun setAutoCloseBrackets(enabled: Boolean)
    suspend fun setSemanticHighlighting(enabled: Boolean)
    suspend fun setTerminalFontSize(sizeSp: Int)
    suspend fun setTerminalFontFamily(family: EditorFontFamily)
    suspend fun setTerminalScrollbackLimit(lines: Int)
    suspend fun setTerminalExtraKeys(keys: List<TerminalExtraKey>)
    suspend fun setTerminalCursorStyle(style: TerminalCursorStyle)
    suspend fun setTerminalBellAlerts(enabled: Boolean)
    suspend fun setTerminalFinishAlerts(enabled: Boolean)
    /** Names starting with "." listed or not, in both explorers (docs/explorer-gaps-plan.md §3.13). */
    suspend fun setShowHidden(shown: Boolean)
    /** The panel's choices, for folders ([view] null) or for one view of its own (§3.12). */
    suspend fun setFilesLook(view: ExplorerView?, look: FilesLook)
    suspend fun setGlobalIndentSize(size: Int)
    suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?)
    suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?)
    suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean)
}
