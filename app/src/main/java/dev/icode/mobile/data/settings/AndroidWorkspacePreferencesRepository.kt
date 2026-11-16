package dev.icode.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
import dev.icode.mobile.domain.EditorKey
import dev.icode.mobile.domain.EditorSettings
import dev.icode.mobile.domain.FloatingPill
import dev.icode.mobile.domain.PillPlace
import dev.icode.mobile.domain.PillPlaces
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.domain.TerminalCursorStyle
import dev.icode.mobile.domain.TerminalExtraKey
import dev.icode.mobile.domain.TerminalSettingRules
import dev.icode.mobile.domain.ThemeVariantRules
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.domain.WorkspacePreferencesRepository
import dev.icode.mobile.domain.files.ExplorerSettings
import dev.icode.mobile.domain.files.ExplorerView
import dev.icode.mobile.domain.files.FilesLook
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
    // A value missing or out of range reads as the settings types' own default.
    private val editorDefaults = EditorSettings()
    private val terminalDefaults = TerminalSettings()
    private val preferenceDefaults = WorkspacePreferences()
    private val globalIndentKey = intPreferencesKey("editor_indent_size")
    private val fontSizeKey = intPreferencesKey("editor_font_size_sp")
    private val fontFamilyKey = stringPreferencesKey("editor_font_family")
    private val wordWrapKey = booleanPreferencesKey("editor_word_wrap")
    private val autoSaveKey = stringPreferencesKey("editor_auto_save")
    private val lineNumbersKey = booleanPreferencesKey("editor_line_numbers")
    private val minimapKey = booleanPreferencesKey("editor_minimap")
    private val bracketKey = booleanPreferencesKey("editor_bracket_matching")
    private val editorKeysKey = stringPreferencesKey("editor_keys")
    private val keyboardSuggestionsKey = booleanPreferencesKey("editor_keyboard_suggestions")
    private val changeMarkersKey = booleanPreferencesKey("editor_change_markers")
    private val autoCloseBracketsKey = booleanPreferencesKey("editor_auto_close_brackets")
    private val semanticHighlightingKey = booleanPreferencesKey("editor_semantic_highlighting")
    private val terminalFontSizeKey = intPreferencesKey("terminal_font_size_sp")
    private val terminalFontFamilyKey = stringPreferencesKey("terminal_font_family")
    private val terminalScrollbackKey = intPreferencesKey("terminal_scrollback_lines")
    private val terminalExtraKeysKey = stringPreferencesKey("terminal_extra_keys")
    private val terminalCursorStyleKey = stringPreferencesKey("terminal_cursor_style")
    private val terminalBellAlertsKey = booleanPreferencesKey("terminal_bell_alerts")
    private val terminalFinishAlertsKey = booleanPreferencesKey("terminal_finish_alerts")
    private val explorerDefaults = ExplorerSettings()
    private val showHiddenKey = booleanPreferencesKey("explorer_show_hidden")
    private val foldersLookKey = stringPreferencesKey("explorer_folders_look")

    override fun values(workspaceLocation: String?): Flow<WorkspacePreferences> = store.data.map { prefs ->
        val indent = workspaceLocation?.let { prefs[indentSizeKey(it)] }?.takeIf { it in SettingLimits.indentSizes }
        val type = workspaceLocation?.let { prefs[indentTypeKey(it)] }
            ?.let { runCatching { IndentType.valueOf(it) }.getOrNull() }
        WorkspacePreferences(
            workspaceLocation = workspaceLocation,
            appearance = AppearanceStore.read(prefs),
            editor = EditorSettings(
                fontSizeSp = prefs[fontSizeKey]?.takeIf { it in SettingLimits.fontSizesSp } ?: editorDefaults.fontSizeSp,
                fontFamily = prefs[fontFamilyKey]?.let { enumOrNull<EditorFontFamily>(it) } ?: editorDefaults.fontFamily,
                wordWrap = prefs[wordWrapKey] ?: editorDefaults.wordWrap,
                autoSave = prefs[autoSaveKey]?.let { enumOrNull<AutoSaveMode>(it) } ?: editorDefaults.autoSave,
                lineNumbers = prefs[lineNumbersKey] ?: editorDefaults.lineNumbers,
                minimap = prefs[minimapKey] ?: editorDefaults.minimap,
                bracketMatching = prefs[bracketKey] ?: editorDefaults.bracketMatching,
                keys = prefs[editorKeysKey]?.split(',')?.mapNotNull { enumOrNull<EditorKey>(it) }?.distinct() ?: editorDefaults.keys,
                keyboardSuggestions = prefs[keyboardSuggestionsKey] ?: editorDefaults.keyboardSuggestions,
                changeMarkers = prefs[changeMarkersKey] ?: editorDefaults.changeMarkers,
                autoCloseBrackets = prefs[autoCloseBracketsKey] ?: editorDefaults.autoCloseBrackets,
                semanticHighlighting = prefs[semanticHighlightingKey] ?: editorDefaults.semanticHighlighting,
            ),
            terminal = TerminalSettings(
                fontSizeSp = prefs[terminalFontSizeKey]?.takeIf { it in SettingLimits.fontSizesSp } ?: terminalDefaults.fontSizeSp,
                fontFamily = prefs[terminalFontFamilyKey]?.let { enumOrNull<EditorFontFamily>(it) } ?: terminalDefaults.fontFamily,
                scrollbackLines = prefs[terminalScrollbackKey]
                    ?.takeIf { it in TerminalSettingRules.scrollbackChoices } ?: terminalDefaults.scrollbackLines,
                extraKeys = prefs[terminalExtraKeysKey]?.split(',')
                    ?.mapNotNull { enumOrNull<TerminalExtraKey>(it) }
                    ?.distinct() ?: terminalDefaults.extraKeys,
                cursorStyle = prefs[terminalCursorStyleKey]?.let { enumOrNull<TerminalCursorStyle>(it) }
                    ?: terminalDefaults.cursorStyle,
                bellAlerts = prefs[terminalBellAlertsKey] ?: terminalDefaults.bellAlerts,
                finishAlerts = prefs[terminalFinishAlertsKey] ?: terminalDefaults.finishAlerts,
            ),
            explorer = ExplorerSettings(
                showHidden = prefs[showHiddenKey] ?: explorerDefaults.showHidden,
                folders = prefs[foldersLookKey]?.let(FilesLook::decode) ?: explorerDefaults.folders,
                views = prefs.asMap().mapNotNull { (key, value) ->
                    val view = key.name.removePrefix(VIEW_LOOK_PREFIX).takeIf { it != key.name } ?: return@mapNotNull null
                    (value as? String)?.let(FilesLook::decode)?.let { view to it }
                }.toMap(),
            ),
            globalIndentSize = prefs[globalIndentKey]?.takeIf { it in SettingLimits.indentSizes } ?: preferenceDefaults.globalIndentSize,
            indentSizeOverride = indent,
            indentTypeOverride = type,
            autoFetch = workspaceLocation?.let { prefs[autoFetchKey(it)] } ?: preferenceDefaults.autoFetch,
        )
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(WorkspacePreferences(workspaceLocation = workspaceLocation, loadError = error.message ?: "Settings could not be read"))
    }

    override suspend fun setTheme(theme: AppTheme) { store.edit { it[AppearanceStore.theme] = theme.name } }

    override suspend fun setUiTextScalePercent(percent: Int) {
        require(percent in SettingLimits.uiTextScales)
        store.edit { it[AppearanceStore.uiScale] = percent }
    }

    override suspend fun setCompactLayout(compact: Boolean) { store.edit { it[AppearanceStore.compact] = compact } }
    override suspend fun setBorderLines(shown: Boolean) { store.edit { it[AppearanceStore.borderLines] = shown } }

    override suspend fun setAccentColor(argb: Int?) {
        store.edit { if (argb == null) it.remove(AppearanceStore.accent) else it[AppearanceStore.accent] = AccentColorRules.opaque(argb) }
    }

    override suspend fun setThemeVariant(theme: AppTheme, id: String?) {
        require(id == null || ThemeVariantRules.isId(id))
        val kept = ThemeVariantRules.kept(id)
        val key = AppearanceStore.variant(theme)
        store.edit { if (kept == null) it.remove(key) else it[key] = kept }
    }

    override suspend fun setFollowSystem(theme: AppTheme, follow: Boolean) {
        store.edit { prefs ->
            prefs[AppearanceStore.followSystem] = follow
            // Turned on, the dark theme in use is the one for nights; turned off, the theme on screen stays.
            if (follow && AppearanceSettings.canBeNight(theme)) prefs[AppearanceStore.nightTheme] = theme.name
            if (!follow) prefs[AppearanceStore.theme] = theme.name
        }
    }

    override suspend fun setNightTheme(theme: AppTheme) {
        require(AppearanceSettings.canBeNight(theme))
        store.edit { it[AppearanceStore.nightTheme] = theme.name }
    }

    override suspend fun setPillPlace(pill: FloatingPill, place: PillPlace?) {
        store.edit { prefs ->
            val places = PillPlaces.decoded(prefs[AppearanceStore.pillPlaces]).let { if (place == null) it - pill else it + (pill to place) }
            if (places.isEmpty()) prefs.remove(AppearanceStore.pillPlaces) else prefs[AppearanceStore.pillPlaces] = PillPlaces.encoded(places)
        }
    }

    override suspend fun setEditorFontSize(sizeSp: Int) {
        require(sizeSp in SettingLimits.fontSizesSp)
        store.edit { it[fontSizeKey] = sizeSp }
    }

    override suspend fun setEditorFontFamily(family: EditorFontFamily) {
        store.edit { it[fontFamilyKey] = family.name }
    }

    override suspend fun setWordWrap(enabled: Boolean) { store.edit { it[wordWrapKey] = enabled } }

    override suspend fun setAutoSave(mode: AutoSaveMode) { store.edit { it[autoSaveKey] = mode.name } }

    override suspend fun setEditorKeys(keys: List<EditorKey>) {
        require(keys.distinct().size == keys.size)
        store.edit { it[editorKeysKey] = keys.joinToString(",") { key -> key.name } }
    }

    override suspend fun setKeyboardSuggestions(enabled: Boolean) { store.edit { it[keyboardSuggestionsKey] = enabled } }

    override suspend fun setChangeMarkers(enabled: Boolean) { store.edit { it[changeMarkersKey] = enabled } }

    override suspend fun setAutoCloseBrackets(enabled: Boolean) { store.edit { it[autoCloseBracketsKey] = enabled } }

    override suspend fun setSemanticHighlighting(enabled: Boolean) { store.edit { it[semanticHighlightingKey] = enabled } }

    override suspend fun setLineNumbers(enabled: Boolean) { store.edit { it[lineNumbersKey] = enabled } }

    override suspend fun setMinimap(enabled: Boolean) { store.edit { it[minimapKey] = enabled } }

    override suspend fun setBracketMatching(enabled: Boolean) { store.edit { it[bracketKey] = enabled } }

    override suspend fun setTerminalFontSize(sizeSp: Int) {
        require(sizeSp in SettingLimits.fontSizesSp)
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

    override suspend fun setShowHidden(shown: Boolean) { store.edit { it[showHiddenKey] = shown } }

    override suspend fun setFilesLook(view: ExplorerView?, look: FilesLook) {
        store.edit { it[if (view == null) foldersLookKey else stringPreferencesKey(VIEW_LOOK_PREFIX + view.key)] = look.encoded }
    }

    override suspend fun setGlobalIndentSize(size: Int) {
        require(size in SettingLimits.indentSizes)
        store.edit { it[globalIndentKey] = size }
    }

    override suspend fun setIndentSizeOverride(workspaceLocation: String, size: Int?) {
        require(size == null || size in SettingLimits.indentSizes)
        val key = indentSizeKey(workspaceLocation)
        store.edit { if (size == null) it.remove(key) else it[key] = size }
    }

    override suspend fun setIndentTypeOverride(workspaceLocation: String, type: IndentType?) {
        val key = indentTypeKey(workspaceLocation)
        store.edit { if (type == null) it.remove(key) else it[key] = type.name }
    }

    override suspend fun setAutoFetch(workspaceLocation: String, enabled: Boolean) {
        store.edit { it[autoFetchKey(workspaceLocation)] = enabled }
    }

    /** A workspace renamed: its overrides follow it to [to] (docs/workspaces-and-files.md). */
    suspend fun moveWorkspace(from: String, to: String) {
        store.edit { prefs ->
            prefs.move(indentSizeKey(from), indentSizeKey(to))
            prefs.move(indentTypeKey(from), indentTypeKey(to))
            prefs.move(autoFetchKey(from), autoFetchKey(to))
        }
    }

    /** A workspace deleted, or one iCode no longer opens: its overrides go. */
    suspend fun forgetWorkspace(location: String) {
        store.edit { prefs ->
            prefs.remove(indentSizeKey(location))
            prefs.remove(indentTypeKey(location))
            prefs.remove(autoFetchKey(location))
        }
    }

    private fun <T> MutablePreferences.move(from: Preferences.Key<T>, to: Preferences.Key<T>) {
        val value = this[from] ?: return
        this[to] = value
        remove(from)
    }

    // A workspace's own keys; stored, do not rename.
    private fun indentSizeKey(location: String) = intPreferencesKey("${workspaceKeyPrefix(location)}_indent_size")
    private fun indentTypeKey(location: String) = stringPreferencesKey("${workspaceKeyPrefix(location)}_indent_type")
    private fun autoFetchKey(location: String) = booleanPreferencesKey("${workspaceKeyPrefix(location)}_auto_fetch")

    private fun workspaceKeyPrefix(location: String): String {
        require(location.isNotBlank())
        return "workspace_" + Sha256.hex(location)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private companion object {
        /** A view's own look, by its key; stored, do not rename. */
        const val VIEW_LOOK_PREFIX = "explorer_look_"
    }
}
