package dev.icode.mobile.ui

import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings

/**
 * The palette [appearance] asks for — its theme in that theme's own variant, its border lines and
 * accent — or iCode's dark one until it is read.
 */
internal fun appearancePalette(appearance: AppearanceSettings?): IcodePalette {
    val theme = appearance?.theme ?: AppTheme.Dark
    return IcodePalette.of(theme, appearance?.variantOf(theme), appearance?.borderLines ?: true).withAccent(appearance?.accentColor)
}
