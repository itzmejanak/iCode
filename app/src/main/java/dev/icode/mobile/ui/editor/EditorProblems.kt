package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.Severity
import dev.icode.mobile.domain.TextPosition
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.text.Content

/**
 * A file's problems drawn under their text (docs/editor-and-language-services.md): the language servers' and a build's,
 * each where it is, and Sora's own tooltip with its message when the caret is in one. The editor only
 * draws them; their owner is the data layer.
 */
internal object EditorProblems {
    /** At most this many are drawn in one file. */
    const val MAX_DRAWN = 1_000

    fun containerOf(problems: List<Diagnostic>, text: Content): DiagnosticsContainer? {
        if (problems.isEmpty()) return null
        val container = DiagnosticsContainer()
        problems.asSequence().filter { it.range != null }.take(MAX_DRAWN).forEachIndexed { index, problem ->
            val range = problem.range ?: return@forEachIndexed
            val start = indexOf(text, range.start)
            var end = indexOf(text, range.end).coerceAtLeast(start)
            // An empty range marks a place: the character there is drawn, or the one before at a line's end.
            if (end == start) end = (start + 1).coerceAtMost(text.length)
            val from = if (end == start && start > 0) start - 1 else start
            container.addDiagnostic(DiagnosticRegion(from, end, severityOf(problem.severity), index.toLong(), detailOf(problem)))
        }
        return container
    }

    private fun indexOf(text: Content, position: TextPosition): Int {
        if (text.lineCount == 0) return 0
        val line = position.line.coerceIn(0, text.lineCount - 1)
        return text.getCharIndex(line, position.column.coerceIn(0, text.getColumnCount(line)))
    }

    private fun severityOf(severity: Severity): Short = when (severity) {
        Severity.Error -> DiagnosticRegion.SEVERITY_ERROR
        Severity.Warning -> DiagnosticRegion.SEVERITY_WARNING
        Severity.Info -> DiagnosticRegion.SEVERITY_TYPO
    }

    private fun detailOf(problem: Diagnostic) = DiagnosticDetail(
        briefMessage = problem.message,
        detailedMessage = listOfNotNull(problem.source, problem.code).joinToString(" "),
    )
}
