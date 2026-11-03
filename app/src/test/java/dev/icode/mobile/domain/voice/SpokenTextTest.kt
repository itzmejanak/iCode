package dev.icode.mobile.domain.voice

import dev.icode.mobile.domain.AssistantMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpokenTextTest {
    @Test fun `code blocks are not read aloud`() {
        assertEquals("Run this: Then it builds.", SpokenText.of("Run this:\n```bash\n./gradlew build\n```\nThen it builds."))
    }

    @Test fun `links are read as their words, addresses not at all`() {
        assertEquals("See the docs or go", SpokenText.of("See [the docs](https://example.com/docs) or go https://example.com"))
    }

    @Test fun `markdown marks are dropped`() {
        assertEquals("Done The build passed. main.kt changed", SpokenText.of("# Done\n- **The build** passed.\n- `main.kt` changed"))
    }

    @Test fun `the last answer is the replies after the user's last message`() {
        val messages = listOf(
            AssistantMessage.User("first"),
            AssistantMessage.Reply("old answer"),
            AssistantMessage.User("turn on wifi"),
            AssistantMessage.Reply("Opening Settings."),
            AssistantMessage.Results(emptyList()),
            AssistantMessage.Reply("Wi-Fi is **on**."),
        )
        assertEquals("Opening Settings. Wi-Fi is on.", SpokenText.lastAnswer(messages))
    }

    @Test fun `a turn with no words says nothing`() {
        assertNull(SpokenText.lastAnswer(listOf(AssistantMessage.User("hi"), AssistantMessage.Reply("```\ncode\n```"))))
    }

    @Test fun `replies are spoken in the bubble by default, never when off, always when always`() {
        assertEquals(SpokenReplies.InBubble, SpokenReplies.of(null))
        assertTrue(SpokenReplies.InBubble.speaks(fromBubble = true))
        assertFalse(SpokenReplies.InBubble.speaks(fromBubble = false))
        assertFalse(SpokenReplies.Off.speaks(fromBubble = true))
        assertTrue(SpokenReplies.Always.speaks(fromBubble = false))
    }
}
