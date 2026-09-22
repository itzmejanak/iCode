package dev.icode.mobile.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Material 3 bridge for the iCode design system.
 *
 * Semantic tokens live in [IcodeColors], [IcodeType], [IcodeMetrics] and
 * [IcodeTone]; this file only wires them into [MaterialTheme] so Material
 * components inherit the same palette and type.
 */
private val IcodeColorScheme = darkColorScheme(
    primary = IcodeColors.Accent,
    onPrimary = Color.Black,
    secondary = IcodeColors.Green,
    tertiary = IcodeColors.Blue,
    background = IcodeColors.Background,
    onBackground = IcodeColors.TextPrimary,
    surface = IcodeColors.Surface,
    onSurface = IcodeColors.TextPrimary,
    surfaceVariant = IcodeColors.SurfaceElevated,
    onSurfaceVariant = IcodeColors.TextSecondary,
    outline = IcodeColors.Outline,
    error = IcodeColors.Error,
)

@Composable
fun IcodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IcodeColorScheme,
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
