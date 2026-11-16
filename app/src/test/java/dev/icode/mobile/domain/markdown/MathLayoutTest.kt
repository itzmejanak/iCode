package dev.icode.mobile.domain.markdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Display lists RaTeX 0.1.15's core laid out for the owner's formulas (docs/editor-and-language-services.md). */
class MathLayoutTest {
    private fun fixture(name: String) = File("src/test/resources/ratex/$name.json").readText()

    @Test fun `the boxed answer reads with its size, glyphs, rules and box`() {
        val layout = MathLayout.read(fixture("boxed"))
        assertEquals(17.0889, layout.width, 1e-4)
        assertEquals(1.79, layout.height, 1e-4)
        assertEquals(1.29, layout.depth, 1e-4)
        assertEquals(mapOf("Rect" to 4, "Glyph" to 27, "Line" to 2), layout.items.groupingBy { it.javaClass.simpleName }.eachCount())
        assertEquals(MathItem.Rect(0.0, 0.0, 17.0889, 0.04, MathLayout.INK), layout.items.filterIsInstance<MathItem.Rect>().first().let { it.copy(width = Math.round(it.width * 1e4) / 1e4) })
        assertTrue(layout.items.filterIsInstance<MathItem.Glyph>().all { it.font in MathLayout.FONTS && it.color == MathLayout.INK })
    }

    @Test fun `colour and paths read as written`() {
        val layout = MathLayout.read(fixture("cancel-arrow"))
        val red = 0xFFFF0000.toInt()
        assertTrue(layout.items.all { item -> listOf(item).filterIsInstance<MathItem.Glyph>().all { it.color == red } })
        val paths = layout.items.filterIsInstance<MathItem.Path>()
        assertEquals(listOf(false, true), paths.map { it.fill })
        assertEquals(MathPath.MoveTo::class, paths.first().commands.first()::class)
    }

    @Test fun `inline and display fractions differ only in style`() {
        val inline = MathLayout.read(fixture("dfrac-inline"))
        assertEquals(listOf(55, 56, 51, 53), inline.items.filterIsInstance<MathItem.Glyph>().map { it.codePoint })
        assertEquals(1, inline.items.filterIsInstance<MathItem.Line>().size)
    }

    @Test fun `layouts are made once and refused past their limits`() {
        var calls = 0
        val boxed = fixture("boxed")
        val layouts = MathLayouts({ latex, _ ->
            calls++
            when (latex) {
                "huge" -> """{"width":301,"height":1,"depth":0,"items":[]}"""
                "bad" -> throw IllegalStateException("parse error: Undefined control sequence: \\bad")
                else -> boxed
            }
        })
        assertTrue(layouts.layout("x", display = true) is MathOutcome.Drawn)
        layouts.layout("x", display = true)
        assertEquals(1, calls)
        assertEquals(MathOutcome.Refused(MathLayouts.TOO_LARGE), layouts.layout("huge", display = true))
        assertEquals(MathOutcome.Refused("parse error: Undefined control sequence: \\bad"), layouts.layout("bad", display = false))
        assertEquals(MathOutcome.Refused(MathLayouts.TOO_LARGE), layouts.layout("x".repeat(MathLayouts.MAX_SOURCE + 1), display = true))
        assertEquals(3, calls)
    }

    @Test fun `every font RaTeX names ships in the app`() {
        MathLayout.FONTS.forEach { font -> assertTrue(font, File("src/main/assets/ratex/fonts/KaTeX_$font.ttf").isFile) }
    }
}
