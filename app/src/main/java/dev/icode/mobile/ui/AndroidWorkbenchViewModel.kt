package dev.icode.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.termux.terminal.TerminalSession
import dev.icode.mobile.data.workspace.AndroidWorkbenchGateway
import dev.icode.mobile.ui.terminal.TerminalSessionSource

internal class AndroidWorkbenchViewModel(context: Context) : ViewModel() {
    val gateway = AndroidWorkbenchGateway(context.applicationContext)

    /** The gateway's terminal sessions, for the view that draws them. */
    val terminalSessions: TerminalSessionSource = object : TerminalSessionSource {
        override fun session(id: String): TerminalSession? = gateway.terminalSession(id)
        override fun observe(id: String, onScreenChanged: () -> Unit): AutoCloseable =
            gateway.observeTerminal(id, onScreenChanged)
    }

    override fun onCleared() {
        gateway.close()
    }
}

internal class AndroidWorkbenchViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val applicationContext = context.applicationContext

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AndroidWorkbenchViewModel::class.java))
        @Suppress("UNCHECKED_CAST")
        return AndroidWorkbenchViewModel(applicationContext) as T
    }
}
