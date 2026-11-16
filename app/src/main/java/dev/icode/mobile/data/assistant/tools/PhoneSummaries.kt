package dev.icode.mobile.data.assistant.tools

import dev.icode.mobile.domain.phone.PhoneScreen
import org.json.JSONObject

/**
 * What a phone act's step and approval say (docs/voice-and-phone-control.md) — "Tap [12] “Send”" —
 * and the app it acts in, read from the screen it was asked on.
 */
internal object PhoneSummaries {
    /** The words of [tool]'s act with [arguments], on [screen]. Never throws. */
    fun of(tool: String, arguments: JSONObject, screen: PhoneScreen?): String = runCatching {
        val element = arguments.optInt("element", 0).takeIf { it > 0 }
        val named = element?.let { number -> "[$number]" + (screen?.elements?.firstOrNull { it.number == number }?.label?.takeIf { it.isNotEmpty() }?.let { " “${it.take(LABEL)}”" } ?: "") }
        when (tool) {
            "phone_open_app" -> "Open ${arguments.optString("name")}"
            "phone_open" -> arguments.optString("link").takeIf { it.isNotBlank() }?.let { "Open $it" }
                ?: arguments.optString("app").takeIf { it.isNotBlank() }?.let { "Open App info of $it" }
                ?: "Open Settings › ${arguments.optString("settings")}"
            "phone_tap" -> (if (arguments.optBoolean("hold")) "Hold " else "Tap ") +
                (named ?: "the picture at (${arguments.optInt("x")}, ${arguments.optInt("y")})")
            "phone_type" -> "Type “${arguments.optString("text").take(TEXT)}” into ${named ?: "a field"}" + if (arguments.optBoolean("submit")) " and submit" else ""
            "phone_scroll" -> "Scroll ${named ?: "the screen"} ${arguments.optString("direction")}"
            "phone_key" -> "Press ${arguments.optString("key")}"
            else -> tool
        }
    }.getOrDefault(tool)

    /**
     * The app [tool]'s act with [arguments] is in, on [screen]: its name where it is known here, and its
     * package; null for an app named by the act (phone_open_app), which the launcher finds.
     */
    fun app(tool: String, arguments: JSONObject, screen: PhoneScreen?): Pair<String?, String>? {
        val element = arguments.optInt("element", 0).takeIf { it > 0 }?.let { number -> screen?.elements?.firstOrNull { it.number == number } }
        return when (tool) {
            "phone_open_app" -> null
            "phone_open" -> if (arguments.optString("link").isNotBlank()) LINKS else null to SETTINGS
            "phone_key" -> if (arguments.optString("key").equals("back", ignoreCase = true)) screen?.app?.let { null to it } else null to SYSTEM_UI
            else -> (element?.app ?: screen?.app)?.takeIf { it.isNotEmpty() }?.let { null to it }
        }
    }

    /** A link opens in whatever app takes it: allowed once for every link of the chat. */
    private val LINKS = "Links" to "links"
    private const val SETTINGS = "com.android.settings"
    private const val SYSTEM_UI = "com.android.systemui"
    private const val LABEL = 40
    private const val TEXT = 40
}
