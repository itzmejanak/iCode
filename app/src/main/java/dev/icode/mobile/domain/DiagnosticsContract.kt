package dev.icode.mobile.domain

// Problems reported for the workspace (docs/editor-and-language-services.md): a build's, and the language servers'.

data class Diagnostic(
    val severity: Severity,
    /** The file, relative to the workspace's top, as the Problems panel heads it. */
    val file: String,
    val message: String,
    /** Where, as shown: `12:5`, one-based; derived from [range] where there is one. */
    val location: String,
    /** The tool that reported it, as it names itself. */
    val source: String,
    /** Exactly where, zero-based, columns in UTF-16 units as the editor counts them; null when only a line or nothing is known. */
    val range: TextRange? = null,
    /** The tool's own code for it (`E501`, `ts2304`). */
    val code: String? = null,
    /** The language service that reported it; null for a build's. */
    val serviceId: String? = null,
)

enum class Severity { Error, Warning, Info }

/** A place in a text: zero-based line and column, the column in UTF-16 units (LSP's default, Sora's own). */
data class TextPosition(val line: Int, val column: Int)

/** From [start] up to, not including, [end]. */
data class TextRange(val start: TextPosition, val end: TextPosition) {
    companion object {
        /** A one-based [line] and [column] as a build tool prints them, as an empty range there. */
        fun at(line: Int, column: Int = 1): TextRange = TextPosition((line - 1).coerceAtLeast(0), (column - 1).coerceAtLeast(0)).let { TextRange(it, it) }
    }
}

object DiagnosticRules {
    /** `12:5` for [range], one-based, as builds print it. */
    fun locationOf(range: TextRange): String = "${range.start.line + 1}:${range.start.column + 1}"

    /** [problems] about the file at [location] in the workspace at [workspace]. */
    fun forFile(problems: List<Diagnostic>, workspace: String?, location: String?): List<Diagnostic> {
        if (workspace == null || location == null) return emptyList()
        val file = location.removePrefix(workspace.trimEnd('/') + "/").takeIf { it != location } ?: return emptyList()
        return problems.filter { it.file == file }
    }

    /** How many problems a file has, or the files beneath a folder, and the worst of them. */
    data class Count(val count: Int, val worst: Severity)

    /**
     * Errors and warnings by location (PR-PROB-01): each file's, and each folder's for every file
     * beneath it, up to [workspace]'s top, for the Explorer's rows.
     */
    fun countsByLocation(problems: List<Diagnostic>, workspace: String?): Map<String, Count> {
        val top = workspace?.trimEnd('/') ?: return emptyMap()
        val counts = HashMap<String, Count>()
        problems.filter { it.severity != Severity.Info }.forEach { problem ->
            var location = "$top/${problem.file}"
            while (location.length > top.length) {
                val before = counts[location]
                counts[location] = Count((before?.count ?: 0) + 1, minOf(before?.worst ?: problem.severity, problem.severity))
                location = location.substringBeforeLast('/')
            }
        }
        return counts
    }

    /** Errors, then warnings, then the rest; by file and place within each. */
    val order: Comparator<Diagnostic> = compareBy<Diagnostic>({ it.severity.ordinal }, { it.file }, { it.range?.start?.line ?: 0 }, { it.range?.start?.column ?: 0 })
}
