package dev.icode.mobile.data.runtime

import dev.icode.mobile.domain.ManagedProcess
import java.io.File
import kotlinx.coroutines.CoroutineScope

/**
 * What runs in the runtime on the user's behalf beyond a terminal (docs/runtime-foundation-plan.md
 * §3): the login environment it starts with, and the managed processes that outlive the command
 * that started them. One per process, as the workbench's backend is.
 */
internal class RuntimeExecution(layout: RuntimeLayout, scope: CoroutineScope, publish: (List<ManagedProcess>) -> Unit) {
    val rootfs: File = layout.rootfs
    val guest = GuestCommand(layout)
    val environment = GuestEnvironment(layout, guest)
    val processes = RuntimeProcesses(scope, layout::spawn, publish, ProcessLogs.of(layout.rootfs))
}
