package dev.icode.mobile.data.workspace

import android.content.Context

/**
 * The process's one workbench (docs/long-operations-plan.md §6). The work a user starts — a
 * delete, an import, a clone, the runtime install — belongs to the process, not to the screen it
 * was started from: leaving iCode with Back or swiping it from Recents must not cut it off
 * half-way. Screens attach and detach; the foreground service keeps the process while work runs,
 * as it already did for terminal sessions ([dev.icode.mobile.data.runtime.TerminalSessionManager]).
 */
object WorkbenchProcess {
    private var gateway: AndroidWorkbenchGateway? = null
    private var screens = 0

    /** A screen opens: the workbench, made on the first. */
    fun attach(context: Context): AndroidWorkbenchGateway = synchronized(this) {
        screens++
        gateway ?: AndroidWorkbenchGateway(context.applicationContext).also { gateway = it }
    }

    /** A screen closed for good; when none is left, what serves a screen stops. */
    fun detach() = synchronized(this) {
        screens = (screens - 1).coerceAtLeast(0)
        if (screens == 0) gateway?.screensGone()
    }
}
