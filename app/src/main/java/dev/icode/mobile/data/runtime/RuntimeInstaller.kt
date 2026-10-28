package dev.icode.mobile.data.runtime

import android.content.Context
import android.os.Build
import dev.icode.mobile.data.io.ArchiveLinks
import dev.icode.mobile.data.io.Archives
import dev.icode.mobile.data.io.DigestAlgorithm
import dev.icode.mobile.data.io.Unpacking
import dev.icode.mobile.data.io.deleteTree
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeInstallReason
import dev.icode.mobile.domain.RuntimeInstallStep
import dev.icode.mobile.domain.files.ArchiveFormat
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/**
 * Installs and verifies the Ubuntu root filesystem.
 *
 * Nothing is reported as ready until a command actually runs inside it
 * (architecture.md section 14.1: "never reports readiness before health checks
 * pass"). A partial download or a failed extraction leaves the runtime marked
 * not-installed rather than broken-but-ready.
 */
internal class RuntimeInstaller(
    context: Context,
    private val layout: RuntimeLayout = RuntimeLayout(context),
) {
    private val appContext = context.applicationContext

    fun readDescriptor(): RootfsDescriptor {
        val manifest = readManifest(appContext)
        require(Build.SUPPORTED_ABIS.contains(manifest.architecture)) {
            "This runtime build requires ${manifest.architecture}; this device reports ${Build.SUPPORTED_ABIS.joinToString()}"
        }
        return manifest.rootfs
    }

    /** The PRoot this build ships, read from the manifest rather than restated here. */
    fun prootVersion(): String? = runCatching { readManifest(appContext).prootVersion }.getOrNull()

    /**
     * Emits progress for the whole install. Collect on a background dispatcher.
     *
     * [RuntimeInstallReason.Repair] keeps the guest home directory across a
     * reinstall, which separates repairing from resetting. Installed
     * packages live outside the home and are not kept either way — a rebuilt
     * base system is a new base system.
     */
    fun install(reason: RuntimeInstallReason = RuntimeInstallReason.Initial): Flow<RuntimeState> = flow {
        val descriptor = runCatching { readDescriptor() }.getOrElse { error ->
            emit(RuntimeState.Failed(error.message ?: "Unreadable runtime manifest", recoverable = false))
            return@flow
        }

        if (!layout.binariesPresent) {
            emit(RuntimeState.Failed("The runtime binaries are missing from this build", recoverable = false))
            return@flow
        }

        // Space other apps' caches hold counts: Android clears them for us when needed.
        if (!layout.reserveSpace(descriptor.minimumFreeBytes)) {
            emit(
                RuntimeState.Failed(
                    "Not enough storage: ${ByteSize.format(descriptor.minimumFreeBytes)} required, " +
                        "${ByteSize.format(layout.allocatableBytes())} free",
                ),
            )
            return@flow
        }

        layout.runtimeRoot.mkdirs()
        val homeBackup = RuntimeHomeBackup(layout.rootfs, layout.runtimeRoot)
        if (reason == RuntimeInstallReason.Initial && homeBackup.hasPreservedHome) {
            emit(RuntimeState.Failed(
                "A previous repair preserved your home. Open Runtime Settings and choose Repair to restore it, or Reset to discard it.",
            ))
            return@flow
        }
        val archive = File(appContext.cacheDir, "${descriptor.id}.tar.gz")
        val pinned = PinnedFile(descriptor.url, bytes = null, Checksum(DigestAlgorithm.Sha256, descriptor.sha256))

        try {
            if (!PinnedDownload.isVerified(archive, pinned)) {
                archive.delete()
                // The digest is checked as the bytes arrive: a download that is not the pinned base system is deleted.
                PinnedDownload.download(pinned, archive) { received, total ->
                    emit(RuntimeState.Downloading(received, total, "Downloading ${descriptor.base}"))
                }
            }

            val savedHome = if (reason == RuntimeInstallReason.Repair) homeBackup.prepareForRepair() else null
            if (reason != RuntimeInstallReason.Initial && layout.installMarker.exists() && !layout.installMarker.delete()) {
                savedHome?.let(homeBackup::restore)
                throw IOException("Unable to begin rebuilding the runtime")
            }
            // Extraction takes minutes: the entries written, against the pinned archive's, are its
            // progress as it goes.
            val extracting = "Extracting ${descriptor.base}"
            emit(RuntimeState.Installing(extracting, RuntimeInstallStep.Extracting, 0, descriptor.entries))
            extract(archive) { entries ->
                emit(RuntimeState.Installing(extracting, RuntimeInstallStep.Extracting, entries, descriptor.entries))
            }

            savedHome?.let(homeBackup::restore)
            emit(RuntimeState.Installing("Configuring the runtime", RuntimeInstallStep.Configuring))
            configureGuest(freshHome = savedHome == null)

            emit(RuntimeState.Installing("Verifying the runtime", RuntimeInstallStep.Verifying))
            val release = healthCheck()
                ?: run {
                    emit(RuntimeState.Failed("The runtime did not answer a health check"))
                    return@flow
                }

            if (reason == RuntimeInstallReason.Reset) homeBackup.discardAfterReset()
            layout.installMarker.writeText(
                JSONObject()
                    .put("id", descriptor.id)
                    .put("base", descriptor.base)
                    .put("sha256", descriptor.sha256)
                    .put("installedAt", System.currentTimeMillis())
                    .toString(),
            )
            // The verified install marker is now durable. A leftover repair
            // sentinel is safe (a later restart still sees Ready), whereas
            // calling this a failed install after writing the marker is not.
            if (reason == RuntimeInstallReason.Repair) runCatching { homeBackup.completeRepair() }
            // A rebuilt base system has none of the tools installed before: they are offered again.
            runCatching { layout.toolsOffer.createNewFile() }
            archive.delete()
            emit(RuntimeState.Ready(release))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(RuntimeState.Failed(error.message ?: "Runtime installation failed"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Unpacks the base system directly rather than through PRoot.
     *
     * PRoot intercepts every syscall with ptrace, and unpacking is thousands of
     * small files, so the interception — not the disk — set the pace. Nothing
     * here needs the guest: it writes files into a directory this app owns.
     */
    private suspend fun extract(archive: File, onEntry: suspend (Long) -> Unit) {
        deleteTree(layout.rootfs)
        layout.rootfs.mkdirs()
        // The unpack runs on its own coroutine and records how far it has got;
        // the count is published from *this* one. A flow may only be emitted to
        // from the coroutine collecting it, so the reporting cannot be what
        // runs in the background — the work is.
        val counted = AtomicLong(0)
        coroutineScope {
            val work = async(Dispatchers.IO) {
                Archives.unpack(archive, ArchiveFormat.TarGz, layout.rootfs, Unpacking(ArchiveLinks.AsWritten, onEntry = { counted.incrementAndGet() }))
            }
            while (!work.isCompleted) {
                delay(EXTRACT_REPORT_INTERVAL_MILLIS)
                onEntry(counted.get())
            }
            work.await()
        }
        onEntry(counted.get())

        // Judged by what landed on disk. A complete tree is a success and an
        // incomplete one is not, whatever the unpacking reported along the way.
        val missing = REQUIRED_PATHS.filterNot { File(layout.rootfs, it).exists() }
        if (missing.isNotEmpty()) {
            throw ExtractionException(
                "Extraction did not produce a usable root filesystem (missing ${missing.joinToString()})",
            )
        }
    }

    /**
     * iCode's files in the base system ([GuestSystemFiles], [GuestSudo], [GuestGpg], its own in
     * `/etc/icode`). A [freshHome] — the base system's, not one Repair kept — gets iCode's prompt
     * line ([ShellPrompt.addTo]); a kept one is the user's, and nothing is written into it.
     */
    private fun configureGuest(freshHome: Boolean) {
        GuestSystemFiles.write(layout.rootfs)
        val home = File(layout.rootfs, RuntimeHomeBackup.HOME_RELATIVE).apply { mkdirs() }
        layout.writeOwnFiles()
        if (freshHome) ShellPrompt.addTo(home)
        layout.ensureGuestGroups()
        GuestSudo.install(layout.rootfs)
        GuestGpg.install(layout.rootfs)
    }

    /** Runs a command in the guest and returns its release string, or null. */
    private fun healthCheck(): String? {
        val result = runGuest(listOf("/bin/sh", "-c", ". /etc/os-release 2>/dev/null; echo \"\${PRETTY_NAME:-unknown}\""))
        if (result.exitCode != 0) return null
        return result.output.trim().lineSequence().lastOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun runGuest(command: List<String>): CommandResult =
        execute(layout.prootCommand(guestCommand = command, variables = GuestVariables.of(GuestContext.Install, layout.loginShell())))

    private fun execute(argv: List<String>): CommandResult {
        val process = RuntimeProcess.start(
            argv = argv,
            environment = layout.hostEnvironment(),
            workingDirectory = appContext.filesDir.absolutePath,
        )
        val output = process.output.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        process.destroy()
        return CommandResult(exitCode, output)
    }

    internal data class CommandResult(val exitCode: Int, val output: String)

    private companion object {
        const val EXTRACT_REPORT_INTERVAL_MILLIS = 250L

        /**
         * Proof that the archive landed: a shell to run commands with, and the
         * package manager every later provisioning step depends on.
         */
        val REQUIRED_PATHS = listOf("bin/sh", "usr/bin/apt")
    }
}

/** The packaged manifest ([RuntimeManifest]): read wherever a pin is needed, never restated. */
internal fun readManifest(context: Context): RuntimeManifest =
    RuntimeManifest.parse(context.assets.open(RuntimeManifest.ASSET).bufferedReader().use { it.readText() })

/** Raised when an archive cannot be unpacked into a usable tree. */
internal class ExtractionException(message: String) : IOException(message)
