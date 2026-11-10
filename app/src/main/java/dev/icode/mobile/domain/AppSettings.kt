package dev.icode.mobile.domain

/** Values persisted by the existing app-private workspace_settings DataStore. */
enum class AppTheme { Dark, Light, Amoled }

enum class EditorFontFamily { PlexMono, SystemMono }

enum class AutoSaveMode(val delayMillis: Long?) {
    Off(null),
    AfterOneSecond(1_000),
    AfterFiveSeconds(5_000),
}

/**
 * The values each numeric setting may take: its control offers them, the store
 * accepts and reads back only them, and pinch zoom stays within the font sizes.
 */
object SettingLimits {
    /** Editor and terminal text, in sp. */
    val fontSizesSp = 9..30
    val indentSizes = 1..8
    /** UI text, in percent. */
    val uiTextScales = 100..150 step 5

    /** Where [percent] sits on its slider, from 0 to 1. */
    fun uiTextScalePosition(percent: Int): Float =
        (percent.coerceIn(uiTextScales.first, uiTextScales.last) - uiTextScales.first).toFloat() /
            (uiTextScales.last - uiTextScales.first)

    /** The UI text scale at [position] (0 to 1) of its slider, on a step. */
    fun uiTextScaleAt(position: Float): Int {
        val steps = (uiTextScales.last - uiTextScales.first) / uiTextScales.step
        return uiTextScales.first + Math.round(position.coerceIn(0f, 1f) * steps) * uiTextScales.step
    }

    /** A pinch [scale] of text set at [baseSp], kept within [fontSizesSp]. */
    fun zoomScale(scale: Float, baseSp: Int): Float =
        scale.coerceIn(fontSizesSp.first.toFloat() / baseSp, fontSizesSp.last.toFloat() / baseSp)
}

data class AppearanceSettings(
    val theme: AppTheme = AppTheme.Dark,
    /** 100 is the validated original UI; editor, Markdown and terminal sizes are independent. */
    val uiTextScalePercent: Int = 100,
    val compactLayout: Boolean = true,
    /** Lines around cards, menus and bars; off, surfaces stand apart by tone (docs/border-lines-plan.md). */
    val borderLines: Boolean = true,
    /** Opaque ARGB chosen over the theme's own accent; null keeps the theme's. App-wide. */
    val accentColor: Int? = null,
    /**
     * Each theme's variant by id (docs/theme-variants-plan.md D1). A theme without one is drawn as
     * iCode's own. Each theme keeps its own, so switching theme brings that theme's back.
     */
    val variants: Map<AppTheme, String> = emptyMap(),
    /**
     * Auto (docs/theme-variants-plan.md T6): Light while the phone is light, [nightTheme] while it is
     * dark, each in its own variant. Off, [theme] is shown whatever the phone is.
     */
    val followSystem: Boolean = false,
    /** The dark theme Auto shows at night: Dark or AMOLED. */
    val nightTheme: AppTheme = AppTheme.Dark,
) {
    /** The variant [theme] is drawn in, or null for iCode's own. */
    fun variantOf(theme: AppTheme = this.theme): String? = variants[theme]

    /** The theme on screen while the phone is [systemDark] or not. */
    fun shown(systemDark: Boolean): AppTheme = when {
        !followSystem -> theme
        systemDark -> nightTheme
        else -> AppTheme.Light
    }

    companion object {
        /** A theme Auto may show at night. */
        fun canBeNight(theme: AppTheme): Boolean = theme != AppTheme.Light
    }
}

/** What a theme variant's id may be (docs/theme-variants-plan.md D1). iCode's own variant is stored as no id. */
object ThemeVariantRules {
    /** iCode's own variant of each theme. */
    const val ICODE = "icode"

    private val ID = Regex("[a-z0-9][a-z0-9_]{0,39}")

    /** Lower-case letters, digits and `_`, up to 40, as the built-in variants' ids are. */
    fun isId(id: String): Boolean = ID.matches(id)

