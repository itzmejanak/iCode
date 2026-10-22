package dev.icode.mobile.domain

// Runtime tools (docs/runtime-tools-plan.md): what the Linux runtime can install — chosen on the
// Set Up Tools page or in Runtime Tools, or installed when a feature that needs one is used.

/** A tool the user chooses; [packages] are what apt installs for it, and [setup] what follows them. */
data class RuntimeTool(
    /** Stable: what a choice and a measured download are keyed by. */
    val id: String,
    val name: String,
    val summary: String,
    /** What in iCode needs it; null when nothing in iCode does. */
    val usedBy: String?,
    val packages: List<String>,
    /** In Standard: what iCode's own features use. */
    val recommended: Boolean = false,
    /**
     * The data layer's setup, by id, run after [packages] (docs/android-toolchain-plan.md §4.1): what
     * apt has no package for. Null for a tool apt installs whole.
     */
    val setup: String? = null,
    /** The licence installing it accepts, by id: its text is shown, and accepted, before it is installed. */
    val licence: String? = null,
    /** The tools it needs, by id (docs/android-ndk-plan.md §3.1): ticked with it, and it goes when they go. */
    val requires: List<String> = emptyList(),
)

/** Every tool iCode offers. A feature names the one it needs, never a package or a path. */
object RuntimeTools {
    val Git = RuntimeTool(
        "git", "Git", "Version control", "Source Control, Clone, Pull and Push", listOf("git"), recommended = true,
    )
    val SshClient = RuntimeTool(
        "ssh-client", "SSH client", "OpenSSH: ssh, scp and ssh-keygen", "SSH keys and git@ remotes",
        listOf("openssh-client"), recommended = true,
    )
    val GnuPg = RuntimeTool(
        "gnupg", "GnuPG", "gpg and its agent: OpenPGP keys and signatures", "Signed commits, the passphrase asked in iCode",
        listOf("gpg", "gpg-agent"),
    )
    val Ripgrep = RuntimeTool(
        "ripgrep", "ripgrep", "Fast text search", "Workspace search", listOf("ripgrep"), recommended = true,
    )
    val AndroidSdk = RuntimeTool(
        "android-sdk", "Android SDK", "JDK 17, SDK Platform 36 and Build-Tools 36, with build tools that run on ARM64",
        "Build & Run for Android apps", listOf("openjdk-17-jdk-headless"), setup = "android-sdk", licence = "android-sdk-license",
    )

    /** The CMake and the NDKs it installs (docs/android-ndk-plan.md §2.3): Android Studio's CMake, and AGP 9's and AGP 8.7–8.13's NDKs. */
    const val CMAKE_VERSION = "3.22.1"
    const val NDK_28 = "28.2.13676358"
    const val NDK_27 = "27.0.12077973"

    val Cmake = RuntimeTool(
        "cmake", "CMake $CMAKE_VERSION", "CMake and Ninja for C and C++ builds, built for ARM64", NATIVE_USE, emptyList(),
        setup = "cmake", licence = AndroidSdk.licence, requires = listOf(AndroidSdk.id),
    )
    val Ndk28 = ndk(NDK_28, "Android Gradle plugin 9's NDK")
    val Ndk27 = ndk(NDK_27, "Android Gradle plugin 8.7–8.13's NDK")

    /** The NDKs by revision, newest first: a build with C or C++ needs one of them. */
    val ndks: Map<String, RuntimeTool> = linkedMapOf(NDK_28 to Ndk28, NDK_27 to Ndk27)

    val Curl = RuntimeTool("curl", "curl", "Downloads over HTTP and HTTPS", null, listOf("curl", "ca-certificates"))
    val Python = RuntimeTool(
        "python", "Python 3", "The interpreter, pip and venv", null, listOf("python3", "python3-pip", "python3-venv"),
    )
    val BuildTools = RuntimeTool(
        "build-tools", "Build tools", "gcc, g++ and make: native npm and pip modules compile with them", null,
        listOf("build-essential"),
    )

    /** In the order the page lists them: iCode's own first, and what a tool requires before it, so an install sets that up first. */
    val all: List<RuntimeTool> = listOf(Git, SshClient, GnuPg, Ripgrep, AndroidSdk, Cmake, Ndk28, Ndk27, Curl, Python, BuildTools)

    /** [id]'s tool; null for one this build does not offer. */
    fun byId(id: String): RuntimeTool? = all.firstOrNull { it.id == id }

    /** An NDK row's id — its setup's, and its pin's in the runtime manifest — by its major version: `android-ndk-28`. */
    fun ndkId(revision: String) = "android-ndk-${revision.substringBefore('.')}"

    /** An NDK row, as Android Studio's SDK Tools tab names it. */
    private fun ndk(revision: String, use: String) = RuntimeTool(
        ndkId(revision), "NDK (Side by side) $revision", "C and C++ for Android apps: $use, built for ARM64",
        NATIVE_USE, emptyList(), setup = ndkId(revision), licence = AndroidSdk.licence, requires = listOf(Cmake.id, AndroidSdk.id),
    )

    /** Standard's choice. */
    val standard: Set<String> = all.filter { it.recommended }.mapTo(linkedSetOf()) { it.id }
}

