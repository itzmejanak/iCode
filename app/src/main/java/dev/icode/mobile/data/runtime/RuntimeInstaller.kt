package dev.icode.mobile.data.runtime

import android.content.Context
import android.os.Build
import dev.icode.mobile.data.io.Sha256
import dev.icode.mobile.domain.ByteSize
import dev.icode.mobile.domain.RuntimeState
import dev.icode.mobile.domain.RuntimeInstallReason
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/** The root filesystem this build installs, read from the packaged manifest. */
internal data class RootfsDescriptor(
    val id: String,
    val base: String,
    val url: String,
    val sha256: String,
    val minimumFreeBytes: Long,
)

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
        val json = appContext.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
        val manifest = JSONObject(json)
        require(manifest.getInt("schemaVersion") == SUPPORTED_SCHEMA) {
            "Unsupported runtime manifest schema"
        }
        val architecture = manifest.getString("architecture")
        require(Build.SUPPORTED_ABIS.contains(architecture)) {
            "This runtime build requires $architecture; this device reports ${Build.SUPPORTED_ABIS.joinToString()}"
        }
        val rootfs = manifest.getJSONObject("rootfs")
        return RootfsDescriptor(
            id = rootfs.getString("id"),
            base = rootfs.getString("base"),
            url = rootfs.getString("url"),
            sha256 = rootfs.getString("sha256"),
            minimumFreeBytes = rootfs.getLong("minimumFreeBytes"),
        )
    }

    /** The PRoot this build ships, read from the manifest rather than restated here. */
    fun prootVersion(): String? = runCatching {
        val json = appContext.assets.open(MANIFEST_ASSET).bufferedReader().use { it.readText() }
        JSONObject(json).getJSONObject("proot").getString("version")
    }.getOrNull()

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

        try {
            if (!archive.isFile || Sha256.hexOf(archive) != descriptor.sha256) {
                archive.delete()
                download(descriptor, archive) { received, total ->
                    emit(RuntimeState.Downloading(received, total, "Downloading ${descriptor.base}"))
                }
                emit(RuntimeState.Installing("Verifying download"))
                val actual = Sha256.hexOf(archive)
                if (actual != descriptor.sha256) {
                    archive.delete()
                    emit(RuntimeState.Failed("The downloaded runtime failed checksum verification"))
                    return@flow
                }
            }

            val savedHome = if (reason == RuntimeInstallReason.Repair) homeBackup.prepareForRepair() else null
            if (reason != RuntimeInstallReason.Initial && layout.installMarker.exists() && !layout.installMarker.delete()) {
                savedHome?.let(homeBackup::restore)
                throw IOException("Unable to begin rebuilding the runtime")
            }
            emit(RuntimeState.Installing("Extracting ${descriptor.base}"))
            // Extracting a root filesystem through PRoot takes minutes. Without
            // a count the screen sits on one line and reads as hung, so tar's
            // own tally of entries is reported as it goes.
            extract(archive) { entries ->
                emit(RuntimeState.Installing("Extracting ${descriptor.base} — $entries files"))
            }

            savedHome?.let(homeBackup::restore)
            emit(RuntimeState.Installing("Configuring the runtime"))
            configureGuest()

            emit(RuntimeState.Installing("Verifying the runtime"))
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
            archive.delete()
            emit(RuntimeState.Ready(release))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            emit(RuntimeState.Failed(error.message ?: "Runtime installation failed"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun download(
        descriptor: RootfsDescriptor,
        target: File,
        onProgress: suspend (Long, Long) -> Unit,
    ) {
        val connection = (URL(descriptor.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Download failed with HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            var received = 0L
            var lastReported = 0L
            var lastReportedAt = System.currentTimeMillis()
            // Reported before the first byte arrives. Without this the caller
            // stays in its previous state until a megabyte has landed, and on
            // a slow connection the screen claims nothing is happening for
            // minutes while the download is in fact running.
            onProgress(0L, total)
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        // Sparingly by volume, but also on a timer, so a slow
                        // link still visibly moves instead of looking hung.
                        val now = System.currentTimeMillis()
                        if (received - lastReported >= PROGRESS_STEP_BYTES ||
                            now - lastReportedAt >= PROGRESS_INTERVAL_MILLIS
                        ) {
                            lastReported = received
                            lastReportedAt = now
                            onProgress(received, total)
                        }
                    }
                }
            }
            onProgress(received, total)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extracts through PRoot so ownership and device nodes in the archive apply
     * cleanly. Extracting directly would fail on every chown the tarball asks
     * for, because the app is not root on Android.
     */
    /**
     * Unpacks the base system directly rather than through PRoot.
     *
     * PRoot intercepts every syscall with ptrace, and unpacking is thousands of
     * small files, so the interception — not the disk — set the pace. Nothing
     * here needs the guest: it writes files into a directory this app owns.
     */
    private suspend fun extract(archive: File, onEntry: suspend (Long) -> Unit) {
        if (layout.rootfs.exists()) layout.rootfs.deleteRecursively()
        layout.rootfs.mkdirs()
        // The unpack runs on its own coroutine and records how far it has got;
        // the count is published from *this* one. A flow may only be emitted to
        // from the coroutine collecting it, so the reporting cannot be what
        // runs in the background — the work is.
        val counted = AtomicLong(0)
        coroutineScope {
            val work = async(Dispatchers.IO) {
                RootfsExtractor().extract(archive, layout.rootfs) { entries -> counted.set(entries) }
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

    /** Android gives the guest no resolver configuration; without this apt cannot resolve names. */
    private fun configureGuest() {
        File(layout.rootfs, "etc").mkdirs()
        File(layout.rootfs, "etc/resolv.conf").writeText(
            "nameserver 8.8.8.8\nnameserver 8.8.4.4\n",
        )
        File(layout.rootfs, "etc/hosts").writeText(
            "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n",
        )
        File(layout.rootfs, RuntimeHomeBackup.HOME_RELATIVE).mkdirs()
        File(layout.rootfs, LayoutConstants.GUEST_WORKSPACE_RELATIVE).mkdirs()
        layout.ensureGuestGroups()
        GuestSudo.install(layout.rootfs)
        // dpkg syncs every file it unpacks, and on phone storage those syncs
        // dominate an install (measured: git 152 s with them, 84 s without).
        // The package database is rebuildable — Repair Runtime exists for a
        // lost write — and container images drop the syncs the same way. The
        // standard dpkg.cfg.d place applies it to every dpkg run.
        File(layout.rootfs, DPKG_UNSAFE_IO_RELATIVE).apply {
            parentFile?.mkdirs()
            writeText("force-unsafe-io\n")
        }
    }

    /** Runs a command in the guest and returns its release string, or null. */
    private fun healthCheck(): String? {
        val result = runGuest(listOf("/bin/sh", "-c", ". /etc/os-release 2>/dev/null; echo \"\${PRETTY_NAME:-unknown}\""))
        if (result.exitCode != 0) return null
        return result.output.trim().lineSequence().lastOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun runGuest(command: List<String>): CommandResult =
        execute(layout.prootCommand(guestCommand = command))

    private fun execute(argv: List<String>): CommandResult {
        val process = RuntimeProcess.start(
            argv = argv,
            environment = layout.prootEnvironment(),
            workingDirectory = appContext.filesDir.absolutePath,
        )
        val output = process.output.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        process.destroy()
        return CommandResult(exitCode, output)
    }

    internal data class CommandResult(val exitCode: Int, val output: String)

    private object LayoutConstants {
        const val GUEST_WORKSPACE_RELATIVE = "workspace"
    }

    private companion object {
        const val MANIFEST_ASSET = "runtime/manifest.json"
        const val DPKG_UNSAFE_IO_RELATIVE = "etc/dpkg/dpkg.cfg.d/icode-unsafe-io"
        const val SUPPORTED_SCHEMA = 1
        const val EXTRACT_REPORT_INTERVAL_MILLIS = 250L
        const val CONNECT_TIMEOUT_MILLIS = 30_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val DOWNLOAD_BUFFER_BYTES = 128 * 1024
        const val PROGRESS_STEP_BYTES = 1024L * 1024L

        /** So a slow link still shows movement between megabytes. */
        const val PROGRESS_INTERVAL_MILLIS = 700L

        /**
         * Proof that the archive landed: a shell to run commands with, and the
         * package manager every later provisioning step depends on.
         */
        val REQUIRED_PATHS = listOf("bin/sh", "usr/bin/apt")
    }
}
