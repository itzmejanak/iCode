package dev.icode.mobile.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.icode.mobile.R

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
}

val ChakraPetch = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

object IcodeType {
    val PageTitle = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = IcodeColors.TextPrimary)
    val Title = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = IcodeColors.TextPrimary)
    val Body = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = 14.sp, color = IcodeColors.TextPrimary)
    val Label = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = IcodeColors.TextSecondary)
    val Caption = TextStyle(fontFamily = ChakraPetch, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = IcodeColors.TextSecondary)
    val Mono = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, color = IcodeColors.TextPrimary)
    val MonoSmall = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, color = IcodeColors.TextSecondary)
}

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
