package dev.icode.mobile.domain

enum class AptPhase { Preparing, Updating, Downloading, Installing, Removing }

/**
 * How far one apt run has got, read from apt's own lines (docs/runtime-tools-plan.md §3.4).
 *
 * Off a terminal apt prints no byte progress, so a download is told by the files fetched, and the
 * install by dpkg's `Setting up` and `Removing` lines against the plan apt printed first.
 */
data class AptProgress(
    val phase: AptPhase = AptPhase.Preparing,
    /** Packages this run sets up or removes, from apt's plan; null until it prints it. */
    val total: Int? = null,
    /** Packages set up or removed so far. */
    val done: Int = 0,
    /** What apt said it has to fetch: "1455 kB". */
    val downloadSize: String? = null,
    /** The package or list in hand. */
    val item: String? = null,
) {
    fun next(line: String): AptProgress {
        val text = line.trim()
        PLAN.find(text)?.let { plan ->
            val (upgraded, installed, reinstalled, removed) = plan.destructured
            return copy(total = upgraded.toInt() + installed.toInt() + (reinstalled.toIntOrNull() ?: 0) + removed.toInt())
        }
        NEED.find(text)?.let { return copy(downloadSize = it.groupValues[1].substringBefore('/')) }
        FETCH.find(text)?.let { fetch ->
            val words = fetch.groupValues[1].split(' ')
            // A package: `<uri> noble/universe arm64 ripgrep arm64 14.1.0-1 [1455 kB]`;
            // a list: `<uri> noble/main arm64 Packages [1774 kB]` or `<uri> noble InRelease`.
            val isPackage = words.size >= 8 && '/' in words[1] && !words[5].startsWith("[")
            // An archive fetched while dpkg works (one missing from the cache) does not undo its phase.
            return if (isPackage) copy(phase = if (counting) phase else AptPhase.Downloading, item = words[3])
            else copy(phase = AptPhase.Updating, item = null)
        }
        if (text.startsWith("Hit:") || text.startsWith("Ign:")) return copy(phase = AptPhase.Updating)
        DPKG.find(text)?.let { step ->
            val (verb, name) = step.destructured
            val item = name.substringBefore(':')
            return when (verb) {
                "Setting up" -> copy(phase = AptPhase.Installing, done = done + 1, item = item)
                "Removing" -> copy(phase = AptPhase.Removing, done = done + 1, item = item)
                "Unpacking" -> copy(phase = AptPhase.Installing, item = item)
                else -> copy(item = item)
            }
        }
        return this
    }

    /** 0..1 by packages set up or removed; null before dpkg starts, when there is no fair share. */
    val fraction: Float?
        get() = total?.takeIf { it > 0 && counting }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }

    /** "4 / 6" while dpkg works: as a task's counts are said. */
    val counts: String? get() = total?.takeIf { counting }?.let { "$done / $it" }

    /** The step, as the page and a waiting button say it. */
    val step: String
        get() = when (phase) {
            AptPhase.Preparing -> "Reading package lists"
            AptPhase.Updating -> "Updating package lists"
            AptPhase.Downloading -> downloadSize?.let { "Downloading $it" } ?: "Downloading"
            AptPhase.Installing -> total?.let { "Setting up $done of $it" } ?: "Setting up"
            AptPhase.Removing -> total?.let { "Removing $done of $it" } ?: "Removing"
        }

    private val counting: Boolean get() = phase == AptPhase.Installing || phase == AptPhase.Removing

    private companion object {
        val PLAN = Regex("""^(\d+) upgraded, (\d+) newly installed, (?:(\d+) reinstalled, )?(\d+) to remove""")
        val NEED = Regex("""^Need to get (.+?) of archives""")
        val FETCH = Regex("""^Get:\d+ (.+)$""")
        /** dpkg's own lines name the version: `Unpacking git (1:2.43.0) over (1:2.42.0) ...`. */
        val DPKG = Regex("""^(Unpacking|Setting up|Removing|Processing triggers for) (\S+) \([^)]*\)(?: over \([^)]*\))? \.\.\.$""")
    }
}
