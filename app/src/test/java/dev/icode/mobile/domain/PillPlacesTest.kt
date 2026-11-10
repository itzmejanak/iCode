package dev.icode.mobile.domain

import dev.icode.mobile.domain.PillPlaces.Laid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** docs/dock-navigation-plan.md D11: the floating pills where the user put them, never over one another. */
class PillPlacesTest {
    private val room = 1000f
    private val gap = 8f

    @Test
    fun `left where they start, the pills stand at the foot as the stack always stood them, the last lowest`() {
        // The undo pill, the bell, the dock.
        assertEquals(listOf(112f, 56f, 0f), PillPlaces.bottoms(listOf(Laid(48f, null), Laid(48f, null), Laid(48f, null)), room, gap))
        // One hidden takes no room: the bell stays put while the undo pill comes and goes.
        assertEquals(56f, PillPlaces.bottoms(listOf(Laid(0f, null), Laid(48f, null), Laid(48f, null)), room, gap)[1])
    }

    @Test
    fun `a pill put at a height stands there, its height a share of the room above the floor`() {
        assertEquals(listOf(0f, 476f), PillPlaces.bottoms(listOf(Laid(48f, null), Laid(48f, 0.5f)), room, gap))
        assertEquals(952f, PillPlaces.bottoms(listOf(Laid(48f, 1f)), room, gap).single())
        // As the keyboard takes room, it keeps its share of what is left.
        assertEquals(226f, PillPlaces.bottoms(listOf(Laid(48f, 0.5f)), 500f, gap).single())
    }

    @Test
    fun `no pill stands over another, and none leaves the room`() {
        val bottoms = PillPlaces.bottoms(listOf(Laid(48f, 0.5f), Laid(48f, 0.5f), Laid(48f, null)), room, gap)
        val spans = bottoms.zip(listOf(48f, 48f, 48f)).map { (bottom, size) -> bottom..bottom + size }.sortedBy { it.start }
        spans.zipWithNext().forEach { (lower, upper) -> assertTrue("$lower under $upper", upper.start >= lower.endInclusive + gap) }
        // Two put at the top: one stands just below the other, neither out of the room.
        val top = PillPlaces.bottoms(listOf(Laid(48f, 1f), Laid(48f, 1f)), room, gap)
        assertTrue(top.all { it + 48f <= room })
        assertEquals(gap, kotlin.math.abs(top[0] - top[1]) - 48f)
    }

    @Test
    fun `a pill let go is kept at its height and on the edge it is nearer`() {
        assertEquals(0.5f, PillPlaces.heightAt(476f, 48f, room))
        assertEquals(0f, PillPlaces.heightAt(-30f, 48f, room))
        assertEquals(1f, PillPlaces.heightAt(2000f, 48f, room))
        assertEquals(ScreenEdge.Start, PillPlaces.edgeAt(100f, 400f, rtl = false))
        assertEquals(ScreenEdge.End, PillPlaces.edgeAt(300f, 400f, rtl = false))
        // Right to left, the left edge is the end.
        assertEquals(ScreenEdge.End, PillPlaces.edgeAt(100f, 400f, rtl = true))
    }

    @Test
    fun `places are stored and read back, and what cannot be read is where it starts`() {
        val places = mapOf(FloatingPill.Dock to PillPlace(ScreenEdge.Start, 0.25f), FloatingPill.Bell to PillPlace(ScreenEdge.End, 1f))
        assertEquals(places, PillPlaces.decoded(PillPlaces.encoded(places)))
        assertEquals(mapOf(FloatingPill.Bell to PillPlace(ScreenEdge.End, 0.5f)), PillPlaces.decoded("Bell=End@0.5;Ghost=End@0.5;Dock=Middle@0.5;Undo=Start@high"))
        assertEquals(emptyMap<FloatingPill, PillPlace>(), PillPlaces.decoded(null))
        assertEquals(1f, PillPlaces.decoded("Dock=Start@7").getValue(FloatingPill.Dock).height)
    }
}
