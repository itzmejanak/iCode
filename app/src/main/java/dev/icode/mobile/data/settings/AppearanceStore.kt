package dev.icode.mobile.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.icode.mobile.domain.AccentColorRules
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.ThemeVariantRules

/**
 * The appearance settings' keys in the workspace_settings store, and how a stored set reads: a value
 * missing or not one the setting takes reads as its default, the others kept. Stored: do not rename.
 */
internal object AppearanceStore {
    private val defaults = AppearanceSettings()
    val theme = stringPreferencesKey("appearance_theme")
    val uiScale = intPreferencesKey("appearance_ui_text_scale_percent")
    val compact = booleanPreferencesKey("appearance_compact_layout")
    val borderLines = booleanPreferencesKey("appearance_border_lines")
    val accent = intPreferencesKey("appearance_accent_color")
    /** Auto (docs/theme-variants-plan.md T6). */
    val followSystem = booleanPreferencesKey("appearance_follow_system")
    val nightTheme = stringPreferencesKey("appearance_night_theme")
    private val darkVariant = stringPreferencesKey("appearance_dark_variant")
    private val lightVariant = stringPreferencesKey("appearance_light_variant")
    private val amoledVariant = stringPreferencesKey("appearance_amoled_variant")

    /** [theme]'s own variant (D1). */
    fun variant(theme: AppTheme) = when (theme) {
        AppTheme.Dark -> darkVariant
        AppTheme.Light -> lightVariant
        AppTheme.Amoled -> amoledVariant
    }

    fun read(prefs: Preferences) = AppearanceSettings(
        theme = prefs[theme]?.let(::themeNamed) ?: defaults.theme,
        uiTextScalePercent = prefs[uiScale]?.takeIf { it in SettingLimits.uiTextScales } ?: defaults.uiTextScalePercent,
        compactLayout = prefs[compact] ?: defaults.compactLayout,
        borderLines = prefs[borderLines] ?: defaults.borderLines,
        accentColor = prefs[accent]?.let(AccentColorRules::opaque),
        variants = AppTheme.entries.mapNotNull { each -> ThemeVariantRules.kept(prefs[variant(each)])?.let { each to it } }.toMap(),
        followSystem = prefs[followSystem] ?: defaults.followSystem,
        nightTheme = prefs[nightTheme]?.let(::themeNamed)?.takeIf { AppearanceSettings.canBeNight(it) } ?: defaults.nightTheme,
    )

    private fun themeNamed(name: String): AppTheme? = AppTheme.entries.firstOrNull { it.name == name }
}
