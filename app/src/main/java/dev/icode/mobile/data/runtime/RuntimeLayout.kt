package dev.icode.mobile.data.runtime

import android.content.Context
import android.os.storage.StorageManager
import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import org.json.JSONObject

/**
 * Where the Linux runtime lives, and how it is launched.
 *
 * Executables come from the native library directory because that is the only
 * place Android lets this app execute from; the root filesystem is ordinary
 * app-private data. See runtime/bootstrap/README.md.
 */
internal class RuntimeLayout(context: Context) {
    private val appContext = context.applicationContext

    /** Extracted at install time, executable, read-only. */
    val nativeLibraryDir: File = File(appContext.applicationInfo.nativeLibraryDir)

    val proot: File = File(nativeLibraryDir, "libproot.so")
    val prootLoader: File = File(nativeLibraryDir, "libprootloader.so")

    /** Writable, never executable. The Ubuntu tree is extracted here. */
    val runtimeRoot: File = File(appContext.filesDir, "runtime")
    val rootfs: File = File(runtimeRoot, "rootfs")
    val prootTemp: File = File(appContext.cacheDir, "proot-tmp")

    /** Every workspace, bound whole at `/workspace` in every launch. */
    val workspaces = WorkspacePaths.of(appContext)

    /** Marks a verified, fully extracted root filesystem. */
    val installMarker: File = File(runtimeRoot, "installed.json")

    val isInstalled: Boolean
        get() = installMarker.isFile && File(rootfs, "bin/sh").exists()

    /**
     * Space the runtime can have on its volume, counting the cached files of
     * other apps that Android deletes on request — the figure Android says a
     * decision about space must use. Worker thread: it can take seconds.
     */
    fun allocatableBytes(): Long {
        val volume = runtimeRoot.parentFile ?: return 0L
        return runCatching {
            val storage = appContext.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(storage.getUuidForPath(volume))
        }.getOrElse { volume.usableSpace }
    }

    /**
     * Makes [bytes] free on the runtime's volume, having Android delete other
     * apps' cached files if that is what it takes; false when it cannot.
     */
    fun reserveSpace(bytes: Long): Boolean {
        val volume = runtimeRoot.parentFile ?: return false
        if (volume.usableSpace >= bytes) return true
        return runCatching {
            val storage = appContext.getSystemService(StorageManager::class.java)
            val uuid = storage.getUuidForPath(volume)
            if (storage.getAllocatableBytes(uuid) < bytes) return false
            storage.allocateBytes(uuid, bytes)
            true
        }.getOrDefault(false)
    }

    /** The release the installer recorded ("Ubuntu 24.04.5 LTS"), or null when there is none to read. */
    fun installedRelease(): String? = runCatching {
        JSONObject(installMarker.readText()).getString("base")
    }.getOrNull()

    val binariesPresent: Boolean
        get() = proot.canExecute() && prootLoader.exists()

    /**
     * Registers the process's Android supplementary groups inside the guest.
     *
     * An Android app runs with supplementary GIDs (inet, everybody, and
     * per-app ids) that no Linux distribution knows about. Without matching
     * `/etc/group` entries every login shell greets the user with
     * "groups: cannot find name for group ID ...". Naming them is the honest
     * fix; hiding the warning would leave the lookups broken for any tool that
     * resolves group names.
     *
     * Idempotent and cheap, so it is safe to call before each session.
     */
    fun ensureGuestGroups() {
        val groupFile = File(rootfs, "etc/group")
        if (!groupFile.isFile) return
        val existing = runCatching { groupFile.readText() }.getOrNull() ?: return
        val additions = androidSupplementaryGroups()
            .filterNot { gid -> existing.contains(":x:$gid:") }
            .joinToString("\n") { gid -> "aid_$gid:x:$gid:" }
        if (additions.isNotEmpty()) {
            runCatching { groupFile.appendText(additions + "\n") }
        }
    }

