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
)

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
