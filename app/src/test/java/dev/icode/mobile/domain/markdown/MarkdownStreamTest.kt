package dev.icode.mobile.domain.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownStreamTest {
    private val answer = """
        |## Plan
        |
        |Rate \( \frac{1}{20} \) per day, so:
        |
        |\[
        |T = \frac{323}{35} \approx 9.23
        |\]
        |
        |1. Read `src/App.kt` and [the guide](docs/guide.md#L4)[^1].
        |2. Change it:
        |
        |   ```kotlin
        |   val rate = 1.0 / 20 // \( not math \)
        |   ```
        |
        || Day | Done |
        ||----:|:-----|
        || 3 | $\dfrac{3}{20}$ |
        |
        |> [!TIP]
        |> Keep **it** short.
        |
        |<details><summary>Why</summary>
        |
        |Because ${'$'}${'$'}x${'$'}${'$'} and \(y\).
        |
        |</details>
        |
        |[^1]: The guide's fourth line.
        """.trimMargin()

    @Test fun `an answer streamed in chunks reads as the same answer whole`() {
        val whole = MarkdownStream().update(answer, complete = true)
        listOf(1, 2, 3, 7, 13, 64).forEach { size ->
            val stream = MarkdownStream()
            answer.chunked(size).runningReduce { sofar, chunk -> sofar + chunk }.forEach { stream.update(it, complete = false) }
            assertEquals("chunks of $size", whole, stream.update(answer, complete = true))
        }
    }

    @Test fun `an answer's math delimiters are the parser's`() {
        val blocks = MarkdownStream().update(answer, complete = true).blocks
        assertEquals(
            MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("Rate "), MarkdownInline.Math("\\frac{1}{20}"), MarkdownInline.Text(" per day, so:"))),
            blocks[1],
        )
        assertEquals(MarkdownBlock.Math("T = \\frac{323}{35} \\approx 9.23"), blocks[2])
        val code = ((blocks[3] as MarkdownBlock.ListBlock).items[1].blocks[1] as MarkdownBlock.Code).text
        assertEquals("val rate = 1.0 / 20 // \\( not math \\)", code)
    }

    @Test fun `what has settled stays the same object as more streams in`() {
        val stream = MarkdownStream()
        val first = stream.update("A [link][r] and a note[^n].\n\nSecond.\n\nThird", complete = false).blocks
        val later = stream.update("A [link][r] and a note[^n].\n\nSecond.\n\nThird, longer.\n\n[r]: https://r.example", complete = false).blocks
        assertTrue(later[1] === first[1])
        // The first paragraph's link is now defined: it is a new paragraph, and drawn again.
        assertTrue(later[0] !== first[0])
        assertEquals(MarkdownDestination.Url("https://r.example"), ((later[0] as MarkdownBlock.Paragraph).content[1] as MarkdownInline.Link).destination)
    }

    @Test fun `a text that does not continue the last is read anew`() {
        val stream = MarkdownStream()
        stream.update("First answer", complete = false)
        assertEquals(listOf(MarkdownBlock.Paragraph(listOf(MarkdownInline.Text("Second")))), stream.update("Second", complete = false).blocks)
    }
}
