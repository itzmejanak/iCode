package dev.icode.mobile.data.android

import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.shellQuote
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.IOException

/**
 * Google's sdkmanager in the guest (docs/android-toolchain-plan.md §4.2), as the Android SDK's setups
 * run it: its stdin closed, so a licence it does not find accepted is declined, never waited on.
 */
internal class SdkManager(private val guest: GuestCommand) {
    /** Installs [packages], checking Google's checksums; its progress bar is said as [step]. Throws in sdkmanager's words. */
    suspend fun install(packages: List<String>, step: String, onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        onProgress(ToolSetupProgress(step, cancellable = true))
        val command = (listOf(AndroidSdk.GUEST_SDKMANAGER, "--sdk_root=${AndroidSdk.GUEST_ROOT}", "--install") + packages).joinToString(" ", transform = ::shellQuote)
        var said: String? = null
        // sdkmanager redraws its progress bar with carriage returns.
        val result = guest.streamRecords(null, listOf("/bin/sh", "-c", "$command </dev/null"), "sdkmanager", '\r'.code) { record ->
            record.decodeToString().split('\n').map(String::trim).filter(String::isNotEmpty).forEach { line ->
                val progress = PROGRESS.find(line)
                if (progress == null) {
                    said = line
                    onLine(line)
                } else {
                    val (percent, doing) = progress.destructured
                    onProgress(ToolSetupProgress(step, percent.toInt() / PERCENT, doing.ifBlank { null }, cancellable = true))
                }
            }
            true
        }
        if (result.exitCode != 0) {
            throw IOException("sdkmanager could not install ${packages.joinToString()}: ${result.failureDetail() ?: said ?: "exit ${result.exitCode}"}")
        }
    }

    /** What sdkmanager lists as installed, by package path: `ndk;27.0.12077973`. */
    suspend fun installed(): Set<String> {
        val ran = guest.capture(null, listOf(AndroidSdk.GUEST_SDKMANAGER, "--sdk_root=${AndroidSdk.GUEST_ROOT}", "--list_installed"), "sdkmanager")
        if (ran.exitCode != 0) throw IOException("sdkmanager could not list what is installed: ${ran.stderr.lines().lastOrNull(String::isNotBlank) ?: "exit ${ran.exitCode}"}")
        return listed(ran.stdout)
    }

    companion object {
        /** `[=====     ] 41% Downloading platform-36_r02.zip...`. */
        private val PROGRESS = Regex("""^\[[= ]*]\s*(\d+)%\s*(.*)$""")
        private const val PERCENT = 100f

        /** `--list_installed`'s table: `  ndk;27.0.12077973 | 27.0.12077973 | NDK (Side by side) 27.0.12077973 | ndk/27.0.12077973`. */
        fun listed(output: String): Set<String> = output.lineSequence()
            .filter { '|' in it }
            .map { it.substringBefore('|').trim() }
            .filter { it.isNotEmpty() && it != "Path" && !it.startsWith("-") }
            .toSet()
    }
}
