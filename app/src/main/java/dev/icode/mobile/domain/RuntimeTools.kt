package dev.icode.mobile.domain

// Runtime tools (docs/runtime-tools-plan.md): what the Linux runtime can install — chosen on the
// Set Up Tools page or in Runtime Tools, or installed when a feature that needs one is used.

/** A tool the user chooses; [packages] are what apt installs for it. */
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
    val Ripgrep = RuntimeTool(
        "ripgrep", "ripgrep", "Fast text search", "Workspace search", listOf("ripgrep"), recommended = true,
    )
    val Curl = RuntimeTool("curl", "curl", "Downloads over HTTP and HTTPS", null, listOf("curl", "ca-certificates"))
    val Python = RuntimeTool(
        "python", "Python 3", "The interpreter, pip and venv", null, listOf("python3", "python3-pip", "python3-venv"),
    )
    val BuildTools = RuntimeTool(
        "build-tools", "Build tools", "gcc, g++ and make: native npm and pip modules compile with them", null,
        listOf("build-essential"),
    )

    /** In the order the page lists them: iCode's own first. */
    val all: List<RuntimeTool> = listOf(Git, SshClient, Ripgrep, Curl, Python, BuildTools)

    /** Standard's choice. */
    val standard: Set<String> = all.filter { it.recommended }.mapTo(linkedSetOf()) { it.id }
}

/** What apt would fetch to install one tool now: each archive's file name and size. */
data class RuntimeToolDownload(val archives: Map<String, Long>)

/** What Apply does: the tools to add and to take away, as apt's packages. */
data class RuntimeToolPlan(
    val install: List<RuntimeTool>,
    val remove: List<RuntimeTool>,
    val installPackages: List<String>,
    /** [remove]'s packages, less any a tool that stays also lists. */
    val removePackages: List<String>,
) {
    val isEmpty: Boolean get() = install.isEmpty() && remove.isEmpty()
}

object RuntimeToolRules {
    /** Installed when dpkg has every one of its packages. */
    fun isInstalled(tool: RuntimeTool, installedPackages: Set<String>): Boolean =
        tool.packages.all { it in installedPackages }

    /** What the page opens on: Standard, and whatever is installed already — nothing goes unasked. */
    fun initialChoice(installedPackages: Set<String>): Set<String> =
        RuntimeTools.standard + RuntimeTools.all.filter { isInstalled(it, installedPackages) }.map { it.id }

    fun plan(
        chosen: Set<String>,
        installedPackages: Set<String>,
        catalog: List<RuntimeTool> = RuntimeTools.all,
    ): RuntimeToolPlan {
        val install = catalog.filter { it.id in chosen && !isInstalled(it, installedPackages) }
        val remove = catalog.filter { it.id !in chosen && isInstalled(it, installedPackages) }
        val kept = catalog.filter { it.id in chosen }.flatMapTo(hashSetOf()) { it.packages }
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
     * confirms the change.
     */
    fun steps(setup: Boolean, custom: Boolean): List<RuntimeToolStep> = when {
        !setup -> listOf(RuntimeToolStep.Select, RuntimeToolStep.Verify)
        custom -> listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Select, RuntimeToolStep.Verify)
        else -> listOf(RuntimeToolStep.InstallType, RuntimeToolStep.Verify)
    }

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
enum class RuntimeToolStep { InstallType, Select, Verify }

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
    /** What dpkg has installed; null until it has been read. */
    val installedPackages: Set<String>? = null,
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
) {
    fun isInstalled(tool: RuntimeTool): Boolean =
        installedPackages?.let { RuntimeToolRules.isInstalled(tool, it) } == true

    /** The run installing [tool] now, for a button that waits on it; null when none does. */
    fun installing(tool: RuntimeTool): RuntimeToolWork? =
        work?.takeIf { it.failure == null && it.action == RuntimeToolAction.Install && it.includes(tool) }
}

enum class RuntimeToolAction { Install, Remove }

/** The one apt run for tools: what the page, a button waiting on it and its notification show. */
data class RuntimeToolWork(
    val action: RuntimeToolAction,
    val tools: List<RuntimeTool>,
    val progress: AptProgress = AptProgress(),
    /** apt's last lines, for Details. */
    val log: List<String> = emptyList(),
    /** Why it stopped, in apt's words; null while it runs. */
    val failure: String? = null,
) {
    /** "Installing Git, SSH client". */
    val title: String
        get() = (if (action == RuntimeToolAction.Install) "Installing " else "Removing ") + tools.joinToString { it.name }

    /**
     * Stopping apt while it reads the lists or downloads leaves nothing half done; once dpkg
     * unpacks, stopping leaves an interrupted package database, so there is no Cancel.
     */
    val cancellable: Boolean
        get() = failure == null && (progress.phase == AptPhase.Preparing || progress.phase == AptPhase.Downloading)

    fun includes(tool: RuntimeTool): Boolean = tools.any { it.id == tool.id }

    /** "Installing SSH client · Setting up 3 of 6": what a button waiting on it says. */
    val status: String get() = "$title · ${progress.step}"
}
