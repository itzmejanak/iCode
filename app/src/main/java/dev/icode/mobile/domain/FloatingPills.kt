package dev.icode.mobile.domain

/**
 * The floating stack's pills (docs/dock-navigation-plan.md D6, D11). Each starts where the stack has
 * always put it, at the end edge's foot; held and dragged anywhere on the screen, it docks on the edge
 * it is let go nearest — left, right, top or bottom — where it was let go along it, and stays there.
 */
enum class FloatingPill { Undo, Bell, Dock }

/** An edge of the screen as the layout reads it: start is the left in a left-to-right language. */
enum class ScreenEdge {
    Start, End, Top, Bottom;

    /** Docked on the top or the bottom: it lies along the screen's width, and tucks up or down. */
    val across: Boolean get() = this == Top || this == Bottom
}

/**
 * Where the user put a pill: on [edge], [along] it from 0 to 1 — on a side from the bottom up, on the
 * top or the bottom from the start.
 */
data class PillPlace(val edge: ScreenEdge, val along: Float)

/** How the pills are laid out on the screen, where one let go goes, and how their places are kept. */
object PillPlaces {
    /** A pill as the screen lays it out: its size, and where the user put it; null where it starts. */
    data class Laid(val width: Float, val height: Float, val place: PillPlace?)

    /** A pill's top left corner, from the screen's left and its top. */
    data class Spot(val x: Float, val y: Float)

    /**
     * Where each of [pills] stands on a [width] by [height] screen. Those at no place stand at the end
     * edge's foot, as the stack has always stood them, [foot] up — clear of the bars below — the last
     * lowest. The others stand where the user put them, anywhere along their edge, the bars and corners
     * included. None stands over another, each [gap] from the next: on the same edge they make way for
     * each other, and on a side one makes way for a pill across the top or the bottom in its corner. A
     * pill of no size (one hidden) takes no room.
     */
    fun spots(pills: List<Laid>, width: Float, height: Float, foot: Float, gap: Float, rtl: Boolean): List<Spot> {
        val sides = pills.map { sideOf(it.place?.edge ?: ScreenEdge.End, rtl) }
        val spots = arrayOfNulls<Spot>(pills.size)
        // Across the top and the bottom first: in a corner, a side's pill makes way for them.
        for (side in listOf(Side.Top, Side.Bottom)) {
            val here = pills.indices.filter { sides[it] == side }
            val lefts = pack(here.map { i ->
                val pill = pills[i]
                val fromStart = pill.place?.along ?: 0f
                Item(pill.width, (if (rtl) 1f - fromStart else fromStart) * (width - pill.width), 0f, width)
            }, gap)
            here.forEachIndexed { at, i -> spots[i] = Spot(lefts[at], if (side == Side.Top) 0f else height - pills[i].height) }
        }
        val across = pills.indices.filter { (sides[it] == Side.Top || sides[it] == Side.Bottom) && pills[it].width > 0f && pills[it].height > 0f }
        for (side in listOf(Side.Left, Side.Right)) {
            val here = pills.indices.filter { sides[it] == side }
            var footAt = foot
            val wanted = FloatArray(here.size)
            for (at in here.indices.reversed()) {
                val pill = pills[here[at]]
                val along = pill.place?.along
                if (along != null) {
                    wanted[at] = along * (height - pill.height)
                } else {
                    wanted[at] = footAt
                    if (pill.height > 0f) footAt += pill.height + gap
                }
            }
            val bottoms = pack(here.mapIndexed { at, i ->
                val pill = pills[i]
                val left = if (side == Side.Left) 0f else width - pill.width
                // The pills across the top or the bottom in this pill's way: its corners are theirs.
                val inWay = across.filter { j -> spots[j]!!.x < left + pill.width && spots[j]!!.x + pills[j].width > left }
                val lo = inWay.filter { sides[it] == Side.Bottom }.maxOfOrNull { pills[it].height + gap } ?: 0f
                val hi = height - (inWay.filter { sides[it] == Side.Top }.maxOfOrNull { pills[it].height + gap } ?: 0f)
                Item(pill.height, wanted[at], lo, hi)
            }, gap)
            here.forEachIndexed { at, i ->
                spots[i] = Spot(if (side == Side.Left) 0f else width - pills[i].width, height - bottoms[at] - pills[i].height)
            }
        }
        return spots.map { it!! }
    }

