package dev.icode.mobile.ui

import android.content.Context
import android.content.res.Configuration
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings

/**
 * The palette [appearance] asks for while the phone is [systemDark] or not — the theme it shows then
 * (Auto follows the phone, docs/theme-variants-plan.md T6) in that theme's own variant, its border
 * lines and accent — or iCode's dark one until it is read.
 */
internal fun appearancePalette(appearance: AppearanceSettings?, systemDark: Boolean): IcodePalette {
    val theme = appearance?.shown(systemDark) ?: AppTheme.Dark
    return IcodePalette.of(theme, appearance?.variantOf(theme), appearance?.borderLines ?: true).withAccent(appearance?.accentColor)
}

/** Whether the phone is in its dark mode now, for code outside Compose (inside it, `isSystemInDarkTheme`). */
internal val Context.systemDark: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
