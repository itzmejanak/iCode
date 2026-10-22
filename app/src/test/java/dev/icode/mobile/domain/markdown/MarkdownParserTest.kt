package dev.icode.mobile.domain.markdown

import dev.icode.mobile.domain.markdown.MarkdownBlock.Code
import dev.icode.mobile.domain.markdown.MarkdownBlock.Heading
import dev.icode.mobile.domain.markdown.MarkdownBlock.ListBlock
import dev.icode.mobile.domain.markdown.MarkdownBlock.Paragraph
import dev.icode.mobile.domain.markdown.MarkdownInline.Styled
import dev.icode.mobile.domain.markdown.MarkdownInline.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    private fun blocks(text: String) = MarkdownParser.file(text).blocks
    private fun inlines(text: String) = (blocks(text).single() as Paragraph).content
    private fun math(latex: String) = MarkdownInline.Math(latex)

    @Test fun `the screenshot's file keeps its LaTeX whole`() {
        val document = MarkdownParser.file(
            """
            |# Time and Work — Wage
            |
            |Let the total work be ${'$'}1${'$'} unit.
            |
            |$$ \text{A's rate} = \frac{1}{20} \quad \text{(work per day)} $$
            |
            |### B works for ${'$'}3${'$'} days, then ${'$'}\dfrac{78}{35}${'$'} days
            |
            |$$
            |\boxed{T = \frac{323}{35} \approx 9.23 \ \text{days} \; \left(= 9\frac{8}{35} \ \text{days}\right)}
            |$$
            |
            |Inline ${'$'}W_A + W_B${'$'} and *real emphasis*, and a price of ${'$'}5 and ${'$'}6.
            |
            || A | B |
            ||---|---|
            || ${'$'}x^2${'$'} | 2 |
            """.trimMargin(),
        )
        assertEquals(
            listOf(
                Heading(1, listOf(Text("Time and Work — Wage"))),
                Paragraph(listOf(Text("Let the total work be "), math("1"), Text(" unit."))),
                MarkdownBlock.Math("""\text{A's rate} = \frac{1}{20} \quad \text{(work per day)}"""),
                Heading(3, listOf(Text("B works for "), math("3"), Text(" days, then "), math("""\dfrac{78}{35}"""), Text(" days"))),
                MarkdownBlock.Math("""\boxed{T = \frac{323}{35} \approx 9.23 \ \text{days} \; \left(= 9\frac{8}{35} \ \text{days}\right)}"""),
                Paragraph(
                    listOf(
                        Text("Inline "), math("W_A + W_B"), Text(" and "), Styled(MarkdownStyle.Emphasis, listOf(Text("real emphasis"))),
                        Text(", and a price of \$5 and \$6."),
                    ),
                ),
                MarkdownBlock.Table(
                    listOf(MarkdownAlignment.Start, MarkdownAlignment.Start),
                    listOf(listOf(Text("A")), listOf(Text("B"))),
                    listOf(listOf(listOf(math("x^2")), listOf(Text("2")))),
                ),
            ),
            document.blocks,
        )
        assertEquals(mapOf("time-and-work--wage" to 0, "b-works-for-3-days-then-dfrac7835-days" to 3), document.anchors)
    }

    @Test fun `display math inside a paragraph is drawn on its own`() {
        assertEquals(
            listOf(Paragraph(listOf(Text("Para with"))), MarkdownBlock.Math("x = 1"), Paragraph(listOf(Text("after.")))),
            blocks("Para with $$\nx = 1\n$$ after."),
        )
        assertEquals(listOf(MarkdownBlock.Math("a^2 + b^2 = c^2")), blocks("```math\na^2 + b^2 = c^2\n```"))
    }

    @Test fun `text, styles, code spans, breaks, entities and escapes`() {
        assertEquals(
            listOf(
                Text("a "), Styled(MarkdownStyle.Strong, listOf(Text("b"))), Text(" "), Styled(MarkdownStyle.Strike, listOf(Text("c"))), Text(" "),
                MarkdownInline.Code("x `y`"), Text(" ==hi== & © * line"), MarkdownInline.LineBreak, Text("next"), MarkdownInline.LineBreak, Text("last joined"),
            ),
            inlines("a **b** ~~c~~ `` x `y` `` ==hi== &amp; &copy; \\* line\\\nnext  \nlast\njoined"),
        )
    }

    @Test fun `links by destination, by reference resolved, and as written when undefined`() {
        val document = MarkdownParser.file("[in](https://a.b \"T\") [ref][R] [short] [none] <https://c.d> https://e.f/g. www.x.com <me@x.com>\n\n[r]: https://r.example \"R\"\n[short]: ./a.md#L10")
        val content = (document.blocks.single() as Paragraph).content
        fun link(href: String, text: String, title: String? = null) = MarkdownInline.Link(MarkdownDestination.Url(href), title, listOf(Text(text)))
        assertEquals(
            listOf(
                link("https://a.b", "in", "T"), Text(" "), link("https://r.example", "ref", "R"), Text(" "), link("./a.md#L10", "short"), Text(" "),
                MarkdownInline.Link(MarkdownDestination.Reference("none", "[none]"), null, listOf(Text("none"))), Text(" "),
                link("https://c.d", "https://c.d"), Text(" "), link("https://e.f/g", "https://e.f/g"), Text(". "),
                link("http://www.x.com", "www.x.com"), Text(" "), link("mailto:me@x.com", "me@x.com"),
            ),
            content,
        )
    }

    @Test fun `images inline and by reference`() {
        assertEquals(
            listOf(
                MarkdownInline.Image(MarkdownDestination.Url("img.png"), "the alt", "t"), Text(" "),
                MarkdownInline.Image(MarkdownDestination.Reference("r", "![logo][r]"), "logo", null),
            ),
            inlines("![the *alt*](img.png \"t\") ![logo][r]"),
        )
    }

    @Test fun `headings, lists, tasks and quotes`() {
        assertEquals(
            listOf(
                Heading(2, listOf(Text("Closing"))),
                Heading(1, listOf(Text("C#"))),
                Heading(2, listOf(Text("Setext"))),
                ListBlock(
                    3, loose = false,
                    items = listOf(
                        MarkdownListItem(null, listOf(Paragraph(listOf(Text("three"))))),
                        MarkdownListItem(
                            null,
                            listOf(
                                Paragraph(listOf(Text("four"))),
                                ListBlock(null, false, listOf(MarkdownListItem(true, listOf(Paragraph(listOf(Text("done"))))), MarkdownListItem(false, listOf(Paragraph(listOf(Text("todo"))))))),
                            ),
                        ),
                    ),
                ),
                MarkdownBlock.Quote(listOf(Paragraph(listOf(Text("line one line two"))), MarkdownBlock.Quote(listOf(Paragraph(listOf(Text("nested"))))))),
            ),
            blocks("## Closing ##\n# C#\nSetext\n---\n\n3. three\n4. four\n   - [x] done\n   - [ ] todo\n\n> line one\n> line two\n> > nested"),
        )
        assertTrue((blocks("- a\n\n- b").single() as ListBlock).loose)
    }

    @Test fun `alerts by kind`() {
        assertEquals(
            listOf(MarkdownBlock.Alert(MarkdownAlertKind.Warning, listOf(Paragraph(listOf(Text("Careful "), Styled(MarkdownStyle.Strong, listOf(Text("now"))), Text(".")))))),
            blocks("> [!WARNING]\n> Careful **now**."),
        )
    }

    @Test fun `code as written, in quotes and lists too`() {
        assertEquals(
            listOf(
                Code("kotlin", "val x = 1\n\n  y()"),
                MarkdownBlock.Quote(listOf(Code("js", "let a = 1\n  b()"))),
                Code(null, "indented\n  more"),
                ListBlock(null, false, listOf(MarkdownListItem(null, listOf(Paragraph(listOf(Text("item"))), Code("py", "x = 1\n  y = 2"))))),
                Code(null, "unclosed"),
            ),
            blocks("```kotlin title\nval x = 1\n\n  y()\n```\n\n> ```js\n> let a = 1\n>   b()\n> ```\n\n    indented\n      more\n\n- item\n  ```py\n  x = 1\n    y = 2\n  ```\n\n```\nunclosed\n"),
        )
        // Indented four spaces after a `- ` item, a line is the item's: CommonMark, not code.
        assertEquals(Paragraph(listOf(Text("more"))), (blocks("- item\n\n    more").single() as ListBlock).items.single().blocks.last())
    }

    @Test fun `tables align and are as wide as their header`() {
        assertEquals(
            MarkdownBlock.Table(
                listOf(MarkdownAlignment.Start, MarkdownAlignment.Center, MarkdownAlignment.End),
                listOf(listOf(Text("L")), listOf(Text("C")), listOf(Text("R"))),
                listOf(listOf(listOf(Text("a")), listOf(MarkdownInline.Code("b|c")), emptyList())),
            ),
            blocks("| L | C | R |\n|:--|:-:|--:|\n| a | `b\\|c` |").single(),
        )
    }

    @Test fun `front matter, rules and footnotes numbered by first reference`() {
        val document = MarkdownParser.file("---\ntitle: Notes\n---\nB[^b] then A[^a] then B again[^b] and [^missing].\n\n***\n\n[^a]: First.\n[^b]: Second with [a link](https://n.example).")
        assertEquals(MarkdownBlock.FrontMatter("title: Notes"), document.blocks.first())
        assertEquals(MarkdownBlock.Rule, document.blocks.last())
        assertEquals(
            listOf(
                MarkdownFootnote("b", 1, listOf(Text("Second with "), MarkdownInline.Link(MarkdownDestination.Url("https://n.example"), null, listOf(Text("a link"))), Text(".")), referredFrom = 1),
                MarkdownFootnote("a", 2, listOf(Text("First.")), referredFrom = 1),
            ),
            document.footnotes,
        )
        assertEquals(
            listOf(
                Text("B"), MarkdownInline.FootnoteReference("b", "[^b]", 1), Text(" then A"), MarkdownInline.FootnoteReference("a", "[^a]", 2),
                Text(" then B again"), MarkdownInline.FootnoteReference("b", "[^b]", 1), Text(" and "), MarkdownInline.FootnoteReference("missing", "[^missing]"), Text("."),
            ),
            (document.blocks[1] as Paragraph).content,
        )
    }

    @Test fun `html draws its safe set and shows the rest as written`() {
        val document = MarkdownParser.file(
            "<details>\n<summary>More <b>info</b></summary>\n\nHidden **bold**\n\n</details>\n\n" +
                "<p align=\"center\"><img src=\"logo.png\" width=\"120\" alt=\"Logo\"></p>\n\n<!-- hidden -->\n\n" +
                "Press <kbd>Ctrl</kbd>, H<sub>2</sub>O,<br>next <span>kept</span> <blink>old</blink>.",
        )
        assertEquals(
            listOf(
                MarkdownBlock.Details(
                    listOf(Text("More "), Styled(MarkdownStyle.Strong, listOf(Text("info")))),
                    open = false,
                    blocks = listOf(Paragraph(listOf(Text("Hidden "), Styled(MarkdownStyle.Strong, listOf(Text("bold")))))),
                ),
                MarkdownBlock.Aligned(MarkdownAlignment.Center, listOf(Paragraph(listOf(MarkdownInline.Image(MarkdownDestination.Url("logo.png"), "Logo", null, 120))))),
                Paragraph(
                    listOf(
                        Text("Press "), MarkdownInline.Code("Ctrl", key = true), Text(", H"), Styled(MarkdownStyle.Subscript, listOf(Text("2"))), Text("O,"),
                        MarkdownInline.LineBreak, Text("next kept "), MarkdownInline.Source("<blink>"), Text("old"), MarkdownInline.Source("</blink>"), Text("."),
                    ),
                ),
            ),
            document.blocks,
        )
    }

    @Test fun `an html table becomes a table`() {
        assertEquals(
            MarkdownBlock.Table(
                listOf(MarkdownAlignment.Start, MarkdownAlignment.Center),
                listOf(listOf(Text("Name")), listOf(Text("Count"))),
                listOf(listOf(listOf(Text("a")), listOf(Text("1")))),
            ),
            blocks("<table>\n<tr><th>Name</th><th align=\"center\">Count</th></tr>\n<tr><td>a</td><td>1</td></tr>\n</table>").single(),
        )
    }

    @Test fun `repeated headings get GitHub's numbered anchors`() {
        assertEquals(mapOf("setup" to 0, "setup-1" to 1, "setup-2" to 2), MarkdownParser.file("# Setup\n## Setup\n### Setup").anchors)
    }
}
