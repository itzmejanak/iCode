package dev.icode.mobile.designsystem

import androidx.compose.ui.text.TextStyle
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
object IcodeType {
    val Display = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = IcodeColors.TextPrimary)
    val Logomark = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = IcodeColors.Accent)
    val LogomarkSmall = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = IcodeColors.Accent)
    val Mark = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 22.sp, color = IcodeColors.TextSecondary)
    val Heading = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = IcodeColors.TextPrimary)
    val PageTitle = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = IcodeColors.TextPrimary)
    val Title = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IcodeColors.TextPrimary)
    val Body = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = IcodeColors.TextPrimary)
    val Label = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = IcodeColors.TextSecondary)
    val Caption = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = IcodeColors.TextSecondary)
    val Micro = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = IcodeColors.TextSecondary)
    val Mono = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = IcodeColors.TextPrimary)
    val MonoSmall = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = IcodeColors.TextSecondary)
    val MonoMicro = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 10.sp, color = IcodeColors.TextSecondary)
}
