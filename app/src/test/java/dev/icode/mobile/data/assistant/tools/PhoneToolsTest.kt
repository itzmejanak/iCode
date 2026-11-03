package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantAppActor
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.phone.PhoneControl
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.phone.PhoneAbility
import dev.icode.mobile.domain.phone.PhoneAct
import dev.icode.mobile.domain.phone.PhoneBounds
import dev.icode.mobile.domain.phone.PhoneDirection
import dev.icode.mobile.domain.phone.PhoneElement
import dev.icode.mobile.domain.phone.PhoneException
import dev.icode.mobile.domain.phone.PhoneKey
import dev.icode.mobile.domain.phone.PhoneScreen
import dev.icode.mobile.domain.phone.PhoneShot
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The phone's tools (docs/phone-agent-plan.md §5.3, L2, L3, L6), over a scripted phone. */
class PhoneToolsTest {
    private fun element(number: Int, label: String, app: String = "com.whatsapp") =
        PhoneElement(number, "button", label, emptyList(), setOf(PhoneAbility.Tap), PhoneBounds(0, 0, 10, 10), app, listOf(0, number))

    private inner class FakePhone : PhoneControl {
        val done = mutableListOf<String>()
        var reads = 0
        var screenNow = PhoneScreen(1, "com.whatsapp", "WhatsApp", listOf(element(1, "Alex"), element(2, "Send")), keyboard = false)
        override var on = true
        override var latest: PhoneScreen? = null
        override fun label(packageName: String) = mapOf("com.whatsapp" to "WhatsApp", "com.android.settings" to "Settings")[packageName] ?: packageName
        override fun appNamed(name: String) = if (name.equals("whatsapp", true)) "WhatsApp" to "com.whatsapp" else null
        override suspend fun screen(): PhoneScreen {
            reads++
            return screenNow.copy(id = reads).also { latest = it }
        }
        override suspend fun act(number: Int, act: PhoneAct): String {
            if (number == 9) throw PhoneException("That is Android's permission dialog")
            done += "$act on $number"
            return "Tapped"
        }
        override suspend fun tapAt(x: Int, y: Int, hold: Boolean) = "Tapped ($x, $y)".also { done += it }
        override suspend fun swipe(direction: PhoneDirection) = "Scrolled the screen".also { done += "swipe $direction" }
        override suspend fun key(key: PhoneKey) = "Pressed ${key.word}".also { done += it }
        override suspend fun openApp(name: String) = "Opened $name".also { done += it }
        override suspend fun openLink(link: String) = "Opened $link".also { done += it }
        override suspend fun openSettings(name: String, app: String?) = "Opened $name".also { done += it }
        override suspend fun picture() = PhoneShot(byteArrayOf(7), 540, 1280, 0.5f)
    }

    private val phone = FakePhone()
    private var handedOver: String? = null
    private val step = AssistantStep { why -> handedOver = why }
    private val tools = PhoneTools(phone, step).all().associateBy { it.name }

    private fun tool(name: String): AssistantTool = tools.getValue(name)
    private fun call(id: String, tool: String) = AssistantCall(id, tool, "{}", tool, AssistantToolKind.Change)
    private fun args(vararg pairs: Pair<String, Any>) = JSONObject(pairs.toMap())

    @Test fun `reading never acts, and the screen is the model's text`() = runBlocking {
        assertEquals(AssistantToolKind.Read, tool("phone_screen").kind)
        val text = tool("phone_screen").run(JSONObject())
        assertTrue(text.startsWith("Screen 1 · WhatsApp (com.whatsapp)"))
        assertTrue(text.contains("[2] button \"Send\" · tap"))
        assertTrue(phone.done.isEmpty())
        assertNull(step.take())
    }

