package dev.icode.mobile.domain

/**
 * The floating stack's pills (docs/dock-navigation-plan.md D6, D11). Each starts where the stack has
 * always put it, at the end edge's foot; held and dragged, it goes to the edge it is let go nearer, at
 * that height, and stays there.
 */
enum class FloatingPill { Undo, Bell, Dock }

/** An edge of the screen as the layout reads it: start is the left in a left-to-right language. */
enum class ScreenEdge { Start, End }

/** Where the user put a pill: on [edge], [height] of the way up the room the pills have, 0 lowest, 1 highest. */
data class PillPlace(val edge: ScreenEdge, val height: Float)

/** How the pills are laid out on an edge, and how their places are kept. */
object PillPlaces {
    /** A pill as an edge lays it out: [size] tall; at [height], or null for its place in the stack's foot. */
    data class Laid(val size: Float, val height: Float?)

    /**
     * The bottom of each of [pills], above the floor of a [room] that tall, in their order. Those at no
     * height stand at the foot as the stack has always stood them, the last lowest; the others at their
     * heights. Then none overlaps another, each [gap] from the next, and none leaves the room. A pill of
     * no size (one hidden) takes no room.
     */
    fun bottoms(pills: List<Laid>, room: Float, gap: Float): List<Float> {
        val wanted = FloatArray(pills.size)
        var foot = 0f
        for (i in pills.indices.reversed()) {
            val pill = pills[i]
            if (pill.height != null) {
                wanted[i] = pill.height.coerceIn(0f, 1f) * (room - pill.size).coerceAtLeast(0f)
            } else {
                wanted[i] = foot
                if (pill.size > 0f) foot += pill.size + gap
            }
        }
        // Lowest first; of two wanting the same place, the later in the stack lower, as at the foot.
        val shown = pills.indices.filter { pills[it].size > 0f }.sortedWith(compareBy({ wanted[it] }, { -it }))
        val bottoms = wanted.copyOf()
        var floor = 0f
        shown.forEach { i ->
            bottoms[i] = maxOf(wanted[i], floor)
            floor = bottoms[i] + pills[i].size + gap
        }
        var ceiling = room
        shown.asReversed().forEach { i ->
            bottoms[i] = minOf(bottoms[i], ceiling - pills[i].size).coerceAtLeast(0f)
            ceiling = bottoms[i] - gap
        }
        return bottoms.toList()
    }

    /** The height a pill [size] tall stands at, its bottom [bottom] above the floor of a [room] that tall. */
    fun heightAt(bottom: Float, size: Float, room: Float): Float {
        val span = room - size
        return if (span <= 0f) 0f else (bottom / span).coerceIn(0f, 1f)
    }

    /** The edge a pill let go with its middle at [x] of a [width] wide screen goes to: the nearer. */
    fun edgeAt(x: Float, width: Float, rtl: Boolean): ScreenEdge = if ((x < width / 2) != rtl) ScreenEdge.Start else ScreenEdge.End

    /** "Dock=End@0.42;Bell=Start@0.1": how the places are stored. */
    fun encoded(places: Map<FloatingPill, PillPlace>): String =
        places.entries.sortedBy { it.key.ordinal }.joinToString(";") { (pill, place) -> "${pill.name}=${place.edge.name}@${place.height}" }

    /** What [text] stored; a part that cannot be read is left out, so that pill is where it starts. */
    fun decoded(text: String?): Map<FloatingPill, PillPlace> = text.orEmpty().split(';').mapNotNull { part ->
        val pill = FloatingPill.entries.firstOrNull { it.name == part.substringBefore('=') } ?: return@mapNotNull null
        val place = part.substringAfter('=', "")
        val edge = ScreenEdge.entries.firstOrNull { it.name == place.substringBefore('@') } ?: return@mapNotNull null
        val height = place.substringAfter('@', "").toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        pill to PillPlace(edge, height.coerceIn(0f, 1f))
    }.toMap()
}
