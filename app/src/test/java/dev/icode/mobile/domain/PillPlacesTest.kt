package dev.icode.mobile.domain

import dev.icode.mobile.domain.PillPlaces.Laid
import dev.icode.mobile.domain.PillPlaces.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * docs/dock-navigation-plan.md D11: the floating pills anywhere on the screen's four edges, docked
 * where they are let go, and never over one another.
 */
class PillPlacesTest {
    private val width = 400f
    private val height = 800f
    private val foot = 100f
    private val gap = 8f

    private fun spots(vararg pills: Laid, rtl: Boolean = false) = PillPlaces.spots(pills.toList(), width, height, foot, gap, rtl)

    @Test
    fun `left where they start, the pills stand at the end edge's foot as the stack always stood them`() {
        // The undo pill, the bell, the dock: the last lowest, the foot clear of the bars.
        val undo = Laid(100f, 48f, null)
        val bell = Laid(70f, 48f, null)
        val dock = Laid(290f, 48f, null)
        assertEquals(listOf(Spot(300f, 540f), Spot(330f, 596f), Spot(110f, 652f)), spots(undo, bell, dock))
        // One hidden takes no room: the bell stays put while the undo pill comes and goes.
        assertEquals(Spot(330f, 596f), spots(Laid(0f, 0f, null), bell, dock)[1])
        // Right to left, the end edge is the left.
        assertEquals(Spot(0f, 652f), spots(undo, bell, dock, rtl = true)[2])
    }

    @Test
    fun `a pill put somewhere stands there, the whole edge its room, past the bars and to the corners`() {
        val bell = Laid(70f, 48f, PillPlace(ScreenEdge.End, 1f))
        assertEquals(Spot(330f, 0f), spots(bell).single())
        assertEquals(Spot(330f, 752f), spots(bell.copy(place = PillPlace(ScreenEdge.End, 0f))).single())
        assertEquals(Spot(0f, 376f), spots(bell.copy(place = PillPlace(ScreenEdge.Start, 0.5f))).single())
        // On the top or the bottom, from the start: the left, or the right in a right-to-left language.
        val dock = Laid(290f, 72f, PillPlace(ScreenEdge.Bottom, 0f))
        assertEquals(Spot(0f, 728f), spots(dock).single())
        assertEquals(Spot(110f, 728f), spots(dock, rtl = true).single())
        assertEquals(Spot(55f, 0f), spots(dock.copy(place = PillPlace(ScreenEdge.Top, 0.5f))).single())
    }

    @Test
    fun `no pill stands over another, on its own edge or in a corner`() {
        val pills = listOf(
            Laid(100f, 48f, PillPlace(ScreenEdge.End, 0f)),
            Laid(70f, 48f, PillPlace(ScreenEdge.End, 0f)),
            Laid(290f, 72f, PillPlace(ScreenEdge.Bottom, 1f)),
            Laid(70f, 48f, PillPlace(ScreenEdge.Top, 1f)),
            Laid(70f, 48f, PillPlace(ScreenEdge.End, 1f)),
        )
        val at = PillPlaces.spots(pills, width, height, foot, gap, rtl = false)
        for (i in pills.indices) for (j in pills.indices) if (i < j) {
            val apart = at[i].x + pills[i].width <= at[j].x || at[j].x + pills[j].width <= at[i].x ||
                at[i].y + pills[i].height <= at[j].y || at[j].y + pills[j].height <= at[i].y
            assertFalse("pill $i over pill $j: ${at[i]} ${at[j]}", !apart)
        }
        // The side's pills make way for the dock across the bottom corner, and the one across the top.
        assertEquals(800f - 72f - gap, at[1].y + 48f)
        assertEquals(48f + gap, at[4].y)
    }

    @Test
    fun `let go, a pill docks on the edge nearest the finger, its middle where the finger was`() {
        assertEquals(PillPlace(ScreenEdge.Start, 0.5f), PillPlaces.placeAt(20f, 400f, width, height, 70f, 48f, rtl = false))
        assertEquals(PillPlace(ScreenEdge.End, 1f), PillPlaces.placeAt(390f, 24f, width, height, 70f, 48f, rtl = false))
        assertEquals(PillPlace(ScreenEdge.Top, 0.5f), PillPlaces.placeAt(200f, 10f, width, height, 100f, 48f, rtl = false))
        assertEquals(PillPlace(ScreenEdge.Bottom, 0f), PillPlaces.placeAt(30f, 795f, width, height, 290f, 48f, rtl = false))
        // Right to left, the left is the end, and the top's start is its right.
        assertEquals(ScreenEdge.End, PillPlaces.placeAt(20f, 400f, width, height, 70f, 48f, rtl = true).edge)
        assertEquals(PillPlace(ScreenEdge.Top, 0.25f), PillPlaces.placeAt(275f, 10f, width, height, 100f, 48f, rtl = true))
    }

    @Test
    fun `where a pill stands is how far along its edge`() {
        assertEquals(0.5f, PillPlaces.alongOf(ScreenEdge.End, Spot(330f, 376f), 70f, 48f, width, height, rtl = false))
        assertEquals(0f, PillPlaces.alongOf(ScreenEdge.Bottom, Spot(0f, 728f), 290f, 72f, width, height, rtl = false))
        assertEquals(1f, PillPlaces.alongOf(ScreenEdge.Bottom, Spot(0f, 728f), 290f, 72f, width, height, rtl = true))
    }

    @Test
    fun `places are stored and read back, and what cannot be read is where it starts`() {
        val places = mapOf(FloatingPill.Dock to PillPlace(ScreenEdge.Bottom, 0.25f), FloatingPill.Bell to PillPlace(ScreenEdge.Top, 1f))
        assertEquals(places, PillPlaces.decoded(PillPlaces.encoded(places)))
        assertEquals(mapOf(FloatingPill.Bell to PillPlace(ScreenEdge.End, 0.5f)), PillPlaces.decoded("Bell=End@0.5;Ghost=End@0.5;Dock=Middle@0.5;Undo=Start@high"))
        assertEquals(emptyMap<FloatingPill, PillPlace>(), PillPlaces.decoded(null))
        assertEquals(1f, PillPlaces.decoded("Dock=Start@7").getValue(FloatingPill.Dock).along)
    }
}
