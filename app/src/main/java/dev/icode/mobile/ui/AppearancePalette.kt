package dev.icode.mobile.ui

import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.withAccent
import dev.icode.mobile.domain.AppTheme
import dev.icode.mobile.domain.AppearanceSettings

/** The palette [appearance] asks for — its theme, border lines and accent — or the dark one until it is read. */
internal fun appearancePalette(appearance: AppearanceSettings?): IcodePalette =
    IcodePalette.of(appearance?.theme ?: AppTheme.Dark, appearance?.borderLines ?: true).withAccent(appearance?.accentColor)
