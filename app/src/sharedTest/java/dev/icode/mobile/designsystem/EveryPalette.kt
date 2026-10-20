package dev.icode.mobile.designsystem

import dev.icode.mobile.domain.AppTheme

/** Every palette the app draws: each theme with its card outlines and without (docs/border-lines-plan.md). */
internal val everyPalette: List<IcodePalette> =
    AppTheme.entries.flatMap { theme -> listOf(true, false).map { borderLines -> IcodePalette.of(theme, borderLines) } }