    /** [id] as it is kept: null for iCode's own, for none, and for anything that is not an id. */
    fun kept(id: String?): String? = id?.takeIf { it != ICODE && isId(it) }
}

/**
 * Which build of iCode this is (docs/environments-plan.md): each installs beside the others with its own
 * icon, and its theme accent is that icon's warm colour, the role Ember plays in the stable icon. Null:
 * Ember, the production icon's own.
 */
enum class AppEnvironment(val accent: Int?, val lightAccent: Int?) {
    /** The icon's warm grey; on Light, the same grey deepened to 4.5:1. */
    Development(0xFFB0A99A.toInt(), 0xFF757167.toInt()),

    /** The icon's lavender; on Light, its violet. */
    Staging(0xFF9C85C9.toInt(), 0xFF6E5AA0.toInt()),
    Preview(null, null),
    Beta(null, null),
    Stable(null, null),
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

    /**
     * Each theme's own accent in [environment]: its icon's colour — Ember for the production builds — deepened
     * on Light so it still reads on a light background.
     */
    fun themeAccent(theme: AppTheme, environment: AppEnvironment): Int = when (theme) {
        AppTheme.Light -> environment.lightAccent ?: LIGHT_EMBER
        AppTheme.Dark, AppTheme.Amoled -> environment.accent ?: presets.first().argb
    }

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
    /** The keys row above the keyboard (docs/editor-plan.md §4). */
    val keys: List<EditorKey> = EditorKey.entries,
    /** Off: the keyboard is asked not to suggest or autocorrect inside code. */
    val keyboardSuggestions: Boolean = false,
    /** The gutter's Git change markers and their sheet (docs/editor-plan.md §3). */
    val changeMarkers: Boolean = true,
    /** Typing an opening bracket or quote adds its closing one. */
    val autoCloseBrackets: Boolean = true,
)

/**
 * A key of the editor's keys row (docs/editor-plan.md §4): an editing action, or a symbol typed as
 * from the keyboard — [text], what it types. How each is drawn is the UI's (KeyFaces.kt).
 */
enum class EditorKey(val text: String? = null) {
    Tab, Outdent, Left, Up, Down, Right, Home, End, Undo, Redo,
    OpenBrace("{"), CloseBrace("}"), OpenParen("("), CloseParen(")"), OpenBracket("["), CloseBracket("]"),
    Semicolon(";"), Equals("="), DoubleQuote("\""), Quote("'"), Less("<"), Greater(">"), Slash("/"), Backslash("\\"),
    Bar("|"), Colon(":"), Comma(","), Period("."), Bang("!"), Ampersand("&"), Underscore("_"), Minus("-"), Plus("+"),
    Star("*"), Hash("#"), Dollar("$"), Percent("%"), Question("?"), At("@"), Tilde("~"), Caret("^"), Backtick("`"),
}

/** A key of the terminal's extra keys row; how each is drawn is the UI's (KeyFaces.kt). */
enum class TerminalExtraKey { Escape, Tab, Ctrl, Alt, Left, Up, Down, Right }

/**
 * A keys row as Settings arranges it (docs/editor-plan.md §4.1), the editor's or the terminal's: the
 * keys it shows, in the order the user put them, which is the order the row shows them.
 */
object KeyRows {
    /** [keys] with [key] taken out, or, when it is not among them, added at their end. */
    fun <K> toggled(keys: List<K>, key: K): List<K> = if (key in keys) keys - key else keys + key

    /** The keys of [all] that [keys] leaves out, in [all]'s order: what can be added. */
    fun <K> others(all: List<K>, keys: List<K>): List<K> = all.filterNot { it in keys }
}

/** How the terminal draws its cursor. Programs may still change it while they run. */
enum class TerminalCursorStyle(val label: String) { Block("Block"), Underline("Underline"), Bar("Bar") }

/**
 * App-wide defaults. The scrollback applies when a session starts; the rest
 * apply to running sessions too. The shell is root's login shell, which the
 * runtime's `/etc/passwd` records (`chsh` changes it).
 */
data class TerminalSettings(
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
}