    /** Reads the supplementary GIDs the kernel assigned to this process. */
    private fun androidSupplementaryGroups(): List<Int> = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Groups:") }
                ?.removePrefix("Groups:")
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.mapNotNull(String::toIntOrNull)
                .orEmpty()
        }
    }.getOrDefault(emptyList())

    /**
     * Environment for every PRoot launch.
     *
     * `PROOT_LOADER` is the load-bearing entry: without it PRoot writes its
     * loader into a temporary directory and executes it, which Android refuses.
     */
    fun prootEnvironment(extra: Map<String, String> = emptyMap()): Map<String, String> {
        prootTemp.mkdirs()
        return buildMap {
            put("HOME", GUEST_HOME)
            put("PATH", GUEST_PATH)
            // True of the terminal emulator, which also draws 24-bit color.
            put("TERM", "xterm-256color")
            put("COLORTERM", "truecolor")
            put("LANG", "C.UTF-8")
            // Resolves libtalloc.so and libandroid-shmem.so for PRoot itself.
            put("LD_LIBRARY_PATH", nativeLibraryDir.absolutePath)
            put("PROOT_LOADER", prootLoader.absolutePath)
            put("PROOT_TMP_DIR", prootTemp.absolutePath)
            // PROOT_NO_SECCOMP is deliberately absent. PRoot's seccomp filter
            // lets calls that need no path translation (read, write, poll…)
            // run natively; with it off, every one stopped in PRoot at ~50 µs
            // (docs/terminal-maturity-plan.md §6).
            // PRoot's own notes ("proot info: vpid 1: terminated with signal
            // 9") would land in the user's terminal and in captured git
            // output. Below zero PRoot keeps only its errors; how a command
            // ended reaches iCode through its exit status.
            put("PROOT_VERBOSE", "-1")
            // Guest glibc's restartable sequences misbehave under PRoot's tracing.
            put("GLIBC_TUNABLES", "glibc.pthread.rseq=0")
            putAll(extra)
        }
    }

    /**
     * Builds the PRoot command line. Every launch sees every workspace at `/workspace`
     * (docs/workspace-storage-plan.md §4.2); only its working directory differs.
     */
    fun prootCommand(
        guestCommand: List<String>,
        guestWorkingDirectory: String = GUEST_HOME,
        /**
         * Ends every process the command started when the command itself
         * ends: the rest get SIGHUP, then SIGKILL after a grace period. A
         * terminal tab lives exactly as long as its shell; without this a
         * background job would keep an exited shell's session open.
         */
        killOnExit: Boolean = false,
    ): List<String> = buildList {
        add(proot.absolutePath)
        // Android refuses link(), and dpkg, git and friends need hard links, so
        // they are emulated with symbolic links — in the root filesystem and
        // the workspaces alike, both in iCode's own storage. A link that still
        // fails leaves the file untouched (third_party/patches/proot/0001).
        add("--link2symlink")
        if (killOnExit) add("--kill-on-exit")
        // Present as root inside the guest so package managers work.
        add("-0")
        add("-r")
        add(rootfs.absolutePath)
        add("-b")
        add("/dev")
        add("-b")
        add("/proc")
        add("-b")
        add("/sys")
        // Host Android trees the bionic linker needs when guest tools call out.
        HOST_BINDS.forEach { hostPath ->
            if (File(hostPath).exists()) {
                File(rootfs, hostPath.removePrefix("/")).mkdirs()
                add("-b")
                add(hostPath)
            }
        }
        // A workspace's name never reaches this argument, so no name can split it.
        workspaces.root.mkdirs()
        File(rootfs, WorkspacePaths.GUEST_ROOT.removePrefix("/")).mkdirs()
        add("-b")
        add("${workspaces.root.path}:${WorkspacePaths.GUEST_ROOT}")
        add("-w")
        add(guestWorkingDirectory)
        addAll(guestCommand)
    }

    companion object {
        const val GUEST_HOME = "/root"
        /**
         * The guest's search path, with the user's own bin directory first.
         *
         * Ubuntu's `~/.profile` prepends `~/.local/bin`, and that is where
         * pip, pipx, user-level npm and installers such as Claude Code put
         * what they install. Setting PATH explicitly without it meant a tool
         * installed a moment earlier was "not found". First, as Ubuntu does,
         * so a user's own install takes precedence over the system's. Absent
         * directories in PATH are harmless.
         */
        const val GUEST_PATH =
            "$GUEST_HOME/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        private val HOST_BINDS = listOf("/system", "/apex", "/vendor", "/product")
    }
}
