package dev.icode.mobile.runtime

import android.app.Activity
import android.os.Bundle
import android.util.Log
import dev.icode.mobile.data.runtime.RuntimeInstaller
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcess
import dev.icode.mobile.domain.RuntimeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only runtime probe: installs the root filesystem and exercises it.
 *
 *   adb shell am start --user 0 \
 *     -n dev.icode.mobile.debug/dev.icode.mobile.runtime.RuntimeProbeActivity \
 *     --es workspace /storage/emulated/0/Download
 *   adb logcat -s IcodeRuntimeProbe
 */
class RuntimeProbeActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // These probes have no UI. Leaving one resumed shows the user a blank
        // window, so hand the screen back immediately and log from the coroutine.
        moveTaskToBack(true)
        val workspace = intent.getStringExtra("workspace")
        scope.launch {
            val layout = RuntimeLayout(this@RuntimeProbeActivity)
            Log.i(TAG, "=== runtime probe ===")
            Log.i(TAG, "installed=${layout.isInstalled} rootfs=${layout.rootfs.absolutePath}")

            if (!layout.isInstalled) {
                RuntimeInstaller(this@RuntimeProbeActivity).install().collect { state ->
                    when (state) {
                        is RuntimeState.Downloading -> Log.i(
                            TAG,
                            "downloading ${state.receivedBytes / (1024 * 1024)}MB" +
                                (state.fraction?.let { " (${(it * 100).toInt()}%)" } ?: ""),
                        )
                        is RuntimeState.Installing -> Log.i(TAG, "installing: ${state.label}")
                        is RuntimeState.Ready -> Log.i(TAG, "READY: ${state.release}")
                        is RuntimeState.Failed -> Log.e(TAG, "FAILED: ${state.reason}")
                        else -> Log.i(TAG, "state: $state")
                    }
                }
            }

            if (layout.isInstalled) {
                runGuest(layout, "uname -a", workspace)
                runGuest(layout, "cat /etc/os-release | head -2", workspace)
                runGuest(layout, "id", workspace)
                if (workspace != null) {
                    runGuest(layout, "ls -la /workspace | head -5", workspace)
                    val probeFile = "/workspace/.icode-runtime-probe"
                    runGuest(layout, "echo written-from-guest > $probeFile && cat $probeFile", workspace)
                }
            }
            if (workspace != null) probeSymlinkSupport(layout, workspace)
            Log.i(TAG, "=== probe complete ===")
            finishAndRemoveTask()
        }
    }

    private fun runGuest(layout: RuntimeLayout, script: String, workspace: String?) {
        val result = runCatching {
            val process = RuntimeProcess.start(
                argv = layout.prootCommand(
                    guestCommand = listOf("/bin/sh", "-c", script),
                    workspaceHostPath = workspace,
                ),
                environment = layout.prootEnvironment(),
                workingDirectory = filesDir.absolutePath,
            )
            val output = process.output.bufferedReader().use { it.readText() }
            val exit = process.waitFor()
            process.destroy()
            "exit=$exit | ${output.trim().replace('\n', ' ').take(300)}"
        }
        Log.i(TAG, "[$script] ${result.getOrElse { "ERROR ${it.message}" }}")
    }

    /**
     * Android's FUSE external storage may not support symlinks or hard links.
     * PRoot's --link2symlink turns every hard link into a symlink, so if
     * symlinks are unsupported there, Git's pack finalisation fails.
     */
    private fun probeSymlinkSupport(layout: RuntimeLayout, workspace: String) {
        listOf(true, false).forEach { linkToSymlink ->
            val label = if (linkToSymlink) "with --link2symlink" else "without --link2symlink"
            val script = "rm -rf /workspace/.icode-linktest; mkdir -p /workspace/.icode-linktest && " +
                "cd /workspace/.icode-linktest && echo hi > a && " +
                "(ln a b && echo HARDLINK_OK || echo HARDLINK_FAIL); " +
                "(ln -s a c && echo SYMLINK_OK || echo SYMLINK_FAIL); " +
                "(mv a d && echo RENAME_OK || echo RENAME_FAIL); " +
                "cd / ; rm -rf /workspace/.icode-linktest"
            val argv = buildList {
                add(layout.proot.absolutePath)
                if (linkToSymlink) add("--link2symlink")
                add("-0"); add("-r"); add(layout.rootfs.absolutePath)
                add("-b"); add("/dev"); add("-b"); add("/proc"); add("-b"); add("/sys")
                listOf("/system", "/apex", "/vendor", "/product").forEach { host ->
                    if (java.io.File(host).exists()) { add("-b"); add(host) }
                }
                add("-b"); add("$workspace:/workspace")
                add("-w"); add("/workspace")
                add("/bin/sh"); add("-c"); add(script)
            }
            val result = runCatching {
                val process = RuntimeProcess.start(
                    argv = argv,
                    environment = layout.prootEnvironment(),
                    workingDirectory = layout.runtimeRoot.absolutePath,
                )
                val output = process.output.bufferedReader().use { it.readText() }
                process.waitFor()
                process.destroy()
                output.trim().replace('\n', ' ')
            }.getOrElse { "ERROR ${it.message}" }
            Log.i(TAG, "RESULT links $label -> $result")
        }
    }

    private companion object {
        const val TAG = "IcodeRuntimeProbe"
    }
}
