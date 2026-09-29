package dev.icode.mobile.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Which theme a palette is, however its accent was changed. */
enum class PaletteKind { Dark, Light, Amoled }

/** One complete semantic palette. Light and AMOLED remain review candidates. */
@Immutable
data class IcodePalette(
    val kind: PaletteKind,
    val background: Color,
    val backgroundDeep: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceTrack: Color,
    val outline: Color,
    val accent: Color,
    val amber: Color,
    val green: Color,
    val blue: Color,
    val error: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val onAccent: Color,
    val diffAddedSurface: Color,
    val diffRemovedSurface: Color,
    val conflictCurrentSurface: Color,
    val conflictIncomingSurface: Color,
) {
    val scrim: Color get() = backgroundDeep.copy(alpha = 0.95f)

    companion object {
        val Dark = IcodePalette(
            kind = PaletteKind.Dark,
            background = Color(0xFF0A0D0B), backgroundDeep = Color(0xFF060807),
            surface = Color(0xFF131812), surfaceElevated = Color(0xFF1C231B),
            surfaceTrack = Color(0xFF232B22), outline = Color(0xFF2A332A),
            accent = Color(0xFFFF6B2C), amber = Color(0xFFFFC24B),
            green = Color(0xFF43D9A3), blue = Color(0xFF6AA9FF), error = Color(0xFFFF5449),
            textPrimary = Color(0xFFE9EFE7), textSecondary = Color(0xFF9AA69A),
            textMuted = Color(0xFF5F6C60), onAccent = Color.Black,
            diffAddedSurface = Color(0xFF182E22), diffRemovedSurface = Color(0xFF3F1E1B),
            conflictCurrentSurface = Color(0xFF3F1E1B), conflictIncomingSurface = Color(0xFF172738),
        )

        /** Proposed light palette; user review remains pending. */
        val Light = IcodePalette(
            kind = PaletteKind.Light,
            background = Color(0xFFF5F7F3), backgroundDeep = Color(0xFFECEFE9),
            surface = Color.White, surfaceElevated = Color(0xFFE7EBE5),
            surfaceTrack = Color(0xFFDCE3DA), outline = Color(0xFF758575),
            accent = Color(0xFFB4470C), amber = Color(0xFF7A5100),
            green = Color(0xFF106B4B), blue = Color(0xFF235B9B), error = Color(0xFFB42318),
            textPrimary = Color(0xFF172018), textSecondary = Color(0xFF425247),
            textMuted = Color(0xFF647467), onAccent = Color.White,
            diffAddedSurface = Color(0xFFE7F4EB), diffRemovedSurface = Color(0xFFFBEAE7),
            conflictCurrentSurface = Color(0xFFFBEAE7), conflictIncomingSurface = Color(0xFFE7F0FB),
        )

        /** Proposed OLED-black palette; user review remains pending. */
        val Amoled = IcodePalette(
            kind = PaletteKind.Amoled,
            background = Color.Black, backgroundDeep = Color.Black,
            surface = Color(0xFF0B0B0B), surfaceElevated = Color(0xFF151915),
            surfaceTrack = Color(0xFF1E241D), outline = Color(0xFF647264),
            accent = Color(0xFFFF6B2C), amber = Color(0xFFFFC24B),
            green = Color(0xFF43D9A3), blue = Color(0xFF6AA9FF), error = Color(0xFFFF5449),
            textPrimary = Color(0xFFF2F6F0), textSecondary = Color(0xFFAAB5A8),
            textMuted = Color(0xFF8B9B8B), onAccent = Color.Black,
            diffAddedSurface = Color(0xFF102318), diffRemovedSurface = Color(0xFF2B1110),
            conflictCurrentSurface = Color(0xFF2B1110), conflictIncomingSurface = Color(0xFF101D2E),
        )
    }
}

internal val LocalIcodePalette = staticCompositionLocalOf { IcodePalette.Dark }

/** Semantic color access for Compose kit and screens; raw literals remain in this file only. */
object IcodeColors {
    val Background: Color @Composable get() = LocalIcodePalette.current.background
    val BackgroundDeep: Color @Composable get() = LocalIcodePalette.current.backgroundDeep
    val Surface: Color @Composable get() = LocalIcodePalette.current.surface
    val SurfaceElevated: Color @Composable get() = LocalIcodePalette.current.surfaceElevated
    val SurfaceTrack: Color @Composable get() = LocalIcodePalette.current.surfaceTrack
    val Outline: Color @Composable get() = LocalIcodePalette.current.outline
    val Accent: Color @Composable get() = LocalIcodePalette.current.accent
    val Amber: Color @Composable get() = LocalIcodePalette.current.amber
    val Green: Color @Composable get() = LocalIcodePalette.current.green
    val Blue: Color @Composable get() = LocalIcodePalette.current.blue
    val Error: Color @Composable get() = LocalIcodePalette.current.error
    val TextPrimary: Color @Composable get() = LocalIcodePalette.current.textPrimary
    val TextSecondary: Color @Composable get() = LocalIcodePalette.current.textSecondary
    val TextMuted: Color @Composable get() = LocalIcodePalette.current.textMuted
    val OnAccent: Color @Composable get() = LocalIcodePalette.current.onAccent
    val DiffAddedSurface: Color @Composable get() = LocalIcodePalette.current.diffAddedSurface
    val DiffRemovedSurface: Color @Composable get() = LocalIcodePalette.current.diffRemovedSurface
    val ConflictCurrentSurface: Color @Composable get() = LocalIcodePalette.current.conflictCurrentSurface
    val ConflictIncomingSurface: Color @Composable get() = LocalIcodePalette.current.conflictIncomingSurface
    val Scrim: Color @Composable get() = LocalIcodePalette.current.scrim
}

/**
 * The sixteen ANSI colors terminal programs choose from, for this palette:
 * normal 0–7, then bright 8–15. Background, text and cursor come from the
 * palette itself. On Light, "white" is a grey, as light terminal themes do, so
 * white text stays readable on the light background.
 */
val IcodePalette.terminalAnsi: List<Color>
    get() = if (kind == PaletteKind.Light) LightTerminalAnsi else DarkTerminalAnsi

private val DarkTerminalAnsi = listOf(
    Color(0xFF1C231B), Color(0xFFFF5449), Color(0xFF43D9A3), Color(0xFFFFC24B),
    Color(0xFF6AA9FF), Color(0xFFD68CFF), Color(0xFF4FD6E0), Color(0xFFC9D2C7),
    Color(0xFF5F6C60), Color(0xFFFF7A70), Color(0xFF6FE8BC), Color(0xFFFFD27A),
    Color(0xFF93C1FF), Color(0xFFE4AEFF), Color(0xFF7FE6EE), Color(0xFFF2F6F0),
)

private val LightTerminalAnsi = listOf(
    Color(0xFF172018), Color(0xFFB42318), Color(0xFF106B4B), Color(0xFF7A5100),
    Color(0xFF235B9B), Color(0xFF8A3FA8), Color(0xFF0B6E7A), Color(0xFF5A675C),
    Color(0xFF425247), Color(0xFFD1402F), Color(0xFF1B8A62), Color(0xFF9A6A00),
    Color(0xFF2F72BF), Color(0xFFA457C4), Color(0xFF168A98), Color(0xFF8E9A8F),
)
