package dev.icode.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AccentColorRules
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException

private val Context.workspacePreferencesDataStore by preferencesDataStore(name = "workspace_settings")

/** App-private preferences; workspace identity is the gateway's canonical root location. */
class AndroidWorkspacePreferencesRepository internal constructor(
    private val store: DataStore<Preferences>,
) : WorkspacePreferencesRepository {
    constructor(context: Context) : this(context.applicationContext.workspacePreferencesDataStore)
    private val globalIndentKey = intPreferencesKey("editor_indent_size")
    private val themeKey = stringPreferencesKey("appearance_theme")
    private val uiScaleKey = intPreferencesKey("appearance_ui_text_scale_percent")
    private val compactKey = booleanPreferencesKey("appearance_compact_layout")
    private val accentKey = intPreferencesKey("appearance_accent_color")
    private val fontSizeKey = intPreferencesKey("editor_font_size_sp")
    private val fontFamilyKey = stringPreferencesKey("editor_font_family")
    private val wordWrapKey = booleanPreferencesKey("editor_word_wrap")
    private val autoSaveKey = stringPreferencesKey("editor_auto_save")
    private val lineNumbersKey = booleanPreferencesKey("editor_line_numbers")
    private val minimapKey = booleanPreferencesKey("editor_minimap")
    private val bracketKey = booleanPreferencesKey("editor_bracket_matching")
    private val terminalShellKey = stringPreferencesKey("terminal_shell_path")
    private val terminalFontSizeKey = intPreferencesKey("terminal_font_size_sp")
    private val terminalFontFamilyKey = stringPreferencesKey("terminal_font_family")
    private val terminalScrollbackKey = intPreferencesKey("terminal_scrollback_lines")
    private val terminalExtraKeysKey = stringPreferencesKey("terminal_extra_keys")
    private val terminalCursorStyleKey = stringPreferencesKey("terminal_cursor_style")
    private val terminalBellAlertsKey = booleanPreferencesKey("terminal_bell_alerts")
    private val terminalFinishAlertsKey = booleanPreferencesKey("terminal_finish_alerts")

    override fun values(workspaceLocation: String?): Flow<WorkspacePreferences> = store.data.map { prefs ->
        val prefix = workspaceLocation?.let(::workspaceKeyPrefix)
        val indent = prefix?.let { prefs[intPreferencesKey("${it}_indent_size")] }?.takeIf { it in 1..8 }
        val type = prefix?.let { prefs[stringPreferencesKey("${it}_indent_type")] }
            ?.let { runCatching { IndentType.valueOf(it) }.getOrNull() }
        WorkspacePreferences(
            workspaceLocation = workspaceLocation,
            appearance = AppearanceSettings(
                theme = prefs[themeKey]?.let { enumOrNull<AppTheme>(it) } ?: AppTheme.Dark,
                uiTextScalePercent = prefs[uiScaleKey]?.takeIf { it in 100..150 } ?: 100,
                compactLayout = prefs[compactKey] ?: true,
                accentColor = prefs[accentKey]?.let(AccentColorRules::opaque),
            ),
            editor = EditorSettings(
                fontSizeSp = prefs[fontSizeKey]?.takeIf { it in 9..30 } ?: 14,
                fontFamily = prefs[fontFamilyKey]?.let { enumOrNull<EditorFontFamily>(it) }
                    ?: EditorFontFamily.PlexMono,
                wordWrap = prefs[wordWrapKey] ?: true,
                autoSave = prefs[autoSaveKey]?.let { enumOrNull<AutoSaveMode>(it) }
                    ?: AutoSaveMode.AfterOneSecond,
                lineNumbers = prefs[lineNumbersKey] ?: true,
                minimap = prefs[minimapKey] ?: false,
                bracketMatching = prefs[bracketKey] ?: true,
            ),
            terminal = TerminalSettings(
                shellPath = prefs[terminalShellKey]?.takeIf(TerminalSettingRules::validShellPath) ?: "/bin/bash",
                fontSizeSp = prefs[terminalFontSizeKey]?.takeIf { it in 9..30 } ?: 13,
                fontFamily = prefs[terminalFontFamilyKey]?.let { enumOrNull<EditorFontFamily>(it) }
                    ?: EditorFontFamily.PlexMono,
                scrollbackLines = prefs[terminalScrollbackKey]
                    ?.takeIf { it in TerminalSettingRules.scrollbackChoices } ?: 10_000,
                extraKeys = prefs[terminalExtraKeysKey]?.split(',')
                    ?.mapNotNull { enumOrNull<TerminalExtraKey>(it) }
                    ?.distinct() ?: TerminalExtraKey.entries,
                cursorStyle = prefs[terminalCursorStyleKey]?.let { enumOrNull<TerminalCursorStyle>(it) }
                    ?: TerminalCursorStyle.Block,
                bellAlerts = prefs[terminalBellAlertsKey] ?: true,
                finishAlerts = prefs[terminalFinishAlertsKey] ?: true,
            ),
            globalIndentSize = prefs[globalIndentKey]?.takeIf { it in 1..8 } ?: 4,
            indentSizeOverride = indent,
            indentTypeOverride = type,
            autoFetch = prefix?.let { prefs[booleanPreferencesKey("${it}_auto_fetch")] } ?: false,
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(WorkspacePreferences(workspaceLocation = workspaceLocation, loadError = error.message ?: "Settings could not be read"))
    }

    override suspend fun setTheme(theme: AppTheme) { store.edit { it[themeKey] = theme.name } }

    override suspend fun setUiTextScalePercent(percent: Int) {
        require(percent in 100..150 && percent % 5 == 0)
        store.edit { it[uiScaleKey] = percent }
    }

    override suspend fun setCompactLayout(compact: Boolean) { store.edit { it[compactKey] = compact } }

    override suspend fun setAccentColor(argb: Int?) {
        store.edit { if (argb == null) it.remove(accentKey) else it[accentKey] = AccentColorRules.opaque(argb) }
    }

    override suspend fun setEditorFontSize(sizeSp: Int) {
        require(sizeSp in 9..30)
        store.edit { it[fontSizeKey] = sizeSp }
    }

    override suspend fun setEditorFontFamily(family: EditorFontFamily) {
        store.edit { it[fontFamilyKey] = family.name }
    }

    override suspend fun setWordWrap(enabled: Boolean) { store.edit { it[wordWrapKey] = enabled } }

    override suspend fun setAutoSave(mode: AutoSaveMode) { store.edit { it[autoSaveKey] = mode.name } }

    override suspend fun setLineNumbers(enabled: Boolean) { store.edit { it[lineNumbersKey] = enabled } }

    override suspend fun setMinimap(enabled: Boolean) { store.edit { it[minimapKey] = enabled } }

    override suspend fun setBracketMatching(enabled: Boolean) { store.edit { it[bracketKey] = enabled } }

    override suspend fun setTerminalShellPath(path: String) {
        require(TerminalSettingRules.validShellPath(path)) { "Enter an absolute guest shell path" }
        store.edit { it[terminalShellKey] = path }
    }

    override suspend fun setTerminalFontSize(sizeSp: Int) {
        require(sizeSp in 9..30)
        store.edit { it[terminalFontSizeKey] = sizeSp }
    }

    override suspend fun setTerminalFontFamily(family: EditorFontFamily) {
        store.edit { it[terminalFontFamilyKey] = family.name }
    }

    override suspend fun setTerminalScrollbackLimit(lines: Int) {
        require(lines in TerminalSettingRules.scrollbackChoices)
        store.edit { it[terminalScrollbackKey] = lines }
    }

    override suspend fun setTerminalCursorStyle(style: TerminalCursorStyle) {
        store.edit { it[terminalCursorStyleKey] = style.name }
    }

    override suspend fun setTerminalBellAlerts(enabled: Boolean) {
        store.edit { it[terminalBellAlertsKey] = enabled }
    }

    override suspend fun setTerminalFinishAlerts(enabled: Boolean) {
        store.edit { it[terminalFinishAlertsKey] = enabled }
    }

    override suspend fun setTerminalExtraKeys(keys: List<TerminalExtraKey>) {
        require(keys.distinct().size == keys.size)
        store.edit { it[terminalExtraKeysKey] = keys.joinToString(",") { key -> key.name } }
    }

    override suspend fun setGlobalIndentSize(size: Int) {
        require(size in 1..8)
        store.edit { it[globalIndentKey] = size }
    }

    override suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?) {
        require(size == null || size in 1..8)
        val key = intPreferencesKey("${workspaceKeyPrefix(workspaceLocation)}_indent_size")
        store.edit { if (size == null) it.remove(key) else it[key] = size }
    }

    override suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?) {
        val key = stringPreferencesKey("${workspaceKeyPrefix(workspaceLocation)}_indent_type")
        store.edit { if (type == null) it.remove(key) else it[key] = type.name }
    }

    override suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean) {
        val key = booleanPreferencesKey("${workspaceKeyPrefix(workspaceLocation)}_auto_fetch")
        store.edit { it[key] = enabled }
    }

    private fun workspaceKeyPrefix(location: String): String {
        require(location.isNotBlank())
        return "workspace_" + Sha256.hex(location)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }
}
