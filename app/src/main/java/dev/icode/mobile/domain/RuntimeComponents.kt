package dev.icode.mobile.domain

// Runtime components (docs/runtime-components-plan.md): the tools the Linux runtime can install —
// chosen on the Components page, or installed when a feature that needs one is used.

/** A tool the user chooses; [packages] are what apt installs for it. */
data class RuntimeComponent(
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

/** Every component iCode offers. A feature names the one it needs, never a package or a path. */
object RuntimeComponents {
    val Git = RuntimeComponent(
        "git", "Git", "Version control", "Source Control, Clone, Pull and Push", listOf("git"), recommended = true,
    )
    val SshClient = RuntimeComponent(
        "ssh-client", "SSH client", "OpenSSH: ssh, scp and ssh-keygen", "SSH keys and git@ remotes",
        listOf("openssh-client"), recommended = true,
    )
    val Ripgrep = RuntimeComponent(
        "ripgrep", "ripgrep", "Fast text search", "Workspace search", listOf("ripgrep"), recommended = true,
    )
    val Curl = RuntimeComponent("curl", "curl", "Downloads over HTTP and HTTPS", null, listOf("curl", "ca-certificates"))
    val Python = RuntimeComponent(
        "python", "Python 3", "The interpreter, pip and venv", null, listOf("python3", "python3-pip", "python3-venv"),
    )
    val BuildTools = RuntimeComponent(
        "build-tools", "Build tools", "gcc, g++ and make: native npm and pip modules compile with them", null,
        listOf("build-essential"),
    )

    /** In the order the page lists them: iCode's own first. */
    val all: List<RuntimeComponent> = listOf(Git, SshClient, Ripgrep, Curl, Python, BuildTools)

    /** Standard's choice. */
    val standard: Set<String> = all.filter { it.recommended }.mapTo(linkedSetOf()) { it.id }
}

/** What apt would fetch to install one component now: each archive's file name and size. */
data class ComponentDownload(val archives: Map<String, Long>)

/** What Apply does: the components to add and to take away, as apt's packages. */
data class ComponentPlan(
    val install: List<RuntimeComponent>,
    val remove: List<RuntimeComponent>,
    val installPackages: List<String>,
    /** [remove]'s packages, less any a component that stays also lists. */
    val removePackages: List<String>,
) {
    val isEmpty: Boolean get() = install.isEmpty() && remove.isEmpty()
}

object ComponentRules {
    /** Installed when dpkg has every one of its packages. */
    fun isInstalled(component: RuntimeComponent, installedPackages: Set<String>): Boolean =
        component.packages.all { it in installedPackages }

    /** What the page opens on: Standard, and whatever is installed already — nothing goes unasked. */
    fun initialChoice(installedPackages: Set<String>): Set<String> =
        RuntimeComponents.standard + RuntimeComponents.all.filter { isInstalled(it, installedPackages) }.map { it.id }

    fun plan(
        chosen: Set<String>,
        installedPackages: Set<String>,
        catalog: List<RuntimeComponent> = RuntimeComponents.all,
    ): ComponentPlan {
        val install = catalog.filter { it.id in chosen && !isInstalled(it, installedPackages) }
        val remove = catalog.filter { it.id !in chosen && isInstalled(it, installedPackages) }
        val kept = catalog.filter { it.id in chosen }.flatMapTo(hashSetOf()) { it.packages }
        return ComponentPlan(
            install = install,
            remove = remove,
            installPackages = install.flatMap { it.packages }.distinct(),
            removePackages = remove.flatMap { it.packages }.distinct().filterNot { it in kept },
        )
    }

    /**
     * What installing [components] downloads, each archive once however many of them need it; null
     * until each one's download has been measured.
     */
    fun downloadBytes(components: List<RuntimeComponent>, downloads: Map<String, ComponentDownload>): Long? {
        val archives = HashMap<String, Long>()
        components.forEach { component -> archives.putAll(downloads[component.id]?.archives ?: return null) }
        return archives.values.sum()
    }
}

/** The runtime's components, as the Components page and the features that need one see them. */
data class RuntimeComponentsState(
    /** What dpkg has installed; null until it has been read. */
    val installedPackages: Set<String>? = null,
    /** What each component not installed would download, by id, once measured. */
    val downloads: Map<String, ComponentDownload> = emptyMap(),
    /** apt is reading the lists or measuring. */
    val measuring: Boolean = false,
    /** Why a component could not be measured. */
    val measureFailure: String? = null,
    /** The apt run in hand, or the page's last failure until read. */
    val work: ComponentWork? = null,
    /** The runtime was just built and the Components page has not been answered: onboarding offers it. */
    val offerPending: Boolean = false,
) {
    fun isInstalled(component: RuntimeComponent): Boolean =
        installedPackages?.let { ComponentRules.isInstalled(component, it) } == true

    /** The run installing [component] now, for a button that waits on it; null when none does. */
    fun installing(component: RuntimeComponent): ComponentWork? =
        work?.takeIf { it.failure == null && it.action == ComponentAction.Install && it.includes(component) }
}

enum class ComponentAction { Install, Remove }

/** The one apt run for components: what the page, a button waiting on it and its notification show. */
data class ComponentWork(
    val action: ComponentAction,
    val components: List<RuntimeComponent>,
    val progress: AptProgress = AptProgress(),
    /** apt's last lines, for Details. */
    val log: List<String> = emptyList(),
    /** Why it stopped, in apt's words; null while it runs. */
    val failure: String? = null,
) {
    /** "Installing Git, SSH client". */
    val title: String
        get() = (if (action == ComponentAction.Install) "Installing " else "Removing ") + components.joinToString { it.name }

    /**
     * Stopping apt while it reads the lists or downloads leaves nothing half done; once dpkg
     * unpacks, stopping leaves an interrupted package database, so there is no Cancel.
     */
    val cancellable: Boolean
        get() = failure == null && (progress.phase == AptPhase.Preparing || progress.phase == AptPhase.Downloading)

    fun includes(component: RuntimeComponent): Boolean = components.any { it.id == component.id }

    /** "Installing SSH client · Setting up 3 of 6": what a button waiting on it says. */
    val status: String get() = "$title · ${progress.step}"
}
