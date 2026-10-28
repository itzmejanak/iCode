package dev.icode.mobile.data.android

import android.app.ActivityManager
import android.content.Context
import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.hexDigest
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.GuestContext
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeLicences
import dev.icode.mobile.data.runtime.RuntimeToolSetup
import dev.icode.mobile.data.runtime.fetchPinned
import dev.icode.mobile.data.runtime.readManifest
import dev.icode.mobile.domain.ToolSetupProgress
import dev.icode.mobile.domain.files.ArchiveFormat
import java.io.File
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The Android SDK tool's setup (docs/android-toolchain-plan.md §4.2), after apt has installed JDK 17:
 *
 * 1. Google's **Command-line Tools**, pinned with Google's SHA-1, into `cmdline-tools/latest`;
 * 2. the **licence** the user accepted, recorded as sdkmanager records it;
 * 3. **sdkmanager** installs SDK Platform 36 and Build-Tools 36.0.0, checking Google's checksums;
 * 4. the build tools' x86-64 host programs are replaced by **ARM64 builds** of the same AOSP source;
 * 5. **Gradle's settings** ([GradleSettings]);
 * 6. each is **verified** by running it.
 *
 * Everything but the environment lives in the guest home, which Repair keeps; the environment
 * (`ANDROID_HOME`, `PATH`) is the system's, written by [configure] on every install.
 */
internal class AndroidSdkSetup(private val layout: RuntimeLayout, private val context: Context) : RuntimeToolSetup {
    override val id: String = ID

    private val guest = GuestCommand(layout, GuestContext.Command)
    private val sdkmanager = SdkManager(guest)
    private val sdk = AndroidSdk.host(layout)
    private val manifest by lazy { readManifest(context) }
    private val commandLineTools by lazy { manifest.archive(COMMAND_LINE_TOOLS) }
    private val hostTools by lazy { manifest.archive(HOST_TOOLS) }
    private val licenceHash by lazy { licenceHash(RuntimeLicences.text(context, AndroidSdk.LICENCE)) }
    private val gradleBlock by lazy { GradleSettings.block(GradleSettings.heapMegabytes(totalMemory())) }

    override val receipt: String by lazy {
        (
            listOf(
                "${AndroidSdk.LICENCE} $licenceHash",
                "cmdline-tools ${commandLineTools.checksum.hex}",
                "host-tools ${hostTools.checksum.hex}",
            ) + AndroidSdk.PACKAGES + "settings ${Sha256.hex(gradleBlock + GradleSettings.INIT_SCRIPT + PROFILE)}"
        ).joinToString("\n", postfix = "\n")
    }

