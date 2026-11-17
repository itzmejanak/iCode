package dev.icode.mobile.domain.phone

import dev.icode.mobile.domain.AssistantApproval
import dev.icode.mobile.domain.AssistantCall
import dev.icode.mobile.domain.AssistantPause
import dev.icode.mobile.domain.AssistantState
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.AssistantTurn
import dev.icode.mobile.domain.voice.Heard
import dev.icode.mobile.domain.voice.VoiceFrom
import dev.icode.mobile.domain.voice.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the agent goes, what it refuses, and what shows over other apps (docs/voice-and-phone-control.md). */
class PhoneRulesTest {
    private val apps = listOf(
        PhonePlaces.App("WhatsApp", "com.whatsapp", "com.whatsapp.Main"),
        PhonePlaces.App("WhatsApp Business", "com.whatsapp.w4b", "com.whatsapp.w4b.Main"),
        PhonePlaces.App("Settings", "com.android.settings", "com.android.settings.Settings"),
        PhonePlaces.App("Google Maps", "com.google.android.apps.maps", "Maps"),
    )

    @Test fun `an app is found by its name, exactly, by its start, or within it, and several alike are said`() {
        assertEquals("com.whatsapp", (PhonePlaces.app("whatsapp", apps) as PhonePlaces.Found.One).value.packageName)
        assertEquals("com.google.android.apps.maps", (PhonePlaces.app("Maps", apps) as PhonePlaces.Found.One).value.packageName)
        assertEquals("com.android.settings", (PhonePlaces.app("com.android.settings", apps) as PhonePlaces.Found.One).value.packageName)
        assertEquals(listOf("WhatsApp", "WhatsApp Business"), (PhonePlaces.app("whats", apps) as PhonePlaces.Found.Several).names)
        assertEquals(PhonePlaces.Found.None, PhonePlaces.app("Telegram", apps))
        assertEquals(PhonePlaces.Found.None, PhonePlaces.app("  ", apps))
    }

    @Test fun `a Settings page is opened by its name or a word for it, and Accessibility is not one`() {
        assertEquals("android.settings.WIFI_SETTINGS", PhonePlaces.settings("wifi")?.action)
        assertEquals("android.settings.WIFI_SETTINGS", PhonePlaces.settings("Wi-Fi settings")?.action)
        assertEquals("android.settings.DISPLAY_SETTINGS", PhonePlaces.settings("brightness")?.action)
        assertEquals("android.settings.BLUETOOTH_SETTINGS", PhonePlaces.settings("Bluetooth")?.action)
        assertNull(PhonePlaces.settings("Accessibility"))
        assertTrue(PhonePlaces.SETTINGS.none { it.action.contains("ACCESSIBILITY") })
    }

    @Test fun `links open for the web, calls, messages, mail, maps and stores, never a file or an intent`() {
        listOf("https://example.com", "tel:+15551234", "sms:+15551234", "mailto:a@b.c", "geo:0,0?q=cafe", "market://details?id=x").forEach { assertTrue(it, PhonePlaces.linkAllowed(it)) }
        listOf("file:///sdcard/a.txt", "intent://x#Intent;end", "content://x", "javascript:alert(1)").forEach { assertFalse(it, PhonePlaces.linkAllowed(it)) }
    }

    /** A screen of rows that tap; a label in [headings] is a heading that does nothing. */
    private fun screen(app: String, title: String? = null, vararg labels: String, headings: Set<String> = emptySet()) = PhoneScreen(
        id = 1, app = app, title = title, keyboard = false,
        elements = labels.mapIndexed { index, label ->
            PhoneElement(index + 1, "item", label, emptyList(), if (label in headings) emptySet() else setOf(PhoneAbility.Tap), PhoneBounds(0, 0, 1, 1), app, listOf(0, index))
        },
    )

    @Test fun `iCode itself, permission dialogs, the installer and Settings' Accessibility are the user's alone`() {
        val own = "dev.icode.mobile"
        assertTrue(PhoneRefusals.refusal(screen(own, "iCode", "Allow"), null, own)!!.contains("iCode itself"))
        assertTrue(PhoneRefusals.refusal(screen("com.google.android.permissioncontroller", null, "Allow"), null, own)!!.contains("permission dialog"))
        assertTrue(PhoneRefusals.refusal(screen("com.android.packageinstaller", null, "Install"), null, own)!!.contains("Installing"))
        val settings = screen("com.android.settings", "Settings", "Network & internet", "Accessibility", "Display")
        assertNull(PhoneRefusals.refusal(settings, settings.elements[0], own))
        assertTrue(PhoneRefusals.refusal(settings, settings.elements[1], own)!!.contains("Accessibility settings"))
        val inside = screen("com.android.settings", "Settings", "Navigate up", "Accessibility", "iCode assistant", headings = setOf("Accessibility"))
        assertTrue(PhoneRefusals.refusal(inside, inside.elements[2], own)!!.contains("Accessibility settings"))
        assertNull(PhoneRefusals.refusal(screen("com.whatsapp", "WhatsApp", "Send"), null, own))
    }

    @Test fun `a swipe that scrolls down moves the finger up, across the middle, and typed text is quoted for the shell`() {
        assertEquals(PhoneSwipes.Stroke(500, 1600, 500, 400), PhoneSwipes.across(1000, 2000, PhoneDirection.Down))
        assertEquals(PhoneSwipes.Stroke(500, 400, 500, 1600), PhoneSwipes.across(1000, 2000, PhoneDirection.Up))
        assertEquals(PhoneSwipes.Stroke(800, 1000, 200, 1000), PhoneSwipes.across(1000, 2000, PhoneDirection.Right))
        assertEquals("'on%smy%sway'", PhoneSwipes.inputText("on my way"))
        assertEquals("'it'\\''s'", PhoneSwipes.inputText("it's"))
    }

