package dev.icode.mobile.data.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every notification has a registered channel and a key no other has (docs/engineering-rules.md). */
class IcodeChannelsTest {
    @Test fun `each channel and group is made once, and none is one retired`() {
        val ids = IcodeChannel.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(IcodeChannelGroup.entries.size, IcodeChannelGroup.entries.map { it.id }.toSet().size)
        assertTrue(IcodeChannels.RETIRED.none { it in ids })
    }

    @Test fun `the ids users chose settings for are kept`() {
        assertEquals(
            listOf("icode.runtime", "icode.work-progress", "icode.phone-control", "icode.work-questions", "icode.problems", "icode.terminal-alerts", "icode.messages"),
            IcodeChannel.entries.map { it.id } - "icode.finished",
        )
    }

    @Test fun `each kind of notification has an id of its own`() {
        val ids = NotificationKey.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}
