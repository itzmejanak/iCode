package dev.icode.mobile.data.git

import dev.icode.mobile.domain.GitProgress

/**
 * Reads Git's own progress reporting off stderr.
 *
 * Git redraws a counter in place with carriage returns
 * (`Receiving objects:  45% (450/1000)\r`), so the stream is split on CR as
 * well as LF and only the newest phase is reported. Every percentage shown to
 * the user comes from Git; nothing here estimates one (PR-GIT-12).
 */
internal object GitProgressParser {

    /** `Resolving deltas:  62% (…)`, with or without git's `remote: ` prefix. */
    private val PERCENT = Regex("""^(?:remote:\s*)?([A-Za-z][A-Za-z ,.'-]*?):\s+(\d{1,3})%""")

    /** The most recent phase Git reported, or null while it is still silent. */
    fun latest(stderr: String): GitProgress? =
        chunks(stderr).asReversed().firstNotNullOfOrNull { parse(it) }

    fun parse(line: String): GitProgress? {
        val match = PERCENT.find(line.trim()) ?: return null
        val percent = match.groupValues[2].toIntOrNull() ?: return null
        return GitProgress(match.groupValues[1].trim(), (percent / 100f).coerceIn(0f, 1f))
    }

    /**
     * Git's output as a terminal would show it: at most [limit] lines, with a
     * phase's in-place redraws collapsed onto the single newest one.
     *
     * Without collapsing, one clone would scroll a hundred near-identical
     * "Receiving objects" lines past the user.
     */
    fun outputLines(stderr: String, limit: Int = DEFAULT_OUTPUT_LINES): List<String> {
        val collapsed = mutableListOf<String>()
        chunks(stderr).forEach { line ->
            val phase = phaseOf(line)
            val previousPhase = collapsed.lastOrNull()?.let { phaseOf(it) }
            if (phase != null && phase == previousPhase) {
                collapsed[collapsed.lastIndex] = line
            } else {
                collapsed += line
            }
        }
        return collapsed.takeLast(limit)
    }

    /** The label before the colon, which identifies one redrawing phase. */
    private fun phaseOf(line: String): String? {
        val match = PERCENT.find(line.trim()) ?: return null
        return match.groupValues[1].trim()
    }

    private fun chunks(stderr: String): List<String> =
        stderr.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    const val DEFAULT_OUTPUT_LINES = 8
}
