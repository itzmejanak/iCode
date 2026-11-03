package dev.icode.mobile.domain.phone

/** The strokes and keys the agent sends the screen itself, where no element takes the act. */
object PhoneSwipes {
    /** A stroke's start and end. */
    data class Stroke(val x1: Int, val y1: Int, val x2: Int, val y2: Int)

    /**
     * A swipe across the middle of a [width] × [height] screen that scrolls its content [direction]:
     * to see what is further down, the finger moves up, as a person's does.
     */
    fun across(width: Int, height: Int, direction: PhoneDirection): Stroke {
        val x = width / 2
        val y = height / 2
        val dy = (height * REACH).toInt()
        val dx = (width * REACH).toInt()
        return when (direction) {
            PhoneDirection.Down -> Stroke(x, y + dy, x, y - dy)
            PhoneDirection.Up -> Stroke(x, y - dy, x, y + dy)
            PhoneDirection.Right -> Stroke(x + dx, y, x - dx, y)
            PhoneDirection.Left -> Stroke(x - dx, y, x + dx, y)
        }
    }

    /** [text] as Android's `input text` takes it in a shell: spaces as `%s`, and quoted for `sh`. */
    fun inputText(text: String): String = "'" + text.replace(" ", "%s").replace("'", "'\\''") + "'"

    /** How far a swipe reaches each way from the middle: three fifths of the screen in all, about a page. */
    private const val REACH = 0.3f
}