    @Test fun `only a reply's last act reads the screen after it, in its own result`() = runBlocking {
        step.calls = listOf(call("a", "phone_tap"), call("b", "phone_type"), call("c", "read_file"))
        step.current = step.calls[0]
        assertEquals("Tapped", tool("phone_tap").run(args("element" to 1)))
        step.current = step.calls[1]
        val typed = tool("phone_type").run(args("element" to 1, "text" to "on my way", "submit" to true))
        assertTrue(typed.startsWith("Tapped\n\nScreen 1"))
        assertEquals(listOf("Tap(hold=false) on 1", "Type(text=on my way, replace=true, submit=true) on 1"), phone.done)
        assertEquals(1, phone.reads)
    }

    @Test fun `a picture goes with the step when asked or when nothing reads, for a model that sees, and taps map back`() = runBlocking {
        assertTrue(tool("phone_look").run(JSONObject()).contains("does not see pictures"))
        assertNull(step.take())
        step.seesPictures = true
        assertTrue(tool("phone_look").run(JSONObject()).contains("540×1280"))
        assertEquals(listOf<Byte>(7), step.take()!!.bytes.toList())
        phone.screenNow = phone.screenNow.copy(elements = emptyList())
        tool("phone_screen").run(JSONObject())
        assertTrue(step.take() != null)
        tool("phone_tap").run(args("x" to 100, "y" to 200))
        assertEquals("Tapped (200, 400)", phone.done.last())
    }

    @Test fun `each act names the app it is in for the gate, from the screen or its own words`() = runBlocking {
        tool("phone_screen").run(JSONObject())
        fun app(name: String, vararg pairs: Pair<String, Any>) = (tool(name) as AssistantAppActor).app(args(*pairs))
        assertEquals("WhatsApp" to "com.whatsapp", app("phone_tap", "element" to 2))
        assertEquals("WhatsApp" to "com.whatsapp", app("phone_open_app", "name" to "WhatsApp"))
        assertEquals("Settings" to "com.android.settings", app("phone_open", "settings" to "Wi-Fi"))
        assertEquals("Links" to "links", app("phone_open", "link" to "https://example.com"))
        assertEquals("WhatsApp" to "com.whatsapp", app("phone_key", "key" to "back"))
        assertEquals("com.android.systemui", app("phone_key", "key" to "home")!!.second)
        assertEquals("Tap [2] “Send”", tool("phone_tap").summary(args("element" to 2)))
        assertEquals("Type “hi” into [1] “Alex” and submit", tool("phone_type").summary(args("element" to 1, "text" to "hi", "submit" to true)))
        assertFalse(tools.values.filter { it.kind == AssistantToolKind.Read }.any { it is AssistantAppActor })
    }

    @Test fun `a refusal and phone control off reach the model as what the user can do`() = runBlocking {
        val refused = runCatching { tool("phone_tap").run(args("element" to 9)) }.exceptionOrNull()
        assertTrue(refused is AssistantToolException && refused.message!!.contains("permission dialog"))
        phone.on = false
        val off = runCatching { tool("phone_screen").run(JSONObject()) }.exceptionOrNull()
        assertTrue(off!!.message!!.contains("Phone control is off"))
    }

    @Test fun `a hand-over waits for the user, then reads the screen`() = runBlocking {
        val text = tool("phone_hand_over").run(args("reason" to "Type your password"))
        assertEquals("Type your password", handedOver)
        assertTrue(text.startsWith("The user says they are done.\n\nScreen 1"))
    }

    @Test fun `scrolls, keys and places go as asked, and what is not one is said`() = runBlocking {
        tool("phone_scroll").run(args("direction" to "down"))
        tool("phone_scroll").run(args("direction" to "Up", "element" to 1))
        tool("phone_key").run(args("key" to "quick_settings"))
        tool("phone_open").run(args("settings" to "Bluetooth"))
        tool("phone_open").run(args("settings" to "App info", "app" to "WhatsApp"))
        assertEquals(listOf("swipe Down", "Scroll(direction=Up) on 1", "Pressed quick settings", "Opened Bluetooth", "Opened App info"), phone.done)
        assertTrue(runCatching { tool("phone_scroll").run(args("direction" to "sideways")) }.exceptionOrNull()!!.message!!.contains("up, down, left or right"))
    }
}
