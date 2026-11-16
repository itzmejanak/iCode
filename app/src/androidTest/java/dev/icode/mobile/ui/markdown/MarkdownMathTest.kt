package dev.icode.mobile.ui.markdown

import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.icode.mobile.domain.markdown.MathItem
import dev.icode.mobile.domain.markdown.MathLayout
import dev.icode.mobile.domain.markdown.MathLayouts
import dev.icode.mobile.domain.markdown.MathOutcome
import io.ratex.RaTeXEngine
import io.ratex.RaTeXException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * RaTeX's own library on the phone (docs/editor-and-language-services.md): the owner's formulas lay
 * out, refused LaTeX says why, and what would run away is stopped, not crashed on. The release is built
 * with `panic = "abort"`: a panic here would end the test process, so this run is the check.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownMathTest {
    private val formulas = listOf(
        """\text{A's rate} = \frac{1}{20} \quad \text{(work per day)}""" to true,
        """\dfrac{78}{35}""" to false,
        """\boxed{T = \frac{323}{35} \approx 9.23 \ \text{days} \; \left(= 9\frac{8}{35} \ \text{days}\right)}""" to true,
        """W_A + W_B = \frac{196}{560} + \frac{253}{560} + \frac{111}{560} = \frac{560}{560} = 1 \quad \checkmark""" to true,
        """2\left(\frac{16 + 20 + 15}{320}\right)""" to true,
        """\begin{pmatrix} a & b \\ c & d \end{pmatrix} \begin{aligned} f(x) &= (x+1)^2 \\ &= x^2 + 2x + 1 \end{aligned}""" to true,
        """f(x) = \begin{cases} x & x \ge 0 \\ -x & x < 0 \end{cases} \quad \operatorname{argmax}_\theta \mathbb{R}^n""" to true,
        """\color{red}{\cancel{x}} \xrightarrow{f} y \overset{!}{=} \text{中文 ünïcödé}""" to true,
    )

    /** The ids RaTeX gives characters its KaTeX fonts lack, drawn in the system's. */
    private val SYSTEM_FONTS = setOf("CJK-Regular", "CJK-Fallback", "Emoji-Fallback")

    @Test
    fun theOwnersFormulasLayOut() {
        formulas.forEach { (latex, display) ->
            val layout = MathLayout.read(RaTeXEngine.layout(latex, display))
            assertTrue(latex, layout.width > 0 && layout.items.isNotEmpty())
            assertTrue(latex, layout.items.filterIsInstance<MathItem.Glyph>().all { it.font in MathLayout.FONTS || it.font in SYSTEM_FONTS })
        }
    }

    @Test
    fun refusedLatexSaysWhy() {
        val reasons = listOf("""\frac{1}{""", """\notacommand{x}""", """\left( x""").map { latex ->
            runCatching { RaTeXEngine.layout(latex, true) }.exceptionOrNull()
        }
        assertTrue(reasons.all { it is RaTeXException && !it.message.isNullOrBlank() })
        assertEquals(MathOutcome.Refused(MathLayouts.TOO_LARGE), mathLayouts.layout("x+".repeat(MathLayouts.MAX_SOURCE), display = true))
    }

    @Test
    fun hostileInputIsStoppedNotCrashedOn() {
        val hostile = buildList {
            formulas.forEach { (latex, _) -> (1..latex.length).forEach { add(latex.substring(0, it)) } }
            listOf(10, 1000, 20_000).forEach { n -> add("{".repeat(n) + "x" + "}".repeat(n)); add("\\frac{".repeat(n) + "1" + "}{2}".repeat(n)) }
            addAll(listOf("""\def\a{\a}\a""", """\def\a{\a\a}\a""", "a".repeat(200_000), """\rule{1000em}{1000em}""", "\\", "&&", "^^", "\\begin{array}{", """\href{javascript:alert(1)}{x}"""))
        }
        hostile.forEach { latex ->
            listOf(true, false).forEach { display -> runCatching { RaTeXEngine.layout(latex, display) } }
        }
    }

    @Test
    fun everyKatexFontLoadsFromTheApk() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        MathLayout.FONTS.forEach { font -> assertNotSame(font, Typeface.DEFAULT, MathFonts.typeface(assets, font)) }
        assertEquals(Typeface.DEFAULT, MathFonts.typeface(assets, "CJK-Regular"))
    }
}
