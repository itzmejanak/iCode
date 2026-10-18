package dev.icode.mobile.ui.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import dev.icode.mobile.domain.EditorTextRules
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.LineChangeKind
import dev.icode.mobile.domain.kind
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandleResult
import io.github.rosemoe.sora.lang.smartEnter.NewlineHandler
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer

/** The change markers' colours: added, modified, deleted. */
internal class MarkerColors(val added: Int, val modified: Int, val deleted: Int)

/**
 * Sora's editor as iCode uses it (docs/editor-plan.md): Git change markers in the gutter, Backspace
 * back to the previous tab stop in leading whitespace, and every line break typed or pasted
 * written as the file's own.
 */
internal class IcodeCodeEditor(context: Context) : CodeEditor(context) {
    /** The open file's changes against Git's index; drawn while there are any. */
    var changes: List<GitDiffHunk> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var markerColors = MarkerColors(0, 0, 0)

    /** The file's line break ([EditorTextRules.separatorOf]). */
    var separator: String = "\n"

    init {
        // Room for the markers between the line numbers and the divider, kept whether or not a
        // file has changes, so the text never moves when they come.
        setDividerMargin(dpUnit * MARKER_LANE_DP, dividerMarginRight)
        // VS Code's Backspace on an indented empty line removes indentation, not the line.
        props.deleteEmptyLineFast = false
    }

    override fun onCreateRenderer(): EditorRenderer = ChangeMarkerRenderer(this)

    override fun deleteText() {
        val cursor = cursor
        if (!cursor.isSelected) {
            val line = cursor.leftLine
            val column = cursor.leftColumn
            val count = EditorTextRules.deleteLeftCount(text.getLine(line).subSequence(0, column).toString(), tabWidth)
            if (count > 1) return text.delete(line, column - count, line, column)
        }
        super.deleteText()
    }

    override fun commitText(text: CharSequence, applyAutoIndent: Boolean, applySymbolCompletion: Boolean) =
        super.commitText(EditorTextRules.withLineBreaks(text.toString(), separator), applyAutoIndent, applySymbolCompletion)

    /** Where the markers are drawn, from the gutter's divider: [MARKER_GAP_DP] past the line numbers. */
    internal fun markerLeft(dividerRight: Float): Float = dividerRight - dividerWidth - dividerMarginLeft + dpUnit * MARKER_GAP_DP

    private companion object {
        /** Sora's own 2 dp gap, the 3 dp bar of VS Code's `scm.diffDecorationsGutterWidth`, and 1 dp before the divider. */
        const val MARKER_LANE_DP = 6f
        const val MARKER_GAP_DP = 2f
    }
}

/** Sora's renderer, drawing each change in the gutter where Sora draws its own gutter colours. */
private class ChangeMarkerRenderer(private val view: IcodeCodeEditor) : EditorRenderer(view) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val wedge = Path()

    override fun drawUserGutterBackground(canvas: Canvas, right: Int) {
        super.drawUserGutterBackground(canvas, right)
        val changes = view.changes
        if (changes.isEmpty()) return
        val unit = view.dpUnit
        val left = view.markerLeft(right.toFloat())
        val first = view.firstVisibleLine
        val last = view.lastVisibleLine
        val lines = view.text.lineCount
        changes.forEach { change ->
            when (change.kind) {
                LineChangeKind.Added, LineChangeKind.Modified -> {
                    val from = change.newStart - 1
                    val to = minOf(from + change.newCount - 1, lines - 1)
                    if (to < first || from > last) return@forEach
                    paint.color = if (change.kind == LineChangeKind.Added) view.markerColors.added else view.markerColors.modified
                    canvas.drawRect(left, top(maxOf(from, first)), left + BAR_DP * unit, bottom(minOf(to, last)), paint)
                }
                LineChangeKind.Deleted -> {
                    // Between the line before the removed ones and the line after: the top of the line after.
                    val after = change.newStart
                    if (after < first || after > last + 1) return@forEach
                    val y = if (after < lines) top(after) else bottom(lines - 1)
                    val half = WEDGE_DP * unit / 2
                    paint.color = view.markerColors.deleted
                    wedge.reset()
                    wedge.moveTo(left, y - half)
                    wedge.lineTo(left + WEDGE_DP * unit, y)
                    wedge.lineTo(left, y + half)
                    wedge.close()
                    canvas.drawPath(wedge, paint)
                }
            }
        }
    }

    /** As Sora finds a line's first row, in `drawUserGutterBackground`. */
    private fun firstRow(line: Int): Int = (view.layout.getCharLayoutOffset(line, 0)[0] / view.rowHeight).toInt() - 1

    private fun top(line: Int): Float = (view.getRowTop(firstRow(line)) - view.offsetY).toFloat()

    private fun bottom(line: Int): Float = (view.getRowBottom(firstRow(line) + view.layout.getRowCountForLine(line) - 1) - view.offsetY).toFloat()

    private companion object {
        const val BAR_DP = 3f
        const val WEDGE_DP = 6f
    }
}

/**
 * [language] with its Enter handlers' line breaks written as the file's ([separator]): Sora's
 * TextMate handler writes `\n`, and how far the caret moves back counts them.
 */
internal class FileLineBreaksLanguage(val language: Language, private val separator: () -> String) : Language by language {
    private val handlers: Array<NewlineHandler?>? =
        language.newlineHandlers?.let { inner -> Array(inner.size) { index -> inner[index]?.let(::FileLineBreaksHandler) } }

    override fun getNewlineHandlers(): Array<NewlineHandler?>? = handlers

    private inner class FileLineBreaksHandler(private val handler: NewlineHandler) : NewlineHandler {
        override fun matchesRequirement(text: Content, position: CharPosition, style: Styles?) = handler.matchesRequirement(text, position, style)

        override fun handleNewline(text: Content, position: CharPosition, style: Styles?, tabSize: Int): NewlineHandleResult {
            val result = handler.handleNewline(text, position, style, tabSize)
            val written = result.text.toString()
            val after = written.substring(written.length - result.shiftLeft)
            return NewlineHandleResult(EditorTextRules.withLineBreaks(written, separator()), EditorTextRules.withLineBreaks(after, separator()).length)
        }
    }
}
