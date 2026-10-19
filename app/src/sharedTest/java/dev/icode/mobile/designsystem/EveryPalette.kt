package dev.icode.mobile.designsystem

import dev.icode.mobile.domain.AppTheme

/** Every palette the app draws: each theme with its card outlines and without (docs/card-borders-plan.md). */
internal val everyPalette: List<IcodePalette> =
    AppTheme.entries.flatMap { theme -> listOf(true, false).map { cardBorders -> IcodePalette.of(theme, cardBorders) } }
