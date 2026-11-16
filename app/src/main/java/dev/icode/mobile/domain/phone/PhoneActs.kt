package dev.icode.mobile.domain.phone

/** What the agent does to an element of the screen (docs/voice-and-phone-control.md). */
sealed interface PhoneAct {
    /** A tap; a long press when [hold]. */
    data class Tap(val hold: Boolean = false) : PhoneAct

    /** [text] into a field — in place of what it holds, or after it — then its keyboard's action when [submit]. */
    data class Type(val text: String, val replace: Boolean = true, val submit: Boolean = false) : PhoneAct

    data class Scroll(val direction: PhoneDirection) : PhoneAct
}

enum class PhoneDirection { Up, Down, Left, Right }

/** Android's own keys, as an accessibility service presses them. */
enum class PhoneKey(val word: String) {
    Back("back"),
    Home("home"),
    Recents("recents"),
    Notifications("notifications"),
    QuickSettings("quick settings"),
}

/** A picture of the screen, as the model sees it: JPEG at the phone's aspect, its longest side at most [MAX_SIDE]. */
class PhoneShot(val jpeg: ByteArray, val width: Int, val height: Int, /** Picture pixels per screen pixel. */ val scale: Float) {
    /** A point of the picture, ([x], [y]), on the screen. */
    fun onScreen(x: Int, y: Int): Pair<Int, Int> = (x / scale).toInt() to (y / scale).toInt()

    override fun toString(): String = "PhoneShot(${width}x$height, ${jpeg.size} bytes)"

    companion object {
        const val MAX_SIDE = 1280

        /** JPEG quality: text on the screen stays readable to the model at a fifth of PNG's size. */
        const val QUALITY = 75

        /** The scale that brings a screen of [width] × [height] within [MAX_SIDE]; never above 1. */
        fun scaleFor(width: Int, height: Int): Float = minOf(1f, MAX_SIDE.toFloat() / maxOf(width, height, 1))
    }
}

/** What stopped an act on the phone, in the words the model reads: it can look again, or hand over. */
class PhoneException(message: String) : Exception(message)