    @Test fun `a picture is at most 1280 on its longest side, and its points map back to the screen`() {
        assertEquals(0.5f, PhoneShot.scaleFor(1080, 2560), 0f)
        assertEquals(1f, PhoneShot.scaleFor(720, 1280), 0f)
        assertEquals(400 to 1000, PhoneShot(ByteArray(0), 540, 1280, 0.5f).onScreen(200, 500))
    }

    private val call = AssistantCall("c1", "phone_tap", "{}", "Tap [3]", AssistantToolKind.Change)

    private val quiet = VoiceState()

    private fun of(assistant: AssistantState, access: PhoneAccess, front: Boolean = false, voice: VoiceState = quiet, muted: Boolean = false) =
        PhoneOverlay.of(assistant, voice, access, front, muted)

    @Test fun `the bubble is out unless put away or iCode is in front, and the glow shows while a turn acts on the phone`() {
        val on = PhoneAccess(service = true, bubble = true)
        val idle = AssistantState()
        assertEquals(PhoneOverlay(bubble = true, glow = PhoneOverlay.Glow.Off, look = PhoneOverlay.Look.Idle, working = false, asking = false, step = null), of(idle, on))
        assertFalse(of(idle, on, front = true).bubble)
        val acting = AssistantState(turns = listOf(AssistantTurn("/w/app", "app", "s1", "Tap [3]", onPhone = true)))
        assertEquals(PhoneOverlay.Glow.On, of(acting, on).glow)
        val waiting = acting.changeTurn("/w/app", "s1") { it.copy(approval = AssistantApproval(call, "Tap [3]")) }
        assertEquals(PhoneOverlay.Glow.Dim, of(waiting, on).glow)
        assertTrue(of(waiting, on).asking)
        assertTrue(of(acting.changeTurn("/w/app", "s1") { it.copy(pause = AssistantPause("Type the password")) }, on).asking)
        val workspaceTurn = AssistantState(turns = listOf(AssistantTurn("/w/app", "app", "s1", "Thinking…")))
        assertEquals(PhoneOverlay.Glow.Off, of(workspaceTurn, on).glow)
        // Put away: the glow goes with the bubble.
        val away = on.copy(bubble = false)
        assertFalse(of(acting, away).bubble)
        assertEquals(PhoneOverlay.Glow.Off, of(acting, away).glow)
    }

    @Test fun `with chats at work at once, the bubble follows one asking, then the one on the phone`() {
        val on = PhoneAccess(service = true, bubble = true)
        val phone = AssistantTurn("/w/app", "app", "s1", "Tap [3]", onPhone = true)
        val home = AssistantTurn(null, "Home", "s2", "Thinking…")
        val shownHome = AssistantState(opened = true, session = "s2", turns = listOf(home, phone))
        assertEquals(phone, shownHome.focus)
        assertEquals(PhoneOverlay.Look.Acting, of(shownHome, on).look)
        assertEquals("Tap [3]", of(shownHome, on).step)
        val asked = shownHome.changeTurn(null, "s2") { it.copy(pause = AssistantPause("")) }
        assertEquals("s2", asked.focus?.session)
        assertEquals(PhoneOverlay.Glow.Dim, of(asked, on).glow)
        // Another chat shown: the bubble still follows the one at work, and sends to the shown one.
        val elsewhere = shownHome.copy(session = "s3")
        assertEquals(phone, elsewhere.focus)
        assertTrue(of(elsewhere, on).working)
    }

    @Test fun `the bubble shows the agent's activity before the microphone's, and listening only while it listens`() {
        val on = PhoneAccess(service = true, bubble = true)
        val idle = AssistantState()
        val thinking = AssistantState(turns = listOf(AssistantTurn("/w/app", "app", "s1", "Thinking…")))
        val acting = AssistantState(turns = listOf(AssistantTurn("/w/app", "app", "s1", "Tap [3]", onPhone = true)))
        val listening = VoiceState(heard = Heard(1, VoiceFrom.Bubble, "turn on", final = false))
        assertEquals(PhoneOverlay.Look.Idle, of(idle, on).look)
        assertEquals(PhoneOverlay.Look.Muted, of(idle, on, muted = true).look)
        assertEquals(PhoneOverlay.Look.Thinking, of(thinking, on, muted = true).look)
        assertEquals(PhoneOverlay.Look.Acting, of(acting, on, muted = true).look)
        assertEquals(PhoneOverlay.Look.Speaking, of(acting, on, voice = VoiceState(speaking = true)).look)
        assertEquals(PhoneOverlay.Look.Listening, of(acting, on, voice = listening).look)
        // The chat's microphone is not the bubble's; a hearing over is not listening.
        assertEquals(PhoneOverlay.Look.Idle, of(idle, on, voice = VoiceState(heard = Heard(1, VoiceFrom.Chat, "hi", final = false))).look)
        assertEquals(PhoneOverlay.Look.Idle, of(idle, on, voice = VoiceState(heard = Heard(1, VoiceFrom.Bubble, "hi", final = true))).look)
        assertTrue(of(thinking, on).working)
    }
}
