package dev.icode.mobile.domain.phone

// The phone's screen as the assistant reads it (docs/voice-and-phone-control.md): Android's accessibility
// tree, platform-free, turned into numbered elements and the text the model reads.

/** A rectangle on the screen, in pixels. */
data class PhoneBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val empty: Boolean get() = right <= left || bottom <= top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

/** One view as accessibility reports it: what the screen's text is made from. */
data class PhoneNode(
    /** Its class, `android.widget.Switch`: what its role is read from. */
    val className: String = "",
    val text: String? = null,
    /** What it says to a screen reader: an icon's only label. */
    val description: String? = null,
    /** An empty field's prompt. */
    val hint: String? = null,
    /** `com.whatsapp:id/send`: its resource name, a label of last resort. */
    val viewId: String? = null,
    val bounds: PhoneBounds = PhoneBounds(0, 0, 0, 0),
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val editable: Boolean = false,
    val scrollable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    /** A password field: its text is never read out. */
    val password: Boolean = false,
    /** On screen now; one that is not is left out, with what is under it. */
    val visible: Boolean = true,
    val children: List<PhoneNode> = emptyList(),
)

/** A window on the screen: an app's, a system one (the notification shade), or the keyboard. */
data class PhoneWindow(
    val kind: Kind,
    /** The package that drew it. */
    val app: String,
    /** Its title as Android reports it: the app's label, a dialog's title. */
    val title: String?,
    val active: Boolean,
    val root: PhoneNode?,
) {
    enum class Kind { App, System, Keyboard, Overlay }
}

/** What an element can be asked to do. */
enum class PhoneAbility(val word: String) {
    Tap("tap"),
    Hold("hold"),
    Type("type"),
    Scroll("scroll"),
}

/** An element the model names by its [number], on the screen it was read on. */
data class PhoneElement(
    val number: Int,
    /** "button", "switch", "field"… */
    val role: String,
    /** What it says, shortened; "" for one that says nothing (a list). */
    val label: String,
    /** "on", "off", "selected", "disabled", "focused", "password". */
    val states: List<String>,
    val abilities: Set<PhoneAbility>,
    val bounds: PhoneBounds,
    /** The package of the window it is in. */
    val app: String,
    /** Where it is: the window's index on the screen, then each child's index from its root. */
    val path: List<Int>,
)

/** The screen as read once: its number, the app in front, and its elements. */
data class PhoneScreen(
    /** Each read has its own: an element's number is good for this screen only. */
    val id: Int,
    /** The package in front. */
    val app: String,
    /** Its window's title. */
    val title: String?,
    val elements: List<PhoneElement>,
    val keyboard: Boolean,
    /** Elements past [PhoneReading.MAX_ELEMENTS] not listed. */
    val more: Int = 0,
)
