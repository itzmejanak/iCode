package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.data.assistant.AssistantParameter
import dev.icode.mobile.data.assistant.AssistantParameterType
import dev.icode.mobile.data.assistant.AssistantPicture
import dev.icode.mobile.data.assistant.AssistantPlaceActor
import dev.icode.mobile.data.assistant.AssistantStep
import dev.icode.mobile.data.assistant.AssistantTool
import dev.icode.mobile.data.assistant.AssistantToolException
import dev.icode.mobile.data.phone.PhoneControl
import dev.icode.mobile.domain.AssistantElsewhere
import dev.icode.mobile.domain.AssistantPermissions
import dev.icode.mobile.domain.AssistantToolKind
import dev.icode.mobile.domain.phone.PhoneAct
import dev.icode.mobile.domain.phone.PhoneDirection
import dev.icode.mobile.domain.phone.PhoneException
import dev.icode.mobile.domain.phone.PhoneKey
import dev.icode.mobile.domain.phone.PhoneReading
import dev.icode.mobile.domain.phone.PhoneShot
import org.json.JSONObject

/**
 * The tools that see and operate the phone (docs/voice-and-phone-control.md), offered while phone control
 * is on. Reading never asks; an act asks about the app it is in ([AssistantPlaceActor]). The last act of
 * a reply reads the settled screen into its own result, so the model sees what it did in the same
 * step; a picture goes with it where the text cannot tell.
 */
internal class PhoneTools(private val phone: PhoneControl, private val step: AssistantStep) {
    fun all(): List<AssistantTool> = listOf(
        Read("phone_screen", "Reads the phone's screen now: its app, and each element that shows text or can be acted on, numbered. Numbers are good for this screen only.", picture = false),
        Read("phone_look", "Reads the screen with a picture of it, for what its text cannot tell: a game, a map, an image, a view without text.", picture = true),
        Act("phone_open_app", "Opens an app by its name, straight from its launch intent — faster than the launcher's pages.", listOf(text("name", "The app's name, as the launcher shows it"))) { a -> phone.openApp(a.text("name")) },
        Act(
            "phone_open",
            "Opens a link (web, tel:, sms:, mailto:, geo:, market:) in the app that opens it, or a Settings page by its name (Wi-Fi, Bluetooth, Display, Sound, Battery, Apps, Storage, Location, Notifications…), or an app's App info page.",
            listOf(text("link", "A link to open", required = false), text("settings", "A Settings page's name", required = false), text("app", "With settings: the app whose App info page opens", required = false)),
        ) { a -> a.textOrNull("link")?.let { phone.openLink(it) } ?: phone.openSettings(a.textOrNull("settings") ?: "App info", a.textOrNull("app")) },
        Act(
            "phone_tap",
            "Taps an element by its number; a long press with hold. Or a point of the last picture (x, y in its pixels), for what has no element.",
            listOf(int("element", "The element's number", required = false), int("x", "A point of the picture", required = false), int("y", "A point of the picture", required = false), flag("hold", "A long press")),
        ) { a -> tap(a) },
        Act(
            "phone_type",
            "Types text into a field by its number, in place of what it holds (or after it, with replace false); submit presses the keyboard's action (send, search, go).",
            listOf(int("element", "The field's number"), text("text", "The text"), flag("replace", "Replace what the field holds (default true)"), flag("submit", "Then press the keyboard's action")),
        ) { a -> phone.act(a.number("element"), PhoneAct.Type(a.text("text"), replace = !a.has("replace") || a.flag("replace"), submit = a.flag("submit"))) },
        Act(
            "phone_scroll",
            "Scrolls an element (a list) by its number, or the screen itself without one: up, down, left or right.",
            listOf(text("direction", "up, down, left or right"), int("element", "The list's number", required = false)),
        ) { a ->
            val direction = direction(a.text("direction"))
            if (a.has("element")) phone.act(a.number("element"), PhoneAct.Scroll(direction)) else phone.swipe(direction)
        },
        Act("phone_key", "Presses one of Android's keys: back, home, recents, notifications, quick settings.", listOf(text("key", "back, home, recents, notifications or quick settings"))) { a -> phone.key(key(a.text("key"))) },
        HandOver(),
    )

    /** A tool that reads the screen; [picture]: with a picture of it. */
    private inner class Read(override val name: String, override val description: String, private val picture: Boolean) : AssistantTool {
        override val parameters = emptyList<AssistantParameter>()
        override val kind = AssistantToolKind.Read

        override fun summary(arguments: JSONObject) = if (picture) "Look at the screen" else "Read the screen"

        override suspend fun run(arguments: JSONObject): String = guarded { screen(picture) }
    }

