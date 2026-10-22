package dev.icode.mobile.ui.markdown

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.icode.mobile.designsystem.IcodeTheme
import dev.icode.mobile.designsystem.IcodeType
import dev.icode.mobile.domain.markdown.MarkdownInline
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A formula is drawn where its text keeps room for it (docs/markdown-rendering-plan.md §3.2): display
 * math, and inline math whose LaTeX runs over lines. Their placeholders were once on several lines of
 * text, which Compose counts as cut off: the room was kept, and nothing drawn in it.
 */
@RunWith(AndroidJUnit4::class)
class MarkdownMathDrawnTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun displayMathIsDrawn() {
        val latex = "\\text{A's rate} = \\frac{1}{20}\n\\quad \\text{(work per day)}"
        compose.setContent { IcodeTheme { MarkdownMathBlock(latex, markdownType(IcodeType.Body)) } }
        compose.onNodeWithContentDescription(latex).assertIsDisplayed()
    }

    @Test
    fun inlineMathOverLinesIsDrawn() {
        val latex = "\\frac{78}{35}\n+ 1"
        val context = MarkdownContext(place = null, remoteImages = false, follow = {}, reveal = {})
        compose.setContent {
            IcodeTheme {
                val type = markdownType(IcodeType.Body)
                MarkdownText(listOf(MarkdownInline.Text("B works for "), MarkdownInline.Math(latex), MarkdownInline.Text(" days")), type.body, context, type)
            }
        }
        compose.onNodeWithContentDescription(latex).assertIsDisplayed()
    }
}
