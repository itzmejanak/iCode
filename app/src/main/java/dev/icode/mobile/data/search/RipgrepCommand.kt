package dev.icode.mobile.data.search

/** Deterministic ripgrep arguments; user text is always an argv value after `--`. */
internal object RipgrepCommand {
    private val DISCOVERY = listOf(
        "--no-config",
        "--hidden",
        "--no-require-git",
        "--glob=!.git",
    )

    fun files(): List<String> = listOf("rg") + DISCOVERY + listOf("--files", "--null", ".")

    fun content(
        query: String,
        matchCase: Boolean,
        wholeWord: Boolean,
        regex: Boolean,
        maxMatchingLinesPerFile: Int,
    ): List<String> = buildList {
        add("rg")
        addAll(DISCOVERY)
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
