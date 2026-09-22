package dev.icode.mobile.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Semantic tone used by feedback surfaces (banners, diagnostics, status rows).
 *
 * Screens map domain state (severity, Git status, health) onto a tone, so a
 * single tint mapping exists for the whole product.
 */
enum class IcodeTone { Neutral, Accent, Info, Success, Warning, Error }

/** Foreground tint for a tone. */
fun IcodeTone.tint(): Color = when (this) {
    IcodeTone.Neutral -> IcodeColors.TextSecondary
    IcodeTone.Accent -> IcodeColors.Accent
    IcodeTone.Info -> IcodeColors.Blue
    IcodeTone.Success -> IcodeColors.Green
    IcodeTone.Warning -> IcodeColors.Amber
    IcodeTone.Error -> IcodeColors.Error
}
