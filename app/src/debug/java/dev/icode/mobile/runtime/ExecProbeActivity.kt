package dev.icode.mobile.runtime

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.util.Log
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeProcess
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Debug-only Cycle 1 packaging probe.
 *
 * Answers, from the app's real process and SELinux domain, the question the
 * whole Linux runtime depends on: which locations may this app execute a
 * packaged binary from? `run-as` cannot answer it — that shell runs in the
 * `runas_app` domain, which is allowed to execute app data files precisely
 * because it is a debugging affordance. Only the app's own `untrusted_app`
 * domain gives a truthful answer.
 *
 * Lives in the debug source set, so it can never ship.
 *
 *   adb shell am start -n dev.icode.mobile.debug/dev.icode.mobile.runtime.ExecProbeActivity
 *   adb logcat -d -s IcodeExecProbe
 */
class ExecProbeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "=== iCode exec probe ===")
        Log.i(TAG, "uid=${Process.myUid()} abi=${Build.SUPPORTED_ABIS.firstOrNull()}")
        Log.i(TAG, "sdk=${Build.VERSION.SDK_INT} device=${Build.MODEL}")

        val nativeDir = applicationInfo.nativeLibraryDir
        Log.i(TAG, "nativeLibraryDir=$nativeDir")

        val packaged = File(nativeDir, BINARY_NAME)
        Log.i(TAG, "packaged exists=${packaged.exists()} canExecute=${packaged.canExecute()}")
        Log.i(TAG, "RESULT nativeLibraryDir -> ${describe(execute(packaged, "native"))}")

        val copy = File(filesDir, "exec-probe-copy.bin")
        val staged = runCatching {
            packaged.copyTo(copy, overwrite = true)
            copy.setExecutable(true, true)
        }.isSuccess
        Log.i(TAG, "dataDir copy staged=$staged canExecute=${copy.canExecute()}")
        Log.i(TAG, "RESULT dataDir -> ${describe(execute(copy, "data"))}")
        copy.delete()

        probeProot()

        Log.i(TAG, "=== probe complete ===")
        finish()
    }

    /**
     * Proves PRoot runs from the native library directory and honours
     * PROOT_LOADER instead of writing a loader into the data directory.
     *
     * `-r /` traces a host binary, so the loader is exercised without needing a
     * root filesystem to be installed yet.
     */
    private fun probeProot() {
        val layout = RuntimeLayout(this)
        Log.i(TAG, "proot exists=${layout.proot.exists()} loader exists=${layout.prootLoader.exists()}")
        if (!layout.binariesPresent) {
            Log.w(TAG, "RESULT proot -> MISSING BINARIES")
            return
        }
        Log.i(TAG, "RESULT proot --version -> ${describe(runProot(layout, listOf("--version")))}")

        val traced = runProot(layout, listOf("-r", "/", "/system/bin/sh", "-c", "echo proot-loader-ok"))
        Log.i(TAG, "RESULT proot traced exec -> ${describe(traced)}")
    }

    private fun runProot(layout: RuntimeLayout, arguments: List<String>): Result<String> = runCatching {
        val process = RuntimeProcess.start(
            argv = listOf(layout.proot.absolutePath) + arguments,
            environment = layout.prootEnvironment(),
            workingDirectory = filesDir.absolutePath,
        )
        val output = process.output.bufferedReader().use { it.readText() }
        val exit = process.waitFor()
        process.destroy()
        "exit=$exit output=${output.trim().take(400)}"
    }

    private fun execute(binary: File, argument: String): Result<String> = runCatching {
        val process = ProcessBuilder(binary.absolutePath, argument)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("timed out")
        }
        "exit=${process.exitValue()} output=${output.trim()}"
    }

    private fun describe(result: Result<String>): String = result.fold(
        onSuccess = { "EXECUTED ($it)" },
        onFailure = { "REFUSED (${it.javaClass.simpleName}: ${it.message})" },
    )

    private companion object {
        const val TAG = "IcodeExecProbe"
        const val BINARY_NAME = "libexectest.so"
        const val TIMEOUT_SECONDS = 15L
    }
}