private const val NATIVE_USE = "Build & Run for apps with C or C++"

/** What installing one tool would fetch now: each file's name and size — apt's archives, then its setup's. */
data class RuntimeToolDownload(val archives: Map<String, Long>)

/** What the runtime holds: dpkg's packages, and each setup whose receipt this build's pins match. */
data class RuntimeInventory(val packages: Set<String>, val setups: Set<String> = emptySet())

/** What Apply does: the tools to add and to take away, as apt's packages. */
data class RuntimeToolPlan(
    val install: List<RuntimeTool>,
    val remove: List<RuntimeTool>,
    val installPackages: List<String>,
    /** [remove]'s packages, less any a tool that stays also lists. */
    val removePackages: List<String>,
) {
    val isEmpty: Boolean get() = install.isEmpty() && remove.isEmpty()

    /** The licences [install] accepts, by id, each once. */
    val licences: List<String> get() = install.mapNotNull { it.licence }.distinct()
}

object RuntimeToolRules {
    /** Installed when dpkg has every one of its packages, and its setup's receipt matches. */
    fun isInstalled(tool: RuntimeTool, installed: RuntimeInventory): Boolean =
        tool.packages.all { it in installed.packages } && (tool.setup == null || tool.setup in installed.setups)

    /** What the page opens on: Standard, and whatever is installed already — nothing goes unasked. */
    fun initialChoice(installed: RuntimeInventory): Set<String> =
        RuntimeTools.standard + RuntimeTools.all.filter { isInstalled(it, installed) }.map { it.id }

    /**
     * [choice] with [id] ticked or unticked, kept whole (docs/android-ndk-plan.md §3.1): ticking a tool
     * ticks what it requires, and unticking one unticks what requires it.
     */
    fun toggle(choice: Set<String>, id: String, catalog: List<RuntimeTool> = RuntimeTools.all): Set<String> =
        if (id in choice) choice - catalog.filter { id in withRequirements(setOf(it.id), catalog) }.map { it.id }.toSet()
        else withRequirements(choice + id, catalog)

    /** [chosen], and what each chosen tool requires, however deep. */
    fun withRequirements(chosen: Set<String>, catalog: List<RuntimeTool> = RuntimeTools.all): Set<String> {
        val byId = catalog.associateBy { it.id }
        val whole = linkedSetOf<String>()
        fun add(id: String) {
            if (whole.add(id)) byId[id]?.requires?.forEach(::add)
        }
        chosen.forEach(::add)
        return whole
    }

    /** What Apply does for [chosen], read with its requirements: nothing is installed without what it needs. */
    fun plan(
        chosen: Set<String>,
        installed: RuntimeInventory,
        catalog: List<RuntimeTool> = RuntimeTools.all,
    ): RuntimeToolPlan {
        val whole = withRequirements(chosen, catalog)
        val install = catalog.filter { it.id in whole && !isInstalled(it, installed) }
        val remove = catalog.filter { it.id !in whole && isInstalled(it, installed) }
        val kept = catalog.filter { it.id in whole }.flatMapTo(hashSetOf()) { it.packages }
        return RuntimeToolPlan(
            install = install,
            remove = remove,
            installPackages = install.flatMap { it.packages }.distinct(),
            removePackages = remove.flatMap { it.packages }.distinct().filterNot { it in kept },
        )
    }

    /**
     * What installing [tools] downloads, each archive once however many of them need it; null
     * until each one's download has been measured.
     */
    fun downloadBytes(tools: List<RuntimeTool>, downloads: Map<String, RuntimeToolDownload>): Long? {
        val archives = HashMap<String, Long>()
        tools.forEach { tool -> archives.putAll(downloads[tool.id]?.archives ?: return null) }
        return archives.values.sum()
    }

    /**
     * The page's steps, in order, as Android Studio's setup wizard has them: Set Up Tools asks the
     * install type first, and lists the tools only for Custom; Runtime Tools lists them, then
     * confirms the change. Either ends on the License Agreement when what it installs has [licences].
     */
    fun steps(setup: Boolean, custom: Boolean, licences: Boolean): List<RuntimeToolStep> = when {
        !setup -> listOf(RuntimeToolStep.Select, RuntimeToolStep.Verify)
        custom -> listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Select, RuntimeToolStep.Verify)
        else -> listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Verify)
    } + listOfNotNull(RuntimeToolStep.Licence.takeIf { licences })

    /** What [tool]'s row says at its end, [ticked] or not. */
    fun status(tool: RuntimeTool, ticked: Boolean, state: RuntimeToolsState): RuntimeToolStatus = when {
        state.isInstalled(tool) -> if (ticked) RuntimeToolStatus.Installed else RuntimeToolStatus.WillBeRemoved
        else -> state.downloads[tool.id]?.let { RuntimeToolStatus.Download(it.archives.values.sum()) }
            ?: if (state.measuring) RuntimeToolStatus.Measuring else RuntimeToolStatus.SizeUnknown
    }

    /**
     * What [plan] costs, as the page's foot says it: "Download 7.1 MB · Remove 1 tool · 42.3 GB
     * available" — the download as apt measured it, never a figure written here.
     */
    fun summary(plan: RuntimeToolPlan?, state: RuntimeToolsState, availableBytes: Long?): String {
        val change = when {
            plan == null -> "Reading what is installed…"
            plan.isEmpty -> "Nothing to change"
            else -> listOfNotNull(
                plan.install.takeIf { it.isNotEmpty() }?.let {
                    downloadBytes(it, state.downloads)?.let { bytes -> "Download ${ByteSize.format(bytes)}" }
                        ?: if (state.measuring) "Measuring the download…" else "Download size unknown"
                },
                plan.remove.takeIf { it.isNotEmpty() }?.let { "Remove ${countOf(it.size, "tool")}" },
            ).joinToString(" · ")
        }
        return listOfNotNull(change, availableBytes?.let { "${ByteSize.format(it)} available" }).joinToString(" · ")
    }
}

