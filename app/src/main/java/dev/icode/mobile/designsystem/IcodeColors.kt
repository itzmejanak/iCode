package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens for the iCode design system.
 *
 * Screens must reference these tokens instead of literal [Color] values;
 * `tools/check-ui-consistency.sh` enforces that no `Color(0x…)` literal
 * exists outside this file.
 */
object IcodeColors {
    val Background = Color(0xFF0A0D0B)
    val BackgroundDeep = Color(0xFF060807)
    val Surface = Color(0xFF131812)
    val SurfaceElevated = Color(0xFF1C231B)
    val SurfaceTrack = Color(0xFF232B22)
    val Outline = Color(0xFF2A332A)
    val Accent = Color(0xFFFF6B2C)
    val Amber = Color(0xFFFFC24B)
    val Green = Color(0xFF43D9A3)
    val Blue = Color(0xFF6AA9FF)
    val Error = Color(0xFFFF5449)
    val TextPrimary = Color(0xFFE9EFE7)
    val TextSecondary = Color(0xFF9AA69A)
    val TextMuted = Color(0xFF5F6C60)

    /** Content drawn on top of [Accent] or another saturated fill. */
    val OnAccent = Color.Black

    /** Diff surfaces for added and removed lines. */
    val DiffAddedSurface = Color(0xFF182E22)
    val DiffRemovedSurface = Color(0xFF3F1E1B)

    /** Conflict surfaces for the current and incoming merge side. */
    val ConflictCurrentSurface = Color(0xFF3F1E1B)
    val ConflictIncomingSurface = Color(0xFF172738)

    /** Backdrop drawn behind dialogs and modal sheets. */
    val Scrim = BackgroundDeep.copy(alpha = 0.95f)
}
