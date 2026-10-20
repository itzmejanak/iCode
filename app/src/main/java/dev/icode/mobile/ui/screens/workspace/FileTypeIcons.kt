package dev.icode.mobile.ui.screens.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.domain.FileFamily
import dev.icode.mobile.domain.FileType
import java.util.concurrent.ConcurrentHashMap

/** The icon a file's type draws with, in the Explorer and in search results alike. */
internal fun fileTypeIcon(type: FileType): ImageVector = when (type.family) {
    FileFamily.Code -> Icons.Outlined.Code
    FileFamily.Data, FileFamily.Document -> Icons.Outlined.Description
}

/** That icon's colour on the current theme. */
@Composable
internal fun fileTypeTint(type: FileType): Color = LocalIcodePalette.current.fileTypeColor(type)

/** [fileTypeTint] for a given palette. */
internal fun IcodePalette.fileTypeColor(type: FileType): Color {
    val seti = fileTypeColours.getValue(type.id)
    val rgb = (if (kind == PaletteKind.Light) seti.light else seti.dark) ?: return textSecondary
    val behind = listOf(background, surface, surfaceElevated)
    // By what it is drawn on: one theme draws on other tones without border lines.
    return iconColors.getOrPut(seti to behind) {
        Oklch.readable(rgb, behind, MIN_ICON_CONTRAST, darker = kind == PaletteKind.Light)
    }
}

/**
 * The colours of VS Code's file icon theme, Seti (extensions/theme-seti at
 * commit 307b60e7, MIT; from jesseweed/seti-ui): its colour for dark themes
 * and its own variant for light ones. Each is drawn as Seti draws it where it
 * is at least 3 : 1 on iCode's backgrounds (WCAG, graphics); where it is not —
 * Seti's light yellow and green — only its lightness moves until it is. Seti's
 * greys and its default file icon are the neutral text colour.
 */
internal enum class SetiColour(val dark: Int?, val light: Int?) {
    Blue(0x519ABA, 0x498BA7), Yellow(0xCBCB41, 0xB7B73B), Orange(0xE37933, 0xCC6D2E), Red(0xCC3E44, 0xB8383D),
    Green(0x8DC149, 0x7FAE42), Purple(0xA074C4, 0x9068B0), Pink(0xF55385, 0xDD4B78),
    Grey(null, null),
}

private val iconColors = ConcurrentHashMap<Pair<SetiColour, List<Color>>, Color>()
private const val MIN_ICON_CONTRAST = 3.0

/** Each file type's colour, as Seti gives its language (`languageIds`), or Seti's default where it lists none. */
internal val fileTypeColours: Map<String, SetiColour> = mapOf(
        "kotlin" to SetiColour.Orange,
        "java" to SetiColour.Red,
        "python" to SetiColour.Blue,
        "typescript" to SetiColour.Blue,
        "javascript" to SetiColour.Yellow,
        "javascriptreact" to SetiColour.Blue,
        "typescriptreact" to SetiColour.Blue,
        "html" to SetiColour.Orange,
        "xml" to SetiColour.Orange,
        "xsl" to SetiColour.Grey,
        "css" to SetiColour.Blue,
        "scss" to SetiColour.Pink,
        "less" to SetiColour.Blue,
        "shell" to SetiColour.Green,
        "bat" to SetiColour.Blue,
        "rust" to SetiColour.Grey,
        "go" to SetiColour.Blue,
        "c" to SetiColour.Blue,
        "cpp" to SetiColour.Blue,
        "csharp" to SetiColour.Blue,
        "php" to SetiColour.Purple,
        "ruby" to SetiColour.Red,
        "swift" to SetiColour.Orange,
        "dart" to SetiColour.Blue,
        "lua" to SetiColour.Blue,
        "groovy" to SetiColour.Green,
        "sql" to SetiColour.Pink,
        "dockerfile" to SetiColour.Blue,
        "makefile" to SetiColour.Orange,
        "json" to SetiColour.Yellow,
        "jsonl" to SetiColour.Yellow,
        "json5" to SetiColour.Grey,
        "yaml" to SetiColour.Purple,
        "ini" to SetiColour.Grey,
        "properties" to SetiColour.Grey,
        "dotenv" to SetiColour.Grey,
        "ignore" to SetiColour.Grey,
        "markdown" to SetiColour.Blue,
        "diff" to SetiColour.Grey,
        "git-commit" to SetiColour.Grey,
        "git-rebase" to SetiColour.Grey,
        "plaintext" to SetiColour.Grey,
)
