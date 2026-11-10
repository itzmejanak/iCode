package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.domain.files.FileCategory
import dev.icode.mobile.ui.FileIconColours
import java.util.concurrent.ConcurrentHashMap

/** What stands for each kind of file Manage storage counts (docs/explorer-gaps-plan.md §3.10). */
internal val FileCategory.icon: ImageVector
    get() = when (this) {
        FileCategory.Images -> Icons.Outlined.Image
        FileCategory.Videos -> Icons.Outlined.Movie
        FileCategory.Audio -> Icons.Outlined.MusicNote
        FileCategory.Documents -> Icons.Outlined.Description
        FileCategory.Archives -> Icons.Outlined.Archive
        FileCategory.Apps -> Icons.Outlined.Android
        FileCategory.Code -> Icons.Outlined.Code
        FileCategory.Other -> Icons.AutoMirrored.Outlined.InsertDriveFile
    }

/** Each kind's colour on the current theme, one of Seti's: its icon's, and its share of Manage storage's bar. */
@Composable
internal fun categoryTint(category: FileCategory): Color = LocalIcodePalette.current.setiColor(
    when (category) {
        FileCategory.Images -> SetiColour.Purple
        FileCategory.Videos -> SetiColour.Pink
        FileCategory.Audio -> SetiColour.Red
        FileCategory.Documents -> SetiColour.Blue
        FileCategory.Archives -> SetiColour.Yellow
        FileCategory.Apps -> SetiColour.Green
        FileCategory.Code -> SetiColour.Orange
        FileCategory.Other -> SetiColour.Grey
    },
)

/** [seti] as this palette draws it: Seti's own colour, its lightness moved only where it would not read; Grey, the neutral text. */
internal fun IcodePalette.setiColor(seti: SetiColour): Color {
    val rgb = (if (kind == PaletteKind.Light) seti.light else seti.dark) ?: return textSecondary
    val behind = listOf(background, surface, surfaceElevated)
    // By what it is drawn on: one theme draws on other tones without border lines.
    return iconColors.getOrPut(seti to behind) {
        Oklch.readable(rgb, behind, FileIconColours.MIN_CONTRAST, darker = kind == PaletteKind.Light)
    }
}

/**
 * The colours of VS Code's file icon theme, Seti (extensions/theme-seti at
 * commit 307b60e7, MIT; from jesseweed/seti-ui), which Manage storage's
 * categories take: its colour for dark themes and its own variant for light
 * ones. Each is drawn as Seti draws it where it is at least 3 : 1 on iCode's
 * backgrounds (WCAG, graphics); where it is not — Seti's light yellow and green —
 * only its lightness moves until it is. Seti's greys are the neutral text colour.
 * The files' own icons are Material Icon Theme's ([dev.icode.mobile.ui.FileTypeIcon]).
 */
internal enum class SetiColour(val dark: Int?, val light: Int?) {
    Blue(0x519ABA, 0x498BA7), Yellow(0xCBCB41, 0xB7B73B), Orange(0xE37933, 0xCC6D2E), Red(0xCC3E44, 0xB8383D),
    Green(0x8DC149, 0x7FAE42), Purple(0xA074C4, 0x9068B0), Pink(0xF55385, 0xDD4B78),
    Grey(null, null),
}

private val iconColors = ConcurrentHashMap<Pair<SetiColour, List<Color>>, Color>()
