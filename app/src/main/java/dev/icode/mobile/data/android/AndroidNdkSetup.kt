package dev.icode.mobile.data.android

import android.content.Context
import dev.icode.mobile.data.io.deleteTree
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
 * build of Google's NDK, its toolchain built for ARM64 Linux, put in `ndk/<revision>`, Android
 * Studio's side-by-side place, where its own `package.xml` makes sdkmanager and the Android Gradle
 * plugin list it as installed. Whatever was there before goes: Google's x86-64 NDK fetched by a build,
 * or a setup cut short.
 *
 * The runtime's own tar and xz unpack it, never the app (§7.6): its archives are compressed with a
 * 256 MiB window, which the decoder holds whole — more than an app's heap may grow to. In the
 * runtime it is native memory, in a process of its own.
 */
internal class AndroidNdkSetup(private val layout: RuntimeLayout, private val context: Context, private val revision: String) : RuntimeToolSetup {
    override val id: String = RuntimeTools.ndkId(revision)

    private val guest = GuestCommand(layout)
    private val folder = File(AndroidSdk.host(layout), AndroidSdk.ndk(revision))
    private val pin by lazy { readManifest(context).archive(id) }

    /** Downloaded, and unpacked first, beside it in the guest home, where the runtime's tar reads and writes. */
    private val archive = File(folder.parentFile, ".$revision.tar.xz")
    private val staging = File(folder.parentFile, ".$revision-unpacking")

    override val receipt: String by lazy { "ndk;$revision ${pin.checksum.hex}\n" }

    override fun measure(): Map<String, Long> = mapOf(pin.fileName to (pin.bytes ?: error("The runtime manifest pins no size for ${pin.fileName}")))

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        // Kept until the whole setup has succeeded: a retry after a later step fails downloads nothing again.
        archive.parentFile?.mkdirs()
        fetchPinned(pin, archive, "Downloading NDK $revision", onProgress)
        unpack(onProgress)
        onProgress(ToolSetupProgress("Verifying NDK $revision", cancellable = true))
        verify()
        archive.delete()
    }

    override fun configure() = Unit

    override suspend fun remove() {
        listOf(folder, staging, archive).forEach { deleteTree(it.toPath()) }
    }

    /**
     * `tar -xJ` in the runtime into a staging folder beside it, its one top folder (`android-ndk-r27/`)
     * left out, then in place of whatever `ndk/<revision>` held. Stopping it, or a failure, leaves no
     * staging folder behind.
     */
    private suspend fun unpack(onProgress: (ToolSetupProgress) -> Unit) {
        val step = "Unpacking NDK $revision"
        onProgress(ToolSetupProgress(step, cancellable = true))
        deleteTree(staging.toPath())
        staging.mkdirs()
        var files = 0
        val ran = try {
            guest.stream(null, unpackCommand(guestPath(archive), guestPath(staging)), "tar") {
                if (++files % PROGRESS_EVERY == 0) onProgress(ToolSetupProgress(step, item = "$files files", cancellable = true))
                true
            }
        } catch (stopped: Throwable) {
            withContext(NonCancellable) { deleteTree(staging.toPath()) }
            throw stopped
        }
        withContext(NonCancellable) {
            if (ran.exitCode != 0 || !File(staging, SOURCE_PROPERTIES).isFile) {
                deleteTree(staging.toPath())
                throw IOException(unpackFailure(revision, ran.stderr) ?: "tar could not unpack it: ${ran.failureDetail() ?: "exit ${ran.exitCode}"}")
            }
            deleteTree(folder.toPath())
            if (!staging.renameTo(folder)) throw IOException("Could not put NDK $revision in place")
        }
    }

    /**
     * It is the pinned revision; its clang compiles C for Android, which proves the compiler and its
     * sysroot; and sdkmanager lists it.
     */
    private suspend fun verify() {
        val properties = File(folder, SOURCE_PROPERTIES).readText()
        if (REVISION.find(properties)?.groupValues?.get(1) != revision) throw IOException("The NDK unpacked is not $revision")
        val clang = "${guestPath(folder)}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
        val compile = "printf 'int icode(void) { return 0; }\\n' | ${shellQuote(clang)} --target=aarch64-linux-android24 -x c -c - -o $CHECK_OBJECT; " +
            "status=\$?; rm -f $CHECK_OBJECT; exit \$status"
        val compiled = guest.capture(null, listOf("/bin/sh", "-c", compile), "verify")
        if (compiled.exitCode != 0) {
            throw IOException("The NDK's clang does not compile: ${compiled.stderr.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty) ?: "exit ${compiled.exitCode}"}")
        }
        if ("ndk;$revision" !in SdkManager(guest).installed()) throw IOException("sdkmanager does not list NDK $revision as installed")
    }

    /** [file], in the SDK, as the runtime names it. */
    private fun guestPath(file: File): String = "${AndroidSdk.GUEST_ROOT}/${file.relativeTo(AndroidSdk.host(layout)).path}"

    companion object {
        /** The runtime's GNU tar, with xz (`xz-utils`, the row's package): each entry's name on a line, as it goes. */
        fun unpackCommand(archive: String, into: String): List<String> =
            listOf("tar", "-xvJf", archive, "-C", into, "--strip-components=1", "--no-same-owner")

        /** What tar's or xz's [stderr] says, in the user's words where it is the phone's doing; null otherwise. */
        fun unpackFailure(revision: String, stderr: String): String? = when {
            "Cannot allocate memory" in stderr -> "There is not enough free memory to unpack NDK $revision. Close other apps, then Retry."
            "No space left on device" in stderr -> "There is not enough free storage to unpack NDK $revision. Free some space, then Retry."
            else -> null
        }

        private const val SOURCE_PROPERTIES = "source.properties"
        private val REVISION = Regex("""Pkg\.Revision\s*=\s*(\S+)""")
        private const val CHECK_OBJECT = "/tmp/icode-ndk-check.o"
        private const val PROGRESS_EVERY = 200
    }
}
