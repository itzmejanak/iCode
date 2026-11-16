package dev.icode.mobile.designsystem

import dev.icode.mobile.domain.AppTheme

/**
 * Every palette the app draws: each variant of each theme (docs/design-and-navigation.md), with its
 * card outlines and without (docs/design-and-navigation.md).
 */
internal val everyPalette: List<IcodePalette> =
    AppTheme.entries.flatMap { theme ->
        ThemeVariants.of(theme).flatMap { variant -> listOf(true, false).map { borderLines -> IcodePalette.of(theme, variant.id, borderLines) } }
    }