    /** A tool that acts on the phone, in the app the screen or its arguments name. */
    private inner class Act(
        override val name: String,
        override val description: String,
        override val parameters: List<AssistantParameter>,
        private val act: suspend (JSONObject) -> String,
    ) : AssistantTool, AssistantPlaceActor {
        override val kind = AssistantToolKind.Change

        override fun summary(arguments: JSONObject): String = PhoneSummaries.of(name, arguments, phone.latest)

        override fun place(arguments: JSONObject): AssistantElsewhere? = runCatching {
            (
                PhoneSummaries.app(name, arguments, phone.latest)?.let { (label, packageName) -> (label ?: phone.label(packageName)) to packageName }
                    ?: if (name == "phone_open_app") phone.appNamed(arguments.optString("name")) else null
                )?.let { (label, packageName) -> AssistantElsewhere(label, AssistantPermissions.appRoot(packageName), AssistantElsewhere.Kind.App) }
        }.getOrNull()

        override suspend fun run(arguments: JSONObject): String = guarded {
            val done = act(arguments)
            val last = step.current?.let { step.lastOf(it.id, ACTS) } != false
            if (last) "$done\n\n${screen(picture = false)}" else done
        }
    }

    /** The user takes a step themselves; the turn waits for Done, then reads the screen. */
    private inner class HandOver : AssistantTool {
        override val name = "phone_hand_over"
        override val description = "Hands a step to the user — a password, a payment, a CAPTCHA, a permission dialog, or a view Android withholds — and waits until they tap Done."
        override val parameters = listOf(text("reason", "What the user should do, in one sentence"))
        override val kind = AssistantToolKind.Read

        override fun summary(arguments: JSONObject) = "Hand over: ${arguments.optString("reason").take(SUMMARY)}"

        override suspend fun run(arguments: JSONObject): String = guarded {
            step.handOver(arguments.text("reason"))
            "The user says they are done.\n\n${screen(picture = false)}"
        }
    }

    /** The screen as text; with a picture when asked, or when it has nothing readable, for a model that sees. */
    private suspend fun screen(picture: Boolean): String {
        val screen = phone.screen()
        val text = PhoneReading.text(screen)
        if (!picture && screen.elements.isNotEmpty()) return text
        if (!step.seesPictures) return if (picture) "$text\n\n(This model does not see pictures: the text is all there is.)" else text
        val shot = phone.picture() ?: return "$text\n\n(Android gave no picture: the window is secure.)"
        step.leave(AssistantPicture(shot.jpeg))
        lastShot = shot
        return "$text\n\nA picture of the screen, ${shot.width}×${shot.height}, goes with this result: tap its points with phone_tap x, y."
    }

    private suspend fun tap(arguments: JSONObject): String {
        val hold = arguments.flag("hold")
        if (arguments.has("element")) return phone.act(arguments.number("element"), PhoneAct.Tap(hold))
        if (!arguments.has("x") || !arguments.has("y")) throw AssistantToolException("Give an element's number, or x and y of the picture")
        val shot = lastShot ?: throw AssistantToolException("Look at the screen first: x and y are the picture's")
        val (x, y) = shot.onScreen(arguments.optInt("x"), arguments.optInt("y"))
        return phone.tapAt(x, y, hold)
    }

    /** The last picture sent: its points are mapped to the screen's for a tap. */
    @Volatile private var lastShot: PhoneShot? = null

    private suspend fun guarded(block: suspend () -> String): String = try {
        if (!phone.on) throw AssistantToolException(OFF)
        block()
    } catch (refused: PhoneException) {
        throw AssistantToolException(refused.message ?: "The phone refused")
    }

    private fun direction(word: String): PhoneDirection =
        PhoneDirection.entries.firstOrNull { it.name.equals(word.trim(), ignoreCase = true) }
            ?: throw AssistantToolException("A direction is up, down, left or right")

    private fun key(word: String): PhoneKey =
        PhoneKey.entries.firstOrNull { it.word.equals(word.trim().replace('_', ' '), ignoreCase = true) }
            ?: throw AssistantToolException("A key is back, home, recents, notifications or quick settings")

    private fun JSONObject.number(name: String): Int {
        if (!has(name) || isNull(name)) throw AssistantToolException("Missing the “$name” argument")
        return optInt(name, -1).takeIf { it > 0 } ?: throw AssistantToolException("“$name” is an element's number")
    }

    companion object {
        /** The tools that act: the last of them in a reply reads the screen after it. */
        val ACTS = setOf("phone_open_app", "phone_open", "phone_tap", "phone_type", "phone_scroll", "phone_key")
        private const val SUMMARY = 60
        private const val OFF = "Phone control is off: the user turns it on in iCode's Settings › Assistant › Phone control"

        private fun text(name: String, description: String, required: Boolean = true) = AssistantParameter(name, description, AssistantParameterType.Text, required)
        private fun int(name: String, description: String, required: Boolean = true) = AssistantParameter(name, description, AssistantParameterType.Integer, required)
        private fun flag(name: String, description: String) = AssistantParameter(name, description, AssistantParameterType.Boolean, required = false)
    }
}
