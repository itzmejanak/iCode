package dev.icode.mobile.domain.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screen as the model reads it (docs/phone-agent-plan.md §5.2), from trees as accessibility reports them. */
class PhoneReadingTest {
    private val box = PhoneBounds(0, 0, 100, 100)

    private fun node(className: String = "android.view.View", text: String? = null, children: List<PhoneNode> = emptyList(), clickable: Boolean = false, block: PhoneNode.() -> PhoneNode = { this }) =
        PhoneNode(className = className, text = text, bounds = box, clickable = clickable, children = children).block()

    private fun app(root: PhoneNode, title: String = "Settings", app: String = "com.android.settings") =
        PhoneWindow(PhoneWindow.Kind.App, app, title, active = true, root = root)

    @Test fun `each element that shows text or acts is numbered in reading order, as the model reads it`() {
        val root = node(children = listOf(
            node("android.widget.ImageButton") { copy(description = "Navigate up", clickable = true) },
            node("android.widget.TextView", "Network & internet"),
            node("android.widget.LinearLayout", clickable = true, children = listOf(
                node("android.widget.TextView", "Wi‑Fi"),
                node("android.widget.TextView", "Connected to Home"),
                node("android.widget.Switch") { copy(checkable = true, checked = true, clickable = true) },
            )),
            node("androidx.recyclerview.widget.RecyclerView") { copy(scrollable = true) },
        ))
        val screen = PhoneReading.screen(7, listOf(app(root)))
        assertEquals(
            """
            Screen 7 · Settings (com.android.settings)
            [1] button "Navigate up" · tap
            [2] text "Network & internet"
            [3] item "Wi‑Fi · Connected to Home" · tap
            [4] switch · on · tap
            [5] list · scroll
            """.trimIndent(),
            PhoneReading.text(screen),
        )
        assertEquals(listOf(0, 2, 2), screen.elements[3].path)
        assertEquals("com.android.settings", screen.elements[3].app)
    }

    @Test fun `a field says its prompt and what it holds, a password never, and the keyboard is noted, not read`() {
        val root = node(children = listOf(
            node("android.widget.EditText", "pizza") { copy(editable = true, hint = "Search", clickable = true, focused = true) },
            node("android.widget.EditText", "hunter2") { copy(editable = true, hint = "Password", password = true) },
            node("android.widget.EditText", "Message") { copy(editable = true, hint = "Message") },
        ))
        val keyboard = PhoneWindow(PhoneWindow.Kind.Keyboard, "com.google.android.inputmethod.latin", null, active = false, root = node(text = "q", clickable = true))
        val screen = PhoneReading.screen(1, listOf(keyboard, app(root, "Messages", "com.google.android.apps.messaging")))
        val text = PhoneReading.text(screen)
        assertTrue(text.contains("The keyboard is open."))
        assertTrue(text.contains("""[1] field "Search" · "pizza" · focused · tap/type"""))
        assertTrue(text.contains("""[2] field "Password" · password · type"""))
        assertFalse(text.contains("hunter2"))
        assertTrue(text.contains("""[3] field "Message" · empty · type"""))
        assertEquals(3, screen.elements.size)
        assertEquals("com.google.android.apps.messaging", screen.app)
    }

    @Test fun `hidden and off-screen views, overlays and what is past the depth guard are left out`() {
        var deep = node(text = "too deep")
        repeat(PhoneReading.MAX_DEPTH + 2) { deep = node(children = listOf(deep)) }
        val root = node(children = listOf(
            node(text = "hidden") { copy(visible = false) },
            node(text = "nowhere") { copy(bounds = PhoneBounds(0, 0, 0, 0)) },
            node(text = "shown"),
            deep,
        ))
        val bubble = PhoneWindow(PhoneWindow.Kind.Overlay, "dev.icode.mobile", "iCode bubble", active = false, root = node(text = "iCode", clickable = true))
        val screen = PhoneReading.screen(2, listOf(bubble, app(root)))
        assertEquals(listOf("shown"), screen.elements.map { it.label })
    }

    @Test fun `a disabled element says so and offers nothing, a button with no words its id, and a screen with none says so`() {
        val root = node(children = listOf(
            node("android.widget.Button", "Send") { copy(clickable = true, enabled = false) },
            node("android.widget.ImageButton") { copy(clickable = true, viewId = "com.whatsapp:id/voice_note_btn") },
        ))
        val screen = PhoneReading.screen(3, listOf(app(root, "WhatsApp", "com.whatsapp")))
        assertEquals("""[1] button "Send" · disabled""", PhoneReading.line(screen.elements[0]))
        assertEquals("""[2] button "voice note btn" · tap""", PhoneReading.line(screen.elements[1]))
        val empty = PhoneReading.screen(4, listOf(app(node())))
        assertTrue(PhoneReading.text(empty).contains("Nothing on it can be read as text."))
    }

    @Test fun `past the most elements the rest are counted, and long labels shortened`() {
        val root = node(children = List(PhoneReading.MAX_ELEMENTS + 5) { node(text = "x".repeat(PhoneReading.MAX_LABEL + 20)) })
        val screen = PhoneReading.screen(5, listOf(app(root)))
        assertEquals(PhoneReading.MAX_ELEMENTS, screen.elements.size)
        assertEquals(5, screen.more)
        assertEquals(PhoneReading.MAX_LABEL, screen.elements.first().label.length)
        assertTrue(PhoneReading.text(screen).endsWith("(5 more elements not listed: scroll, or look at the screen)"))
    }
}
