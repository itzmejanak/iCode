package dev.icode.mobile.data.android

import android.content.Context
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.data.runtime.ArchiveExtractor
import dev.icode.mobile.data.runtime.GuestCommand
import dev.icode.mobile.data.runtime.RuntimeLayout
import dev.icode.mobile.data.runtime.RuntimeToolSetup
import dev.icode.mobile.data.runtime.fetchPinned
import dev.icode.mobile.data.runtime.readManifest
import dev.icode.mobile.domain.RuntimeTools
import dev.icode.mobile.domain.ToolSetupProgress
import java.io.File
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * CMake 3.22.1's setup (docs/android-ndk-plan.md §3.2), once the Android SDK is installed: Google's
 * package, so sdkmanager and the Android Gradle plugin list it as installed, with its programs and
 * modules replaced by Kitware's own build of 3.22.1 for ARM64 and Ninja's for ARM64. Google's are
 * built for x86-64 only. Whatever was at `cmake/3.22.1` before — Google's, fetched by a build — is
 * made ARM64 the same way.
 */
internal class CmakeSetup(private val layout: RuntimeLayout, private val context: Context) : RuntimeToolSetup {
    override val id: String = ID

    private val guest = GuestCommand(layout)
    private val sdkmanager = SdkManager(guest)
    private val folder = File(AndroidSdk.host(layout), AndroidSdk.cmake(RuntimeTools.CMAKE_VERSION))
    private val manifest by lazy { readManifest(context) }
    private val cmake by lazy { manifest.archive(CMAKE_ARCHIVE) }
    private val ninja by lazy { manifest.archive(NINJA_ARCHIVE) }

    override val receipt: String by lazy {
        listOf(PACKAGE, "$CMAKE_ARCHIVE ${cmake.checksum.hex}", "$NINJA_ARCHIVE ${ninja.checksum.hex}").joinToString("\n", postfix = "\n")
    }

    override fun measure(): Map<String, Long> =
        listOf(cmake, ninja).associate { it.fileName to (it.bytes ?: error("The runtime manifest pins no size for ${it.fileName}")) } +
            (PACKAGE to (manifest.packageBytes[PACKAGE] ?: error("The runtime manifest pins no size for $PACKAGE")))

    override suspend fun install(onProgress: (ToolSetupProgress) -> Unit, onLine: (String) -> Unit) {
        // Kept until the whole setup has succeeded: a retry after a later step fails downloads nothing again.
        val cmakeArchive = File(context.cacheDir, cmake.fileName)
        val ninjaArchive = File(context.cacheDir, ninja.fileName)
        fetchPinned(cmake, cmakeArchive, "Downloading CMake for ARM64", onProgress)
        fetchPinned(ninja, ninjaArchive, "Downloading Ninja for ARM64", onProgress)
        sdkmanager.install(listOf(PACKAGE), "Installing CMake ${RuntimeTools.CMAKE_VERSION}", onProgress, onLine)
        if (!File(folder, "package.xml").isFile) throw IOException("sdkmanager did not install CMake ${RuntimeTools.CMAKE_VERSION}")
        withContext(NonCancellable) {
            onProgress(ToolSetupProgress("Setting up CMake for ARM64", cancellable = false))
            replacePrograms(cmakeArchive, ninjaArchive)
        }
        onProgress(ToolSetupProgress("Verifying CMake", cancellable = true))
        verify()
        cmakeArchive.delete()
        ninjaArchive.delete()
    }

    override fun configure() = Unit

    override suspend fun remove() {
        deleteTree(folder.toPath())
    }

    /**
     * Google's programs and modules go, Kitware's come — the tree measured (§2.4), whole — and Ninja
     * beside them. Nothing that cannot run here is left: `bin` holds [PROGRAMS] and nothing else.
     */
    private fun replacePrograms(cmakeArchive: File, ninjaArchive: File) {
        val bin = File(folder, "bin")
        listOf(bin, File(folder, "share")).forEach { deleteTree(it.toPath()) }
        val extractor = ArchiveExtractor()
        extractor.extractTarGz(cmakeArchive, folder, ::kitware) {}
        extractor.extractZip(ninjaArchive, bin) { it.takeIf { it == NINJA } }
        val placed = bin.list().orEmpty().toSet()
        if (placed != PROGRAMS) throw IOException("The pinned CMake and Ninja archives did not give ${PROGRAMS.sorted()}: ${placed.sorted()}")
    }

    /** `cmake --version` names 3.22.1, and Ninja runs: both as the build will run them. */
    private suspend fun verify() {
        val guestBin = "${AndroidSdk.GUEST_ROOT}/${AndroidSdk.cmake(RuntimeTools.CMAKE_VERSION)}/bin"
        val cmakeRan = guest.capture(null, listOf("$guestBin/cmake", "--version"), "verify")
        if (cmakeRan.exitCode != 0 || "cmake version ${RuntimeTools.CMAKE_VERSION}" !in cmakeRan.stdout) {
            throw IOException("cmake does not run as it should: ${said(cmakeRan) ?: "exit ${cmakeRan.exitCode}"}")
        }
        val ninjaRan = guest.capture(null, listOf("$guestBin/$NINJA", "--version"), "verify")
        if (ninjaRan.exitCode != 0) throw IOException("ninja does not run as it should: ${said(ninjaRan) ?: "exit ${ninjaRan.exitCode}"}")
    }

    private fun said(ran: GuestCommand.Captured): String? = ran.stderr.lineSequence().map(String::trim).lastOrNull(String::isNotEmpty)

    companion object {
        /** [RuntimeTools.Cmake]'s setup. */
        const val ID = "cmake"

        /** sdkmanager's package, and the pins in `assets/runtime/manifest.json`. */
        const val PACKAGE = "cmake;${RuntimeTools.CMAKE_VERSION}"
        const val CMAKE_ARCHIVE = "cmake-arm64"
        const val NINJA_ARCHIVE = "ninja-arm64"

        private const val NINJA = "ninja"
        val PROGRAMS = setOf("cmake", "ctest", "cpack", NINJA)

        /**
         * Where an entry of Kitware's tarball goes in `cmake/3.22.1`: its programs into `bin`, its
         * modules into `share`; `ccmake` and `cmake-gui` (terminal and desktop interfaces), docs and
         * man pages stay out.
         */
        fun kitware(entry: String): String? {
            val path = entry.substringAfter('/', "")
            return when {
                path.startsWith("bin/") -> path.takeIf { it.removePrefix("bin/") in PROGRAMS }
                path.startsWith("share/") -> path
                else -> null
            }
        }
    }
}
