package dev.icode.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.termux.terminal.TerminalSession
import dev.icode.mobile.data.runtime.terminalReplay
import dev.icode.mobile.data.workspace.AndroidICodeGateway
import dev.icode.mobile.data.workspace.ICodeProcess
import dev.icode.mobile.domain.TerminalSettings
import dev.icode.mobile.ui.terminal.TerminalSessionSource

/** A screen's hold on the process's one iCode backend ([ICodeProcess]); the work outlives the screen. */
internal class AndroidICodeViewModel(private val context: Context) : ViewModel() {
    val gateway: AndroidICodeGateway = ICodeProcess.attach(context)

    /** The gateway's terminal sessions, for the view that draws them. */
    val terminalSessions: TerminalSessionSource = object : TerminalSessionSource {
        override fun session(id: String): TerminalSession? = gateway.terminalSession(id)
        override fun observe(id: String, onScreenChanged: () -> Unit): AutoCloseable =
            gateway.observeTerminal(id, onScreenChanged)
        override fun replay(printed: String, settings: TerminalSettings): TerminalSession = terminalReplay(context, printed, settings)
    }

    override fun onCleared() {
        ICodeProcess.detach()
    }
}

internal class AndroidICodeViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AndroidICodeViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AndroidICodeViewModel(applicationContext) as T
    }
}
