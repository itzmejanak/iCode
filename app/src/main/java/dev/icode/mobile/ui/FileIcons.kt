package dev.icode.mobile.ui

import android.content.Context
import android.graphics.Picture
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.caverock.androidsvg.SVG
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.IcodeSizes
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.designsystem.Oklch
import dev.icode.mobile.designsystem.PaletteKind
import dev.icode.mobile.domain.FileType
import dev.icode.mobile.domain.files.FileIconTheme
import java.util.concurrent.ConcurrentHashMap

/**
 * A file's or a folder's icon (docs/file-kinds-icons-plan.md D8): the one drawing of them, wherever a
 * file is listed or its sheet is open. [parent] is the name of the folder it is in, when known; [type]
 * what its bytes said it is, where a scan read them; [open] a folder expanded.
 */
@Composable
internal fun FileTypeIcon(
    name: String,
    folder: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = IcodeSizes.IconMd,
    open: Boolean = false,
    parent: String? = null,
    type: FileType? = null,
) {
    val context = LocalContext.current.applicationContext
    val palette = LocalIcodePalette.current
    val light = palette.kind == PaletteKind.Light
    val icon = remember(name, folder, open, parent, type, light) {
        val theme = FileIcons.theme(context)
        if (folder) theme.folderIcon(name, parent, open, light) else theme.fileIcon(name, parent, type, light)
    }
    val painter = remember(icon, palette) { FileIcons.painter(context, icon, palette) }
    Image(painter, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Material Icon Theme as iCode draws it (D5–D7): its data read once — off the main thread from app
 * start ([warm]) — and each icon parsed once by AndroidSVG into a [Picture] per palette, its colours
 * made to read on that palette ([FileIconColours]), the default folder in the accent.
 */
internal object FileIcons {
    @Volatile
    private var theme: FileIconTheme? = null
    private val sources = ConcurrentHashMap<String, String>()
    private val drawn = ConcurrentHashMap<IcodePalette, ConcurrentHashMap<String, Painter>>()

    fun theme(context: Context): FileIconTheme = theme ?: synchronized(this) {
        theme ?: FileIconTheme.parse(context.assets.open(THEME).bufferedReader().use { it.readText() }).also { theme = it }
    }

    /** The theme and the icons every list shows, read ahead so the first list draws without waiting. */
    fun warm(context: Context) {
        val theme = runCatching { theme(context) }.getOrNull() ?: return
        listOfNotNull(theme.file, theme.folder, theme.folderIcon("", open = true)).forEach { runCatching { source(context, it) } }
    }

    /** [icon] on [palette]; the default file's where it cannot be drawn, and nothing at all past that. */
    fun painter(context: Context, icon: String, palette: IcodePalette): Painter {
        val made = drawn.getOrPut(palette) { ConcurrentHashMap() }
        made[icon]?.let { return it }
        val theme = theme(context)
        val painter = runCatching { make(context, icon, palette, theme) }
            .recoverCatching { make(context, theme.file, palette, theme) }
            .getOrElse { ColorPainter(Color.Transparent) }
        made[icon] = painter
        return painter
    }

    private fun make(context: Context, icon: String, palette: IcodePalette, theme: FileIconTheme): Painter {
        val accent = icon == theme.folder || icon == theme.folderIcon("", open = true)
        val svg = SVG.getFromString(FileIconColours.drawn(source(context, icon), palette, accent))
        return PicturePainter(svg.renderToPicture(PICTURE_SIDE, PICTURE_SIDE))
    }

    private fun source(context: Context, icon: String): String =
        sources.getOrPut(icon) { context.assets.open("$ICONS/$icon.svg").bufferedReader().use { it.readText() } }

    private const val THEME = "file-icons/theme.json"
    private const val ICONS = "file-icons/icons"

    /** The side, in pixels, an icon is recorded at: a picture is drawn at any size from it. */
    private const val PICTURE_SIDE = 96
}

/** The name of the folder [location] is in, for the icons a theme gives a folder's own files (`.github/workflows`); null at the top. */
internal fun parentName(location: String): String? = location.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { null }

/** An SVG icon's colours as a palette draws them (D7): each of them, moved together until the icon reads. */
internal object FileIconColours {
    /** [svg] with its colours readable on [palette]; with the [accent] in place of every one of them, the default folder's. */
    fun drawn(svg: String, palette: IcodePalette, accent: Boolean = false): String {
        val found = COLOUR.findAll(svg).map { it.value.lowercase() }.distinct().toList()
        if (found.isEmpty()) return svg
        val wanted = if (accent) found.map { palette.accent.toArgb() and RGB } else found.map(::rgbOf)
        val drawn = Oklch.readableTogether(wanted, listOf(palette.background, palette.surface, palette.surfaceElevated), MIN_CONTRAST, darker = palette.kind == PaletteKind.Light)
            .map { it.toArgb() and RGB }
        if (!accent && drawn == wanted) return svg
        val by = found.zip(drawn.map { "#%06x".format(it) }).toMap()
        return COLOUR.replace(svg) { by.getValue(it.value.lowercase()) }
    }

    /** The colours [svg] is drawn in, as `0xRRGGBB`. */
    fun colours(svg: String): List<Int> = COLOUR.findAll(svg).map { rgbOf(it.value.lowercase()) }.distinct().toList()

    private fun rgbOf(hex: String): Int {
        val digits = hex.removePrefix("#")
        val full = if (digits.length == SHORT) digits.map { "$it$it" }.joinToString("") else digits
        return full.toInt(HEX)
    }

    /** A colour an attribute paints with: `fill`, `stroke`, a gradient's `stop-color`, `color`. Never an id (`url(#abc)`, `href="#bad"`). */
    private val COLOUR = Regex("""(?<=\b(?:fill|stroke|stop-color|color)=")#(?:[0-9a-fA-F]{6}|[0-9a-fA-F]{3})(?=")""")
    private const val RGB = 0xFFFFFF
    private const val SHORT = 3
    private const val HEX = 16

    /** WCAG's minimum for graphics, as every icon colour in iCode has held. */
    const val MIN_CONTRAST = 3.0
}

/** A recorded drawing, scaled to whatever size it is drawn at. */
private class PicturePainter(private val picture: Picture) : Painter() {
    override val intrinsicSize: Size = Size(picture.width.toFloat(), picture.height.toFloat())

    override fun DrawScope.onDraw() {
        drawIntoCanvas { it.nativeCanvas.drawPicture(picture, RectF(0f, 0f, size.width, size.height)) }
    }
}
