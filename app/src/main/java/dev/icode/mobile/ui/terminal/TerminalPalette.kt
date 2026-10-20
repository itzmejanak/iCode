package dev.icode.mobile.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import dev.icode.mobile.designsystem.IcodePalette
import java.util.Properties
import java.util.WeakHashMap

/**
 * Gives terminal output the app's colors.
 *
 * Termux keeps one color scheme for every emulator, and each emulator copies it
 * when it starts or resets its colors. So the scheme follows the app palette,
 * and a session's colors are reset whenever it is shown under a palette it has
 * not been colored with yet — which also covers sessions started before any
 * terminal was on screen.
 */
internal object TerminalPalette {
    private var applied: IcodePalette? = null
    private val colored = WeakHashMap<TerminalSession, IcodePalette>()

    fun colorFor(session: TerminalSession, palette: IcodePalette) {
        if (applied != palette) {
            TerminalColors.COLOR_SCHEME.updateWith(schemeOf(palette))
            applied = palette
        }
        if (colored[session] != palette) {
            session.emulator?.mColors?.reset()
            colored[session] = palette
        }
    }

    /** The cursor slot is also the shell prompt's colour (`ShellPrompt.ACCENT_COLOR_INDEX`): both are the accent. */
    internal fun schemeOf(palette: IcodePalette) = Properties().apply {
        setProperty("background", hex(palette.backgroundDeep))
        setProperty("foreground", hex(palette.textPrimary))
        setProperty("cursor", hex(palette.accent))
        palette.terminalAnsi.forEachIndexed { index, color -> setProperty("color$index", hex(color)) }
    }

    private fun hex(color: Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
}
