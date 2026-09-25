package dev.icode.mobile.designsystem

import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.icode.mobile.R

/** Bundled display face used for product chrome. */
val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Bundled monospace face used for paths, code and technical metadata. */
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/**
 * Type scale for the iCode design system.
 *
 * Screens pick a named style instead of overriding `fontSize` so the Figma
 * scale stays consistent across all 40 frames.
 */
internal val LocalIcodeUiTextScale = staticCompositionLocalOf { 1f }

@Composable
private fun scaled(size: Float) = (size * LocalIcodeUiTextScale.current).sp

object IcodeType {
    val Display: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(32f), color = IcodeColors.TextPrimary)
    val Heading: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(22f), color = IcodeColors.TextPrimary)
    val PageTitle: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(20f), color = IcodeColors.TextPrimary)
    val Title: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(16f), color = IcodeColors.TextPrimary)
    val Body: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = scaled(14f), color = IcodeColors.TextPrimary)
    val Label: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(12f), color = IcodeColors.TextSecondary)
    val Caption: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = scaled(11f), color = IcodeColors.TextSecondary)

    /** Counts drawn inside a pill, such as the rail's pending-change badge. */
    val Badge: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = scaled(10f), color = IcodeColors.TextPrimary)
    val Micro: TextStyle @Composable get() = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = scaled(10f), color = IcodeColors.TextSecondary)
    val Mono: TextStyle @Composable get() = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = scaled(13f), color = IcodeColors.TextPrimary)
    val MonoSmall: TextStyle @Composable get() = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = scaled(11f), color = IcodeColors.TextSecondary)
    val MonoMicro: TextStyle @Composable get() = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = scaled(10f), color = IcodeColors.TextSecondary)
    /** Terminal transcript has its own pinch zoom and does not inherit UI chrome scaling. */
    val TerminalBase: TextStyle @Composable get() = TextStyle(fontFamily = PlexMono, fontSize = 13.sp, color = IcodeColors.TextPrimary)
}
