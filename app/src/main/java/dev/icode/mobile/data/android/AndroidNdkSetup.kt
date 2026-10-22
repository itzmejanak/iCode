package dev.icode.mobile.data.android

import android.content.Context
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.runtime.ArchiveExtractor
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeToolSetup
import dev.icode.mobile.data.runtime.fetchPinned
import dev.icode.mobile.data.runtime.readManifest
import dev.icode.mobile.data.runtime.shellQuote
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * An NDK's setup, one per pinned [revision] (docs/android-ndk-plan.md §3.2): android-ndk-custom's
 * build of Google's NDK, its toolchain built for ARM64 Linux, unpacked from the app — never through
 * PRoot — into `ndk/<revision>`, Android Studio's side-by-side place, where its own `package.xml` makes
 * sdkmanager and the Android Gradle plugin list it as installed. Whatever was there before goes:
 * Google's x86-64 NDK fetched by a build, or a setup cut short.
 */
internal class AndroidNdkSetup(private val layout: RuntimeLayout, private val context: Context, private val revision: String) : RuntimeToolSetup {
    override val id: String = RuntimeTools.ndkId(revision)

    private val guest = GuestCommand(layout)
    private val folder = File(AndroidSdk.host(layout), AndroidSdk.ndk(revision))
    private val pin by lazy { readManifest(context).archive(id) }

    override val receipt: String by lazy { "ndk;$revision ${pin.checksum.hex}\n" }

    override fun measure(): Map<String, Long> = mapOf(pin.fileName to (pin.bytes ?: error("The runtime manifest pins no size for ${pin.fileName}")))

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        // Kept until the whole setup has succeeded: a retry after a later step fails downloads nothing again.
        val archive = File(context.cacheDir, pin.fileName)
        fetchPinned(pin, archive, "Downloading NDK $revision", onProgress)
        withContext(NonCancellable) { unpack(archive, onProgress) }
        onProgress(ToolSetupProgress("Verifying NDK $revision", cancellable = true))
        verify()
        archive.delete()
    }

    override fun configure() = Unit

    override suspend fun remove() {
        deleteTree(folder.toPath())
    }

    /** Into a staging folder beside it, then in place of whatever `ndk/<revision>` held. */
    private fun unpack(archive: File, onProgress: (ToolSetupProgress) -> Unit) {
        val step = "Unpacking NDK $revision"
        onProgress(ToolSetupProgress(step, cancellable = false))
        val staging = File(folder.parentFile, ".$revision-unpacking")
        deleteTree(staging.toPath())
        ArchiveExtractor().extractTarXz(archive, staging, ::withinTop) { files ->
            onProgress(ToolSetupProgress(step, item = "$files files", cancellable = false))
        }
        if (!File(staging, SOURCE_PROPERTIES).isFile) throw IOException("The NDK archive holds no NDK: it has no $SOURCE_PROPERTIES")
        deleteTree(folder.toPath())
        if (!staging.renameTo(folder)) throw IOException("Could not put NDK $revision in place")
    }

    /**
     * It is the pinned revision; its clang compiles C for Android, which proves the compiler and its
     * sysroot; and sdkmanager lists it.
     */
    private suspend fun verify() {
        val properties = File(folder, SOURCE_PROPERTIES).readText()
        if (REVISION.find(properties)?.groupValues?.get(1) != revision) throw IOException("The NDK unpacked is not $revision")
        val clang = "${AndroidSdk.GUEST_ROOT}/${AndroidSdk.ndk(revision)}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
        val compile = "printf 'int icode(void) { return 0; }\\n' | ${shellQuote(clang)} --target=aarch64-linux-android24 -x c -c - -o $CHECK_OBJECT; " +
            "status=\$?; rm -f $CHECK_OBJECT; exit \$status"
        val compiled = guest.capture(null, listOf("/bin/sh", "-c", compile), "verify")
        if (compiled.exitCode != 0) {
            throw IOException("The NDK's clang does not compile: ${compiled.stderr.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty) ?: "exit ${compiled.exitCode}"}")
        }
        if ("ndk;$revision" !in SdkManager(guest).installed()) throw IOException("sdkmanager does not list NDK $revision as installed")
    }

    companion object {
        /** An entry of the archive, under its one top folder (`android-ndk-r27/`), as a path in the NDK; the top itself is left out. */
        fun withinTop(entry: String): String? = entry.substringAfter('/', "").takeIf { it.isNotEmpty() }

        private const val SOURCE_PROPERTIES = "source.properties"
        private val REVISION = Regex("""Pkg\.Revision\s*=\s*(\S+)""")
        private const val CHECK_OBJECT = "/tmp/icode-ndk-check.o"
    }
}