    override fun measure(): Map<String, Long> =
        listOf(commandLineTools, hostTools).associate { it.fileName to (it.bytes ?: error("The runtime manifest pins no size for ${it.fileName}")) } +
            AndroidSdk.PACKAGES.associateWith { manifest.packageBytes[it] ?: error("The runtime manifest pins no size for $it") }

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        // Kept until the whole setup has succeeded: a retry after a later step fails downloads nothing again.
        val toolsZip = File(context.cacheDir, "$COMMAND_LINE_TOOLS.zip")
        val hostZip = File(context.cacheDir, "$HOST_TOOLS.zip")
        fetchPinned(commandLineTools, toolsZip, "Downloading Android SDK Command-line Tools", onProgress)
        fetchPinned(hostTools, hostZip, "Downloading build tools for ARM64", onProgress)
        withContext(NonCancellable) {
            onProgress(ToolSetupProgress("Unpacking Android SDK Command-line Tools", cancellable = false))
            unpackCommandLineTools(toolsZip)
            // As sdkmanager records an accepted licence: its hash, after a newline.
            File(sdk, "licenses").mkdirs()
            File(sdk, "licenses/${AndroidSdk.LICENCE}").writeText("\n$licenceHash")
        }
        val step = "Installing SDK Platform 36 and Build-Tools ${AndroidSdk.BUILD_TOOLS}"
        sdkmanager.install(AndroidSdk.PACKAGES, step, onProgress, onLine)
        if (!File(sdk, "platforms/${AndroidSdk.PLATFORM}/android.jar").isFile || !File(sdk, "build-tools/${AndroidSdk.BUILD_TOOLS}").isDirectory) {
            throw IOException("sdkmanager did not install SDK Platform 36 and Build-Tools ${AndroidSdk.BUILD_TOOLS}")
        }
        withContext(NonCancellable) {
            onProgress(ToolSetupProgress("Setting up build tools for ARM64", cancellable = false))
            replaceHostTools(hostZip)
            writeGradleSettings()
        }
        onProgress(ToolSetupProgress("Verifying the Android SDK", cancellable = true))
        verify()
        toolsZip.delete()
        hostZip.delete()
    }

    override fun configure() {
        File(layout.rootfs, PROFILE_PATH).apply { parentFile?.mkdirs() }.writeText(PROFILE)
    }

    override suspend fun remove() {
        deleteTree(sdk.toPath())
        File(layout.rootfs, PROFILE_PATH).delete()
        val gradle = File(layout.home, ".gradle")
        val properties = File(gradle, "gradle.properties")
        if (properties.isFile) {
            // What is left is the user's; when nothing is, the file was only iCode's.
            val rest = GradleSettings.withBlock(properties.readText(), null)
            if (rest.isEmpty()) properties.delete() else properties.writeText(rest)
        }
        File(gradle, "init.d/${GradleSettings.INIT_SCRIPT_NAME}").delete()
    }

    /** The zip holds one folder, `cmdline-tools/`; sdkmanager expects to find itself at `cmdline-tools/latest`. */
    private fun unpackCommandLineTools(zip: File) {
        val folder = File(sdk, "cmdline-tools")
        val staging = File(folder, ".latest-unpacking")
        deleteTree(staging.toPath())
        Archives.unpack(zip, ArchiveFormat.Zip, staging, Unpacking(ArchiveLinks.AsWritten, place = { name -> name.removePrefix("cmdline-tools/").takeIf { it != name && it.isNotEmpty() } }))
        val latest = File(folder, "latest")
        deleteTree(latest.toPath())
        if (!staging.renameTo(latest)) throw IOException("Could not unpack Android SDK Command-line Tools")
    }

    private fun replaceHostTools(zip: File) {
        val placed = Archives.unpack(zip, ArchiveFormat.Zip, File(sdk, "build-tools/${AndroidSdk.BUILD_TOOLS}"), Unpacking(ArchiveLinks.AsWritten, place = ::hostTool))
        if (placed.entries != AndroidSdk.HOST_TOOLS.size.toLong()) throw IOException("The pinned ARM64 build tools archive lacks some of ${AndroidSdk.HOST_TOOLS}")
    }

    private fun writeGradleSettings() {
        val gradle = File(layout.home, ".gradle")
        val properties = File(gradle, "gradle.properties")
        gradle.mkdirs()
        properties.writeText(GradleSettings.withBlock(if (properties.isFile) properties.readText() else "", gradleBlock))
        File(gradle, "init.d").mkdirs()
        File(gradle, "init.d/${GradleSettings.INIT_SCRIPT_NAME}").writeText(GradleSettings.INIT_SCRIPT)
    }

    /** Each part runs, as a build will run it: JDK 17 (it says its version on stderr), aapt2 and sdkmanager. */
    private suspend fun verify() {
        listOf(listOf("java", "-version"), listOf("${AndroidSdk.GUEST_BUILD_TOOLS}/aapt2", "version"), listOf(AndroidSdk.GUEST_SDKMANAGER, "--version"))
            .forEach { argv ->
                val ran = guest.capture(null, argv, "verify")
                val said = ran.stderr.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty)
                if (ran.exitCode != 0 || (argv.first() == "java" && "version \"17." !in ran.stderr)) {
                    throw IOException("${argv.first().substringAfterLast('/')} does not run as it should: ${said ?: "exit ${ran.exitCode}"}")
                }
            }
    }

    private fun totalMemory(): Long =
        ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem

    companion object {
        /** [dev.icode.mobile.domain.RuntimeTools.AndroidSdk]'s setup. */
        const val ID = "android-sdk"

        /** The pins in `assets/runtime/manifest.json`. */
        const val COMMAND_LINE_TOOLS = "android-cmdline-tools"
        const val HOST_TOOLS = "android-host-tools-arm64"

        const val PROFILE_PATH = "etc/profile.d/icode-android-sdk.sh"
        val PROFILE = """
            # ${RuntimeLayout.MANAGED} Android SDK (Settings › Runtime Tools), removed with it.
            export ANDROID_HOME=${AndroidSdk.GUEST_ROOT}
            export PATH="${'$'}PATH:${'$'}ANDROID_HOME/cmdline-tools/latest/bin:${'$'}ANDROID_HOME/build-tools/${AndroidSdk.BUILD_TOOLS}"
        """.trimIndent() + "\n"

        /** What sdkmanager writes for an accepted licence: the SHA-1 of its text, exactly as Google publishes it. */
        fun licenceHash(text: String): String = hexDigest(text.toByteArray(Charsets.UTF_8), DigestAlgorithm.Sha1)

        /** Where an entry of the ARM64 archive goes in the build tools: only the host programs they replace. */
        fun hostTool(entry: String): String? = entry.removePrefix("build-tools/").takeIf { "build-tools/$it" == entry && it in AndroidSdk.HOST_TOOLS }
    }
}
