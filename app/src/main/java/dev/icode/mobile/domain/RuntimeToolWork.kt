package dev.icode.mobile.domain

// The run that installs or removes runtime tools (docs/runtime-tools-plan.md §3.4): apt, then each
// tool's setup, as the page, a button waiting on it and its notification see it.

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
