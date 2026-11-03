package dev.icode.mobile.domain.phone

/**
 * Where the agent goes straight, by intent, never through the launcher's pages
 * (docs/phone-agent-plan.md §5.4): an app by its name, a link, a Settings page by its name.
 */
object PhonePlaces {
    /** An app the launcher lists: its name as the user sees it. */
    data class App(val label: String, val packageName: String, val activity: String)

    /** The app called [name]: exactly, else the one whose name starts so, else contains it; several alike are said. */
    fun app(name: String, apps: List<App>): Found<App> {
        val wanted = name.trim()
        if (wanted.isEmpty()) return Found.None
        for (match in listOf<(App) -> Boolean>(
            { it.label.equals(wanted, ignoreCase = true) || it.packageName.equals(wanted, ignoreCase = true) },
            { it.label.startsWith(wanted, ignoreCase = true) },
            { it.label.contains(wanted, ignoreCase = true) },
        )) {
            val found = apps.filter(match).distinctBy { it.packageName }
            if (found.size == 1) return Found.One(found.single())
            if (found.size > 1) return Found.Several(found.map { it.label }.sorted())
        }
        return Found.None
    }

    sealed interface Found<out T> {
        data class One<T>(val value: T) : Found<T>
        data class Several(val names: List<String>) : Found<Nothing>
        data object None : Found<Nothing>
    }

    /** A Settings page the agent opens by name: its words, and the intent action that opens it. */
    data class SettingsPage(val name: String, val action: String, val words: List<String> = emptyList())

    /**
     * The pages, as `android.provider.Settings` names their actions. Accessibility is not among them:
     * which services read the screen is the user's alone (§5.5).
     */
    val SETTINGS = listOf(
        SettingsPage("Settings", "android.settings.SETTINGS", listOf("main", "home")),
        SettingsPage("Wi-Fi", "android.settings.WIFI_SETTINGS", listOf("wifi", "wireless")),
        SettingsPage("Internet panel", "android.settings.panel.action.INTERNET_CONNECTIVITY", listOf("internet", "connectivity")),
        SettingsPage("Bluetooth", "android.settings.BLUETOOTH_SETTINGS"),
        SettingsPage("Mobile data", "android.settings.DATA_ROAMING_SETTINGS", listOf("roaming", "cellular", "sim")),
        SettingsPage("Data usage", "android.settings.DATA_USAGE_SETTINGS"),
        SettingsPage("Airplane mode", "android.settings.AIRPLANE_MODE_SETTINGS", listOf("flight")),
        SettingsPage("Hotspot", "android.settings.TETHER_SETTINGS", listOf("tethering")),
        SettingsPage("NFC", "android.settings.NFC_SETTINGS"),
        SettingsPage("Display", "android.settings.DISPLAY_SETTINGS", listOf("brightness", "screen", "dark theme")),
        SettingsPage("Sound", "android.settings.SOUND_SETTINGS", listOf("volume", "ringtone", "vibration")),
        SettingsPage("Volume panel", "android.settings.panel.action.VOLUME"),
        SettingsPage("Notifications", "android.settings.NOTIFICATION_SETTINGS"),
        SettingsPage("Battery", "android.intent.action.POWER_USAGE_SUMMARY", listOf("power")),
        SettingsPage("Battery saver", "android.settings.BATTERY_SAVER_SETTINGS"),
        SettingsPage("Apps", "android.settings.APPLICATION_SETTINGS", listOf("applications")),
        SettingsPage("Default apps", "android.settings.MANAGE_DEFAULT_APPS_SETTINGS"),
        SettingsPage("Storage", "android.settings.INTERNAL_STORAGE_SETTINGS"),
        SettingsPage("Location", "android.settings.LOCATION_SOURCE_SETTINGS", listOf("gps")),
        SettingsPage("Security", "android.settings.SECURITY_SETTINGS", listOf("lock screen", "screen lock")),
        SettingsPage("Privacy", "android.settings.PRIVACY_SETTINGS"),
        SettingsPage("Accounts", "android.settings.SYNC_SETTINGS", listOf("sync")),
        SettingsPage("Date and time", "android.settings.DATE_SETTINGS", listOf("time", "date", "time zone")),
        SettingsPage("Language", "android.settings.LOCALE_SETTINGS", listOf("languages", "locale")),
        SettingsPage("Keyboard", "android.settings.INPUT_METHOD_SETTINGS", listOf("input")),
        SettingsPage("Developer options", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS", listOf("developer")),
        SettingsPage("About phone", "android.settings.DEVICE_INFO_SETTINGS", listOf("about", "device info")),
        SettingsPage("Do not disturb", "android.settings.ZEN_MODE_SETTINGS", listOf("dnd", "zen")),
        SettingsPage("Cast", "android.settings.CAST_SETTINGS", listOf("screen cast")),
        SettingsPage("VPN", "android.settings.VPN_SETTINGS"),
    )

    /** The page called [name], by its name or one of its words; "App info" pages are an app's own ([APP_INFO]). */
    fun settings(name: String): SettingsPage? {
        val wanted = name.trim().lowercase().removeSuffix(" settings").replace("‑", "-")
        return SETTINGS.firstOrNull { it.name.lowercase() == wanted || wanted in it.words }
            ?: SETTINGS.firstOrNull { page -> page.name.lowercase().replace("-", "") == wanted.replace("-", "") }
    }

    /** An app's own App info page: the action, with `package:<name>` as its data. */
    const val APP_INFO = "android.settings.APPLICATION_DETAILS_SETTINGS"

    /** A link the agent opens: the web, a call's or a message's number, mail, a map, a store page. Never a file or an intent. */
    fun linkAllowed(link: String): Boolean {
        val scheme = link.substringBefore(':', "").lowercase()
        return scheme in LINK_SCHEMES
    }

    private val LINK_SCHEMES = setOf("http", "https", "tel", "sms", "smsto", "mailto", "geo", "market", "whatsapp", "tg", "spotify", "youtube")
}
