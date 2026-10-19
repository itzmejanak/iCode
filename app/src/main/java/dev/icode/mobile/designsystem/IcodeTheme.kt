package dev.icode.mobile.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Material 3 bridge for the iCode design system.
 *
 * Semantic tokens live in [IcodeColors], [IcodeType], [IcodeMetrics] and
 * [IcodeTone]; this file only wires them into [MaterialTheme] so Material
 * components inherit the same palette and type.
 */
@Composable
fun IcodeTheme(
    palette: IcodePalette = IcodePalette.Dark,
    uiTextScalePercent: Int = 100,
    compactLayout: Boolean = true,
    cardBorders: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalIcodePalette provides palette,
        LocalIcodeUiTextScale provides uiTextScalePercent / 100f,
        LocalIcodeCompactLayout provides compactLayout,
        LocalIcodeCardBorders provides cardBorders,
    ) {
        val scheme = if (palette.kind == PaletteKind.Light) lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            secondary = palette.green, tertiary = palette.blue,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated, onSurfaceVariant = palette.textSecondary,
            outline = palette.outline, error = palette.error,
        ) else darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            secondary = palette.green, tertiary = palette.blue,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.surface, onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceElevated, onSurfaceVariant = palette.textSecondary,
            outline = palette.outline, error = palette.error,
        )
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography.copy(
                bodyLarge = IcodeType.Body,
                bodyMedium = IcodeType.Body,
                labelLarge = IcodeType.Label,
                titleLarge = IcodeType.PageTitle,
                titleMedium = IcodeType.Title,
            ),
            content = content,
        )
    }
}
