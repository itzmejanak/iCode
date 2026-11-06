package dev.icode.mobile.ui.terminal

import com.termux.terminal.TerminalOutput

/** What a test's emulator writes back, rings or copies goes nowhere: the screen is what is checked. */
internal object NoOutput : TerminalOutput() {
    override fun write(data: ByteArray?, offset: Int, count: Int) = Unit
    override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit
    override fun onCopyTextToClipboard(text: String?) = Unit
    override fun onPasteTextFromClipboard() = Unit
    override fun onBell() = Unit
    override fun onColorsChanged() = Unit
}
