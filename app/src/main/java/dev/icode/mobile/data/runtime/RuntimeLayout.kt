package dev.icode.mobile.data.runtime

import android.content.Context
import android.os.storage.StorageManager
import dev.icode.mobile.data.storage.PhoneStorage
import dev.icode.mobile.data.storage.WorkspacePaths
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
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

    /** The runtime's paths, as the files on the phone that hold them ([GuestPaths]). */
    val paths = GuestPaths(rootfs, workspaces, PhoneStorage.top())

    /** The guest home, `/root`: Repair keeps it ([RuntimeHomeBackup]). */
    val home: File = File(rootfs, GUEST_HOME.removePrefix("/"))

    /** Each runtime tool setup's receipt, by id: `~/.icode/runtime-tools/<id>` (docs/android-toolchain-plan.md §4.2). */
    val toolReceipts: File = File(home, ".icode/runtime-tools")

    /** Marks a verified, fully extracted root filesystem. */
    val installMarker: File = File(runtimeRoot, "installed.json")

    /**
     * Written when a build of the runtime completes, and removed once its Set Up Tools page is
     * answered (docs/runtime-tools-plan.md §3.5). A runtime built before the page existed has
     * none, and is not offered it.
     */
    val toolsOffer: File = File(runtimeRoot, "tools-offer")

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
     * iCode's own files in the runtime's `/etc/icode`, brought to this build: the prompt
     * ([ShellPrompt]), the shell integration ([ShellIntegration]) and the README ([README_PATH], the `runtime/README.md` asset). At install and
     * at every iCode start, so an update reaches them; a runtime that cannot take them still runs.
     */
    fun writeOwnFiles() {
        runCatching {
            ShellPrompt.write(rootfs)
            ShellIntegration.write(rootfs)
            val readme = appContext.assets.open(README_ASSET).use { it.readBytes() }
            File(rootfs, README_PATH.removePrefix("/")).apply { parentFile?.mkdirs() }.writeBytes(readme)
        }
    }

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
     * PRoot's own environment, which no guest program inherits ([prootCommand] starts each with
     * exactly its variables, docs/runtime-contract-plan.md §3.1).
     *
     * `PROOT_LOADER` is the load-bearing entry: without it PRoot writes its
     * loader into a temporary directory and executes it, which Android refuses.
     */
    fun hostEnvironment(): Map<String, String> {
        prootTemp.mkdirs()
        return mapOf(
            // Resolves libtalloc.so and libandroid-shmem.so for PRoot itself.
            "LD_LIBRARY_PATH" to nativeLibraryDir.absolutePath,
            "PROOT_LOADER" to prootLoader.absolutePath,
            "PROOT_TMP_DIR" to prootTemp.absolutePath,
            // PROOT_NO_SECCOMP is deliberately absent. PRoot's seccomp filter
            // lets calls that need no path translation (read, write, poll…)
            // run natively; with it off, every one stopped in PRoot at ~50 µs
            // (docs/terminal-maturity-plan.md §6).
            // PRoot's own notes ("proot info: vpid 1: terminated with signal
            // 9") would land in the user's terminal and in captured git
            // output. Below zero PRoot keeps only its errors; how a command
            // ended reaches iCode through its exit status.
            "PROOT_VERBOSE" to "-1",
        )
    }

    /** The user's login environment, read once and again when a profile file changes, for every context that reads it. */
    val environment: GuestEnvironment by lazy { GuestEnvironment(this) }

    /**
     * Root's login shell as the runtime's `/etc/passwd` records it — `chsh` changes it — while it is
     * installed; `/bin/bash` otherwise.
     */
    fun loginShell(): String = loginShell(paths)

    /** The shell the runtime's `/etc/passwd` gives root, installed or not ([recordedShell]). */
    fun recordedShell(): String? = recordedShell(paths)

    /**
     * Builds the PRoot command line. Every launch sees the same tree: the runtime's root, the
     * phone's [GuestPaths.HOST_PATHS], and every workspace at `/workspace`
     * (docs/runtime-contract-plan.md §4.1).
     */
    fun prootCommand(
        guestCommand: List<String>,
        /** Every variable the guest program starts with, and none other: a [GuestContext]'s ([GuestVariables.of]). */
        variables: Map<String, String>,
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
        // Android refuses link(), and dpkg needs hard links, so they are
        // emulated with symbolic links — in the root filesystem and the
        // workspaces alike, both in iCode's own storage. A link that still
        // fails leaves the file untouched (third_party/patches/proot/0001).
        // An emulated link names an absolute path and breaks when its folder
        // is renamed, so Git is set not to ask for one (RuntimeInstaller).
        add("--link2symlink")
        if (killOnExit) add("--kill-on-exit")
        // Present as root inside the guest so package managers work.
        add("-0")
        add("-r")
        add(rootfs.absolutePath)
        // The kernel's trees, and the Android ones the bionic linker needs when guest tools call out.
        GuestPaths.HOST_PATHS.forEach { hostPath ->
            if (File(hostPath).exists()) {
                File(rootfs, hostPath.removePrefix("/")).mkdirs()
                add("-b")
                add(hostPath)
            }
        }
        // Phone storage where Android has it, readable with All Files Access, and `/sdcard` naming it
        // (docs/phone-agent-plan.md L8). Bound in every launch, so a terminal opened before the access
        // was given reads it once it is.
        paths.shared?.takeIf { it.isDirectory }?.let { shared ->
            File(rootfs, shared.path.removePrefix("/")).mkdirs()
            linkSdcard(shared)
            add("-b")
            add(shared.path)
        }
        workspaces.root.mkdirs()
        File(rootfs, WorkspacePaths.GUEST_ROOT.removePrefix("/")).mkdirs()
        add("-b")
        // A workspace's name never reaches this argument, so no name can split it.
        add("${workspaces.root.path}:${WorkspacePaths.GUEST_ROOT}")
        add("-w")
        add(guestWorkingDirectory)
        // `env -i` starts the program with these variables alone, then replaces itself with it: PRoot's
        // own never reach the guest, and the program keeps env's pid.
        add(GUEST_ENV)
        add("-i")
        variables.forEach { (name, value) -> add("$name=$value") }
        addAll(guestCommand)
    }

    /** `/sdcard` in the root filesystem, a link to [shared]; something the user put there is left as it is. */
    private fun linkSdcard(shared: File) {
        val link = File(rootfs, GuestPaths.SDCARD.removePrefix("/")).toPath()
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) return
        runCatching { Files.createSymbolicLink(link, shared.toPath()) }
    }

    companion object {
        const val GUEST_HOME = "/root"

        /**
         * What every file iCode writes into the runtime says of itself, in a comment: the first line,
         * or after a script's `#!` (docs/runtime-contract-plan.md §6). `grep -rl` finds them all.
         */
        const val MANAGED = "Managed by iCode:"

        /** What the runtime is, its contexts, and what Android and PRoot cannot do: iCode's own file. */
        const val README_PATH = "/etc/icode/README.md"
        private const val README_ASSET = "runtime/README.md"
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

        /** Root's login shell in the runtime [paths] reach ([loginShell]). */
        fun loginShell(paths: GuestPaths): String = recordedShell(paths)?.takeIf { shellAvailable(paths, it) } ?: DEFAULT_SHELL

        /** The shell the runtime's `/etc/passwd` gives root, installed or not; null where it records none. */
        fun recordedShell(paths: GuestPaths): String? = runCatching {
            paths.hostOf("/etc/passwd").useLines { lines -> lines.firstOrNull { it.startsWith("root:") } }
        }.getOrNull()?.split(':')?.getOrNull(PASSWD_SHELL_FIELD)?.takeIf { it.startsWith('/') }

        /** [shellPath] is a file in the runtime, its links followed as the runtime follows them. */
        fun shellAvailable(paths: GuestPaths, shellPath: String): Boolean = runCatching { paths.hostOf(shellPath).isFile }.getOrDefault(false)

        private const val GUEST_ENV = "/usr/bin/env"
        /** What a terminal starts when the recorded shell is not installed, and every other context's `SHELL` then. */
        private const val DEFAULT_SHELL = "/bin/bash"
        /** `name:password:uid:gid:gecos:home:shell`. */
        private const val PASSWD_SHELL_FIELD = 6
    }
}
