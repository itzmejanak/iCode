package dev.icode.mobile.data.runtime

import dev.icode.mobile.data.workspace.ICodeStateHolder
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * What runs in the runtime on the user's behalf beyond a terminal (docs/runtime-foundation-plan.md
 * §3): the login environment it starts with, the managed processes that outlive the command that
 * started them, and gpg's agent, which outlives the command that needed it
 * (docs/gpg-signing-plan.md). One per process, as iCode's backend is.
 */
internal class RuntimeExecution(layout: RuntimeLayout, scope: CoroutineScope, stateHolder: ICodeStateHolder) {
    val rootfs: File = layout.rootfs
    val guest = GuestCommand(layout)
    val environment = GuestEnvironment(layout, guest)
    val processes = RuntimeProcesses(scope, layout::spawn, { list -> stateHolder.update { it.copy(processes = list) } }, ProcessLogs.of(layout.rootfs))
    val gpg = GpgBridge(stateHolder, scope, layout)
}
