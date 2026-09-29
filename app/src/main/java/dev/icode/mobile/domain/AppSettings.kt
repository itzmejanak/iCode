package dev.icode.mobile.domain

/** Values persisted by the existing app-private workspace_settings DataStore. */
enum class AppTheme { Dark, Light, Amoled }

enum class EditorFontFamily { PlexMono, SystemMono }

enum class AutoSaveMode(val delayMillis: Long?) {
    Off(null),
    AfterOneSecond(1_000),
    AfterFiveSeconds(5_000),
}

data class AppearanceSettings(
    val theme: AppTheme = AppTheme.Dark,
    /** 100 is the validated original UI; editor, Markdown and terminal sizes are independent. */
    val uiTextScalePercent: Int = 100,
    val compactLayout: Boolean = true,
    /** Opaque ARGB chosen over the theme's own accent; null keeps the theme's. App-wide. */
    val accentColor: Int? = null,
) {
    /** The accent the app draws with: the one chosen, or the theme's own. */
    val effectiveAccent: Int get() = accentColor ?: AccentColorRules.themeAccent(theme)
}

/** The accent colours offered first, how a choice is written, and whether it reads on a background. */
object AccentColorRules {
    data class Preset(val name: String, val argb: Int)

    /** Ember is the theme's own accent; the rest span the wheel at a similar strength. */
    val presets = listOf(
        Preset("Ember", 0xFFFF6B2C.toInt()),
        Preset("Amber", 0xFFFFB020.toInt()),
        Preset("Lime", 0xFF9BD447.toInt()),
        Preset("Mint", 0xFF43D9A3.toInt()),
        Preset("Sky", 0xFF4FB3FF.toInt()),
        Preset("Indigo", 0xFF7C83FF.toInt()),
        Preset("Violet", 0xFFB67CFF.toInt()),
        Preset("Rose", 0xFFFF5C8A.toInt()),
    )

    /** Each theme's own accent: Ember, deepened on Light so it still reads on a light background. */
    fun themeAccent(theme: AppTheme): Int = if (theme == AppTheme.Light) LIGHT_EMBER else presets.first().argb

    private val LIGHT_EMBER = 0xFFB4470C.toInt()

    /** Below this an accent used for text or icons is hard to read (WCAG, large text and UI parts). */
    const val MIN_CONTRAST = 3.0

    fun nameOf(argb: Int): String = presets.firstOrNull { it.argb == opaque(argb) }?.name ?: "Custom"

    /** "#RRGGBB". */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** "#RRGGBB" or "RRGGBB", any case, as an opaque colour; null for anything else. */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.length != 6 || digits.any { it.digitToIntOrNull(16) == null }) return null
        return opaque(digits.toInt(16))
    }

    /** WCAG 2 contrast ratio of two colours, 1 (none) to 21 (black on white). */
    fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Int): Int =
        if (contrastRatio(background, BLACK) >= contrastRatio(background, WHITE)) BLACK else WHITE

    fun opaque(argb: Int): Int = argb or ALPHA

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((argb shr shift) and 0xFF) / 255.0
            return if (value <= 0.03928) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private const val ALPHA = 0xFF shl 24
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}

data class EditorSettings(
    val fontSizeSp: Int = 14,
    val fontFamily: EditorFontFamily = EditorFontFamily.PlexMono,
    val wordWrap: Boolean = true,
    val autoSave: AutoSaveMode = AutoSaveMode.AfterOneSecond,
    val lineNumbers: Boolean = true,
    val minimap: Boolean = false,
    val bracketMatching: Boolean = true,
)

enum class TerminalExtraKey(val label: String) {
    Escape("ESC"), Tab("TAB"), Ctrl("CTRL"), Alt("ALT"),
    Left("◀"), Up("▲"), Down("▼"), Right("▶"),
}

/** How the terminal draws its cursor. Programs may still change it while they run. */
enum class TerminalCursorStyle(val label: String) { Block("Block"), Underline("Underline"), Bar("Bar") }

/**
 * App-wide defaults. The shell path and scrollback apply when a session
 * starts; the rest apply to running sessions too.
 */
data class TerminalSettings(
    val shellPath: String = "/bin/bash",
    val fontSizeSp: Int = 13,
    val fontFamily: EditorFontFamily = EditorFontFamily.PlexMono,
    val scrollbackLines: Int = 10_000,
    val extraKeys: List<TerminalExtraKey> = TerminalExtraKey.entries,
    val cursorStyle: TerminalCursorStyle = TerminalCursorStyle.Block,
    /** Alert when a program rings the terminal bell while its session is out of sight. */
    val bellAlerts: Boolean = true,
    /** Alert when a command that ran at least [TerminalSettingRules.FINISH_ALERT_MILLIS] ends out of sight. */
    val finishAlerts: Boolean = true,
)

object TerminalSettingRules {
    val scrollbackChoices = listOf(1_000, 5_000, 10_000, 20_000)

    /** How long a command must run before its end is worth an alert (owner's decision). */
    const val FINISH_ALERT_MILLIS = 30_000L

    fun validShellPath(path: String): Boolean =
        path.startsWith('/') && path.length in 2..256 &&
            path.split('/').none { it == "." || it == ".." } &&
            path.none { it == '\u0000' || it == '\n' || it == '\r' }
}