/** The page's steps (docs/runtime-tools-plan.md §8). */
enum class RuntimeToolStep { InstallType, Select, Verify, Licence }

/** What a tool's row says at its end. */
sealed interface RuntimeToolStatus {
    data object Installed : RuntimeToolStatus
    /** Installed, and unticked: Apply removes it. */
    data object WillBeRemoved : RuntimeToolStatus
    /** Not installed: what installing it downloads. */
    data class Download(val bytes: Long) : RuntimeToolStatus
    data object Measuring : RuntimeToolStatus
    data object SizeUnknown : RuntimeToolStatus
}

/** The runtime's tools, as their page and the features that need one see them. */
data class RuntimeToolsState(
    /** What is installed; null until it has been read. */
    val installed: RuntimeInventory? = null,
    /** What each tool not installed would download, by id, once measured. */
    val downloads: Map<String, RuntimeToolDownload> = emptyMap(),
    /** apt is reading the lists or measuring. */
    val measuring: Boolean = false,
    /** Why a tool could not be measured. */
    val measureFailure: String? = null,
    /** The apt run in hand, or the page's last failure until read. */
    val work: RuntimeToolWork? = null,
    /** The runtime was just built and the tools page has not been answered: onboarding offers it. */
    val offerPending: Boolean = false,
    /** Each licence a tool accepts, by id: its text, as the License Agreement shows it. */
    val licences: Map<String, String> = emptyMap(),
) {
    fun isInstalled(tool: RuntimeTool): Boolean =
        installed?.let { RuntimeToolRules.isInstalled(tool, it) } == true

    /** CMake and an NDK are installed: Build & Run builds an app with C or C++ (docs/android-ndk-plan.md §3.5). */
    val buildsNative: Boolean get() = isInstalled(RuntimeTools.Cmake) && RuntimeTools.ndks.values.any(::isInstalled)

    /** The run installing [tool] now, for a button that waits on it; null when none does. */
    fun installing(tool: RuntimeTool): RuntimeToolWork? =
        work?.takeIf { it.failure == null && it.action == RuntimeToolAction.Install && it.includes(tool) }
}

enum class RuntimeToolAction { Install, Remove }

/**
 * A tool's setup as it goes, in its own words: "Downloading Android SDK Command-line Tools", how
 * far, the file in hand, and whether stopping now leaves nothing half done.
 */
data class ToolSetupProgress(val step: String, val fraction: Float? = null, val item: String? = null, val cancellable: Boolean)

/** The one run for tools — apt, then any setup: what the page, a button waiting on it and its notification show. */
data class RuntimeToolWork(
    val action: RuntimeToolAction,
    val tools: List<RuntimeTool>,
    val progress: AptProgress = AptProgress(),
    /** The setup in hand, once apt is done; null while apt runs. */
    val setup: ToolSetupProgress? = null,
    /** apt's, or the setup's, last lines, for Details. */
    val log: List<String> = emptyList(),
    /** Why it stopped, in apt's or the setup's words; null while it runs. */
    val failure: String? = null,
) {
    /** "Installing Git, SSH client". */
    val title: String
        get() = (if (action == RuntimeToolAction.Install) "Installing " else "Removing ") + tools.joinToString { it.name }

    /**
     * Stopping apt while it reads the lists or downloads leaves nothing half done; once dpkg
     * unpacks, stopping leaves an interrupted package database, so there is no Cancel. A setup
     * says for itself.
     */
    val cancellable: Boolean
        get() = failure == null &&
            (setup?.cancellable ?: (progress.phase == AptPhase.Preparing || progress.phase == AptPhase.Downloading))

    /** The step in hand: apt's, or the setup's once apt is done. */
    val step: String get() = setup?.step ?: progress.step
    val item: String? get() = if (setup != null) setup.item else progress.item
    val fraction: Float? get() = if (setup != null) setup.fraction else progress.fraction
    /** "4 / 6" while dpkg works; a setup says its share by [fraction] alone. */
    val counts: String? get() = if (setup != null) null else progress.counts

    fun includes(tool: RuntimeTool): Boolean = tools.any { it.id == tool.id }

    /** "Installing SSH client · Setting up 3 of 6": what a button waiting on it says. */
    val status: String get() = "$title · $step"
}
