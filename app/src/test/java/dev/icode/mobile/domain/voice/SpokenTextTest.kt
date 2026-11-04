package dev.icode.mobile.domain.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What is read aloud of the assistant, and when (docs/phone-agent-plan.md §5.8). */
class SpokenTextTest {
    @Test fun `code blocks are not read aloud`() {
        assertEquals("Run this: Then it builds.", SpokenText.of("Run this:\n```bash\n./gradlew build\n```\nThen it builds."))
    }

    @Test fun `links are read as their words, addresses not at all`() {
        assertEquals("See the docs or go.", SpokenText.of("See [the docs](https://example.com/docs) or go https://example.com"))
    }

    @Test fun `markdown marks are dropped, and a heading or an item pauses`() {
        assertEquals("Done. The build passed. main.kt changed.", SpokenText.of("# Done\n- **The build** passed.\n- `main.kt` changed"))
        assertEquals("snake_case stays.", SpokenText.of("snake_case stays"))
        assertEquals("A b.", SpokenText.of("| A | b |\n|---|---|"))
    }

    @Test fun `replies are spoken in the bubble by default, never when off, always when always`() {
        assertEquals(SpokenReplies.InBubble, SpokenReplies.of(null))
        assertTrue(SpokenReplies.InBubble.speaks(aloud = true))
        assertFalse(SpokenReplies.InBubble.speaks(aloud = false))
        assertFalse(SpokenReplies.Off.speaks(aloud = true))
        assertTrue(SpokenReplies.Always.speaks(aloud = false))
    }

    @Test fun `long text is cut between sentences, and a sentence too long between words`() {
        assertEquals(listOf("One. Two.", "Three."), SpokenText.parts("One. Two. Three.", 10))
        assertEquals(listOf("aaaa bbbb", "cccc"), SpokenText.parts("aaaa bbbb cccc", 9))
        assertEquals(emptyList<String>(), SpokenText.parts("  ", 9))
    }

    @Test fun `while a reply is written, only its whole sentences are ready, and how much they took`() {
        assertEquals(SpokenText.Ready(listOf("Opening Settings."), 18), SpokenText.ready("Opening Settings. Then Wi", final = false))
        assertEquals(SpokenText.Ready(emptyList(), 0), SpokenText.ready("Opening Sett", final = false))
        assertEquals(SpokenText.Ready(listOf("Then Wi-Fi."), 10), SpokenText.ready("Then Wi-Fi", final = true))
        // A list's "1." and a number's "3.5" are not the end of a sentence; a line's end is.
        assertEquals(SpokenText.Ready(listOf("Steps:"), 7), SpokenText.ready("Steps:\n1. Open it", final = false))
        assertEquals(SpokenText.Ready(emptyList(), 0), SpokenText.ready("Version 3.5 is", final = false))
    }

    @Test fun `code is never read, and nothing is taken from inside a block not yet closed`() {
        val open = "Run this:\n```bash\n./gradlew build. Then\n"
        assertEquals(SpokenText.Ready(listOf("Run this:"), 10), SpokenText.ready(open, final = false))
        val closed = "$open```\nIt builds. Next"
        val ready = SpokenText.ready(closed, final = false)
        assertEquals(listOf("Run this: It builds."), ready.sentences)
        assertEquals(closed.indexOf("Next"), ready.taken)
    }
}