    /**
     * Where a pill [pillWidth] by [pillHeight], let go with the finger at [x], [y] on a [width] by
     * [height] screen, docks: on the edge nearest the finger — a side's on a tie — its middle at the
     * finger along it.
     */
    fun placeAt(x: Float, y: Float, width: Float, height: Float, pillWidth: Float, pillHeight: Float, rtl: Boolean): PillPlace {
        val left = x
        val right = width - x
        val top = y
        val bottom = height - y
        val nearest = minOf(left, right, top, bottom)
        val side = { onLeft: Boolean -> if (onLeft != rtl) ScreenEdge.Start else ScreenEdge.End }
        return when (nearest) {
            left -> PillPlace(side(true), share(height - y - pillHeight / 2, height - pillHeight))
            right -> PillPlace(side(false), share(height - y - pillHeight / 2, height - pillHeight))
            else -> {
                val fromLeft = share(x - pillWidth / 2, width - pillWidth)
                PillPlace(if (nearest == top) ScreenEdge.Top else ScreenEdge.Bottom, if (rtl) 1f - fromLeft else fromLeft)
            }
        }
    }

    /** How far along [edge] a pill [pillWidth] by [pillHeight] at [spot] stands, as [PillPlace.along] says. */
    fun alongOf(edge: ScreenEdge, spot: Spot, pillWidth: Float, pillHeight: Float, width: Float, height: Float, rtl: Boolean): Float =
        if (edge.across) {
            share(spot.x, width - pillWidth).let { if (rtl) 1f - it else it }
        } else {
            share(height - spot.y - pillHeight, height - pillHeight)
        }

    /** "Dock=End@0.42;Bell=Top@0.1": how the places are stored. */
    fun encoded(places: Map<FloatingPill, PillPlace>): String =
        places.entries.sortedBy { it.key.ordinal }.joinToString(";") { (pill, place) -> "${pill.name}=${place.edge.name}@${place.along}" }

    /** What [text] stored; a part that cannot be read is left out, so that pill is where it starts. */
    fun decoded(text: String?): Map<FloatingPill, PillPlace> = text.orEmpty().split(';').mapNotNull { part ->
        val pill = FloatingPill.entries.firstOrNull { it.name == part.substringBefore('=') } ?: return@mapNotNull null
        val place = part.substringAfter('=', "")
        val edge = ScreenEdge.entries.firstOrNull { it.name == place.substringBefore('@') } ?: return@mapNotNull null
        val along = place.substringAfter('@', "").toFloatOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
        pill to PillPlace(edge, along.coerceIn(0f, 1f))
    }.toMap()

    /** The screen's own sides, which an edge is one of as the language reads. */
    private enum class Side { Left, Right, Top, Bottom }

    private fun sideOf(edge: ScreenEdge, rtl: Boolean) = when (edge) {
        ScreenEdge.Start -> if (rtl) Side.Right else Side.Left
        ScreenEdge.End -> if (rtl) Side.Left else Side.Right
        ScreenEdge.Top -> Side.Top
        ScreenEdge.Bottom -> Side.Bottom
    }

    /** One pill along its edge: its [size] along it, where it would start, and the stretch it must stay in. */
    private class Item(val size: Float, val wanted: Float, val lo: Float, val hi: Float)

    /**
     * Where each item starts along its edge: where it wants, within its stretch, none over another
     * and each [gap] apart. Of two wanting the same start, the later in the stack goes first, as at the
     * foot, where the last stands lowest.
     */
    private fun pack(items: List<Item>, gap: Float): List<Float> {
        val starts = FloatArray(items.size) { items[it].wanted }
        val shown = items.indices.filter { items[it].size > 0f }.sortedWith(compareBy({ items[it].wanted }, { -it }))
        var floor = Float.NEGATIVE_INFINITY
        shown.forEach { i ->
            starts[i] = maxOf(starts[i], items[i].lo, floor)
            floor = starts[i] + items[i].size + gap
        }
        var ceiling = Float.POSITIVE_INFINITY
        shown.asReversed().forEach { i ->
            starts[i] = minOf(starts[i], items[i].hi - items[i].size, ceiling - items[i].size).coerceAtLeast(0f)
            ceiling = starts[i] - gap
        }
        return starts.toList()
    }

    private fun share(at: Float, span: Float): Float = if (span <= 0f) 0f else (at / span).coerceIn(0f, 1f)
}
