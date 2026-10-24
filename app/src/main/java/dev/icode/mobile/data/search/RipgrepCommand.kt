package dev.icode.mobile.data.search

/** Deterministic ripgrep arguments; user text is always an argv value after `--`. */
internal object RipgrepCommand {
    private val DISCOVERY = listOf(
        "--no-config",
        "--hidden",
        "--no-require-git",
        "--glob=!.git",
    )

    /** Every file but Git's own folder, hidden ones included; what ignore files leave out only when [ignored]. */
    private fun discovery(ignored: Boolean) = if (ignored) DISCOVERY + "--no-ignore" else DISCOVERY

    fun files(ignored: Boolean): List<String> = listOf("rg") + discovery(ignored) + listOf("--files", "--null", ".")

    fun content(
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        maxMatchingLinesPerFile: Int,
        ignored: Boolean,
    ): List<String> = buildList {
        add("rg")
        addAll(discovery(ignored))
        add("--json")
        add("--max-count=$maxMatchingLinesPerFile")
        add(if (matchCase) "--case-sensitive" else "--ignore-case")
        if (wholeWord) add("--word-regexp")
        if (!regex) add("--fixed-strings")
        add("--")
        add(query)
        add(".")
    }
}
