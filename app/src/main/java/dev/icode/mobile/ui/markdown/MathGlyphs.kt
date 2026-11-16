package dev.icode.mobile.ui.markdown

import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.domain.markdown.MathLayout
import java.util.concurrent.ConcurrentHashMap

/**
 * RaTeX's fonts (docs/editor-and-language-services.md): the 19 KaTeX faces it names, from the APK's
 * assets, each loaded once; the system's for the rest (`CJK-Regular`, `Emoji-Fallback`), whose
 * fallback covers CJK and emoji, as RaTeX-CMP's Android painter has it.
 */
internal object MathFonts {
    private val faces = ConcurrentHashMap<String, Typeface>()

    fun typeface(assets: AssetManager, font: String): Typeface =
        if (font in MathLayout.FONTS) faces.getOrPut(font) { Typeface.createFromAsset(assets, "ratex/fonts/KaTeX_$font.ttf") } else Typeface.DEFAULT
}

/** One of RaTeX's glyphs, [text] at ([x], its baseline [y]); adapted from RaTeX-CMP 0.1.15's Android glyph painter (MIT). */
internal fun DrawScope.drawGlyph(text: String, x: Float, y: Float, size: Float, typeface: Typeface, color: Color) {
    drawIntoCanvas { canvas ->
        glyphPaint.typeface = typeface
        glyphPaint.textSize = size
        glyphPaint.color = color.toArgb()
        canvas.nativeCanvas.drawText(text, x, y, glyphPaint)
    }
}

/** One paint for every glyph: drawing is on the main thread only. */
private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG)
