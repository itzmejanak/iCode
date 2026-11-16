package dev.icode.mobile.domain.markdown

import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

/**
 * A formula as RaTeX lays it out (docs/editor-and-language-services.md): its size and what to draw.
 * Lengths are in ems of the size it is drawn at, so one layout serves every zoom; y grows downwards
 * from the formula's top, and its baseline is at [height]. Colours are ARGB, laid out in [INK].
 */
data class MathLayout(val width: Double, val height: Double, val depth: Double, val items: List<MathItem>) {
    companion object {
        /** What formulas are laid out in; the screen draws it in the text's colour, `\color{…}` in its own. */
        const val INK = 0xFF000000.toInt()

        /** RaTeX's 19 KaTeX fonts, by the ids its glyphs name; the rest (`CJK-Regular`…) are the system's. */
        val FONTS = listOf(
            "AMS-Regular", "Caligraphic-Regular", "Fraktur-Bold", "Fraktur-Regular", "Main-Bold", "Main-BoldItalic",
            "Main-Italic", "Main-Regular", "Math-BoldItalic", "Math-Italic", "SansSerif-Bold", "SansSerif-Italic",
            "SansSerif-Regular", "Script-Regular", "Size1-Regular", "Size2-Regular", "Size3-Regular", "Size4-Regular",
            "Typewriter-Regular",
        )

        /** RaTeX's display list, as its JSON gives it. */
        fun read(json: String): MathLayout {
            val list = JSONObject(json)
            val items = list.getJSONArray("items")
            return MathLayout(
                list.getDouble("width"),
                list.getDouble("height"),
                list.getDouble("depth"),
                (0 until items.length()).map { item(items.getJSONObject(it)) },
            )
        }

        private fun item(item: JSONObject): MathItem {
            val x = item.getDouble("x")
            val y = item.getDouble("y")
            val color = color(item.getJSONObject("color"))
            return when (val type = item.getString("type")) {
                "GlyphPath" -> MathItem.Glyph(x, y, item.getDouble("scale"), item.getString("font"), item.getInt("char_code"), color)
                "Line" -> MathItem.Line(x, y, item.getDouble("width"), item.getDouble("thickness"), color, item.optBoolean("dashed"))
                "Rect" -> MathItem.Rect(x, y, item.getDouble("width"), item.getDouble("height"), color)
                "Path" -> MathItem.Path(x, y, commands(item.getJSONArray("commands")), item.getBoolean("fill"), color)
                else -> throw IllegalArgumentException("Unknown display item $type")
            }
        }

        private fun commands(commands: JSONArray): List<MathPath> = (0 until commands.length()).map { at ->
            val command = commands.getJSONObject(at)
            fun d(name: String) = command.getDouble(name)
            when (val type = command.getString("type")) {
                "MoveTo" -> MathPath.MoveTo(d("x"), d("y"))
                "LineTo" -> MathPath.LineTo(d("x"), d("y"))
                "QuadTo" -> MathPath.QuadTo(d("x1"), d("y1"), d("x"), d("y"))
                "CubicTo" -> MathPath.CubicTo(d("x1"), d("y1"), d("x2"), d("y2"), d("x"), d("y"))
                "Close" -> MathPath.Close
                else -> throw IllegalArgumentException("Unknown path command $type")
            }
        }

        private fun color(rgba: JSONObject): Int {
            fun channel(name: String) = (rgba.getDouble(name).coerceIn(0.0, 1.0) * 255).roundToInt()
            return (channel("a") shl 24) or (channel("r") shl 16) or (channel("g") shl 8) or channel("b")
        }
    }
}

sealed interface MathItem {
    /** A character of the font RaTeX names ([font], `Main-Regular`), its baseline's left end at ([x], [y]). */
    data class Glyph(val x: Double, val y: Double, val scale: Double, val font: String, val codePoint: Int, val color: Int) : MathItem

    /** A rule, [y] its centre line. */
    data class Line(val x: Double, val y: Double, val width: Double, val thickness: Double, val color: Int, val dashed: Boolean) : MathItem

    data class Rect(val x: Double, val y: Double, val width: Double, val height: Double, val color: Int) : MathItem

    /** A path, its commands' points from ([x], [y]); filled, or stroked. */
    data class Path(val x: Double, val y: Double, val commands: List<MathPath>, val fill: Boolean, val color: Int) : MathItem
}

sealed interface MathPath {
    data class MoveTo(val x: Double, val y: Double) : MathPath
    data class LineTo(val x: Double, val y: Double) : MathPath
    data class QuadTo(val x1: Double, val y1: Double, val x: Double, val y: Double) : MathPath
    data class CubicTo(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val x: Double, val y: Double) : MathPath
    data object Close : MathPath
}

/** A formula laid out, or why it is shown as its source instead. */
sealed interface MathOutcome {
    data class Drawn(val layout: MathLayout) : MathOutcome
    data class Refused(val reason: String) : MathOutcome
}

/**
 * Formulas laid out once each, the newest [capacity] kept. [engine] lays LaTeX out, in display
 * or inline style, to RaTeX's JSON, or throws with why it cannot. Safe from any thread.
 *
 * A formula over [MAX_SOURCE] characters, or one wider than [MAX_WIDTH_EM] or taller
 * than [MAX_HEIGHT_EM], is shown as its source.
 */
class MathLayouts(private val engine: (latex: String, display: Boolean) -> String, private val capacity: Int = 256) {
    private val kept = object : LinkedHashMap<Pair<String, Boolean>, MathOutcome>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<String, Boolean>, MathOutcome>) = size > capacity
    }

    fun layout(latex: String, display: Boolean): MathOutcome {
        val key = latex to display
        synchronized(kept) { kept[key] }?.let { return it }
        val outcome = laidOut(latex, display)
        synchronized(kept) { kept[key] = outcome }
        return outcome
    }

    private fun laidOut(latex: String, display: Boolean): MathOutcome {
        if (latex.length > MAX_SOURCE) return MathOutcome.Refused(TOO_LARGE)
        val layout = try {
            MathLayout.read(engine(latex, display))
        } catch (error: Exception) {
            return MathOutcome.Refused(error.message ?: error.javaClass.simpleName)
        }
        val tooLarge = layout.width > MAX_WIDTH_EM || layout.height + layout.depth > MAX_HEIGHT_EM
        return if (tooLarge) MathOutcome.Refused(TOO_LARGE) else MathOutcome.Drawn(layout)
    }

    companion object {
        const val MAX_SOURCE = 4_000
        const val MAX_WIDTH_EM = 300.0
        const val MAX_HEIGHT_EM = 100.0
        const val TOO_LARGE = "Too large to draw"
    }
}
