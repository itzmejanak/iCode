package dev.icode.mobile.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import dev.icode.mobile.R
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.EditorKey
import dev.icode.mobile.domain.EditorSettings
import dev.icode.mobile.domain.EditorTextRules
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.LanguageHighlights
import dev.icode.mobile.domain.LineDiff
import dev.icode.mobile.domain.LineEnding
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.SettingLimits
import dev.icode.mobile.domain.WorkspacePreferences
import dev.icode.mobile.ui.syntax.PaletteColorScheme
import dev.icode.mobile.ui.syntax.SyntaxTheme
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.text.LineSeparator
import io.github.rosemoe.sora.widget.SelectionMovement
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** One view/content/undo history shared by the Editor and Find routes. */
class CodeEditorSession(
    context: Context,
    initialPalette: IcodePalette = IcodePalette.Dark,
    /** The language servers' semantic tokens, drawn over the grammar's colours. */
    highlights: LanguageHighlights = LanguageHighlights.None,
) {
    internal val editor = IcodeCodeEditor(context)
    /** Why the document is not coloured, said under the editor (EditorSyntax). */
    val coloringError: String? get() = syntax.note

    private var location: String? = null
    private var acceptedText: String = ""
    private var pendingLocalText: String? = null
    private var pendingReveal: Pair<Int, Int>? = null
    private var onTextChanged: (String) -> Unit = {}
    private val plainTextLanguage = ConfigurablePlainTextLanguage()
    private val syntax: EditorSyntax = EditorSyntax(editor, plainTextLanguage, ::indent) { semantic.refresh() }
    private val scope = MainScope()
    private val semantic: EditorSemanticTokens = EditorSemanticTokens(scope, coloured = { syntax.textMate != null }, show = { editor.semanticLines = it }) { line ->
        editor.text.takeIf { line < it.lineCount }?.getLineString(line)
    }.apply { source = highlights }
    /** Text the backend already holds — the disk's, the assistant's, a restored draft — is going in: not the user's typing. */
    private var applyingOutside = false
    /** A tap on a changed line's gutter: which change; null leaves the gutter to Sora. */
    var onChangeTapped: ((Int) -> Unit)? = null
    private var preferences = WorkspacePreferences()
    private var appliedEditorSettings: EditorSettings? = null
    private var appliedPalette: IcodePalette? = null
    private val plexTypeface = runCatching { ResourcesCompat.getFont(context, R.font.ibm_plex_mono_regular) }
        .getOrNull() ?: Typeface.MONOSPACE
    var revealVersion by mutableIntStateOf(0)
        private set

    init {
        editor.typefaceText = plexTypeface
        editor.setTextSize(14f)
        // Sora's native scale detector handles two-finger pinches without
        // competing with selection, caret dragging, or the IME; it zooms
        // within the sizes the Font Size setting offers.
        editor.setScalable(true)
        editor.setScaleTextSizes(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SettingLimits.fontSizesSp.first.toFloat(), context.resources.displayMetrics),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, SettingLimits.fontSizesSp.last.toFloat(), context.resources.displayMetrics),
        )
        editor.setWordwrap(true)
        editor.setTabWidth(preferences.effectiveIndentSize)
        editor.setEditorLanguage(plainTextLanguage)
        applyPalette(initialPalette)
        editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) semantic.edited()
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT && !applyingOutside) {
                val text = editor.text.toString()
                pendingLocalText = text
                onTextChanged(text)
            }
        }
        editor.subscribeEvent(ClickEvent::class.java) { event, _ ->
            val tapped = onChangeTapped ?: return@subscribeEvent
            if (event.motionRegion !in GUTTER) return@subscribeEvent
            val index = LineDiff.changeAt(editor.changes, event.line) ?: return@subscribeEvent
            // The sheet opens instead of the caret moving and the keyboard rising.
            event.intercept()
            tapped(index)
        }
    }

    fun bind(document: OpenDocument, onChange: (String) -> Unit) {
        onTextChanged = onChange
        separateLinesAs(document.lineEnding)
        if (location != document.location) {
            location = document.location
            acceptedText = document.text
            pendingLocalText = null
            editor.setText(document.text)
            semantic.open(document.location)
            syntax.apply(document)
        } else {
            // The editor's own typing come back, or nothing new: its text is not copied at every keystroke.
            val caughtUp = document.text === pendingLocalText || (pendingLocalText == null && document.text === acceptedText)
            val viewText = if (caughtUp) document.text else editor.text.toString()
            when {
                document.text == viewText -> {
                    acceptedText = document.text
                    pendingLocalText = null
                }
                pendingLocalText != null -> acceptedText = document.text
                document.text != acceptedText -> {
                    acceptedText = document.text
                    applyOutside(viewText, document.text)
                    syntax.apply(document)
                }
            }
        }
        editor.isEditable = !document.readOnly
        pendingReveal?.let { (lineNumber, column) ->
            val line = (lineNumber - 1).coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
            val clampedColumn = column.coerceIn(0, editor.text.getColumnCount(line))
            editor.setSelection(line, clampedColumn)
            editor.post { editor.ensurePositionVisible(line, clampedColumn, true) }
            pendingReveal = null
        }
    }

    /**
     * Text from outside the editor, as the smallest edit between what it shows and [text]: the
     * caret and what is around it stay, and Undo brings the old text back (docs/editor-and-language-services.md).
     */
    private fun applyOutside(viewText: String, text: String) {
        val edit = EditorTextRules.splice(viewText, text) ?: return
        applyingOutside = true
        try {
            editor.text.replace(edit.start, edit.end, edit.replacement)
        } finally {
            applyingOutside = false
        }
    }

    /** New lines — Enter, typed, pasted — take the file's line break, as it is saved with it. */
    private fun separateLinesAs(ending: LineEnding) {
        editor.separator = EditorTextRules.separatorOf(ending)
        editor.lineSeparator = when (ending) {
            LineEnding.CrLf -> LineSeparator.CRLF
            LineEnding.Cr -> LineSeparator.CR
            LineEnding.Lf, LineEnding.None -> LineSeparator.LF
        }
    }

    /** [change] undone in the editor — an edit, so Undo brings it back; false when the text no longer holds it. */
    fun revert(change: GitDiffHunk, base: String): Boolean {
        val viewText = editor.text.toString()
        val reverted = LineDiff.revert(viewText, base, change) ?: return false
        EditorTextRules.splice(viewText, reverted)?.let { editor.text.replace(it.start, it.end, it.replacement) }
        editor.notifyIMEExternalCursorChange()
        return true
    }

    /** A key of the keys row (docs/editor-and-language-services.md), as from a keyboard; over the completion list, Tab takes its row and ↑ ↓ choose (docs/editor-and-language-services.md). */
    fun press(key: EditorKey) {
        if (!editor.isEditable) return
        when (key) {
            EditorKey.Tab -> if (!editor.completions.acceptChosen()) editor.indentOrCommitTab()
            EditorKey.Outdent -> editor.unindentSelection()
            EditorKey.Left -> editor.moveSelection(SelectionMovement.LEFT)
            EditorKey.Up -> if (!editor.completions.moveChoice(up = true)) editor.moveSelection(SelectionMovement.UP)
            EditorKey.Down -> if (!editor.completions.moveChoice(up = false)) editor.moveSelection(SelectionMovement.DOWN)
            EditorKey.Right -> editor.moveSelection(SelectionMovement.RIGHT)
            // Sora's enhanced Home and End: the first non-blank character, then column 0; the end of the text, then the line's.
            EditorKey.Home -> editor.moveSelection(SelectionMovement.LINE_START)
            EditorKey.End -> editor.moveSelection(SelectionMovement.LINE_END)
            EditorKey.Undo -> return editor.undo()
            EditorKey.Redo -> return editor.redo()
            else -> editor.commitText(checkNotNull(key.text))
        }
        editor.notifyIMEExternalCursorChange()
    }

    /** The caret's line, counted from 0: where the changes count starts. */
    val caretLine: Int get() = editor.cursor.leftLine

    /** Apply a search-result location after the document has been bound to this view. */
    fun revealAt(lineNumber: Int, column: Int) {
        pendingReveal = lineNumber to column
        revealVersion += 1
    }

    /** Updates indentation in place; no document text or editor history is replaced. */
    fun applyPreferences(next: WorkspacePreferences) {
        val previous = appliedEditorSettings
        val settings = next.editor
        if (previous?.fontSizeSp != settings.fontSizeSp) editor.setTextSize(settings.fontSizeSp.toFloat())
        if (previous?.fontFamily != settings.fontFamily) {
            editor.typefaceText = when (settings.fontFamily) {
                EditorFontFamily.PlexMono -> plexTypeface
                EditorFontFamily.SystemMono -> Typeface.MONOSPACE
            }
        }
        if (previous?.wordWrap != settings.wordWrap) editor.setWordwrap(settings.wordWrap)
        if (previous?.lineNumbers != settings.lineNumbers) editor.setLineNumberEnabled(settings.lineNumbers)
        if (previous?.bracketMatching != settings.bracketMatching) {
            editor.setHighlightBracketPair(settings.bracketMatching)
        }
        if (previous?.minimap != settings.minimap) {
            editor.props.showMinimap = settings.minimap
            editor.requestLayout()
            editor.invalidate()
        }
        if (previous?.keyboardSuggestions != settings.keyboardSuggestions) {
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                if (settings.keyboardSuggestions) 0 else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            // An open keyboard reads the input type only when it starts again.
            if (previous != null) editor.restartInput()
        }
        editor.props.symbolPairAutoCompletion = settings.autoCloseBrackets
        semantic.enabled = settings.semanticHighlighting
        appliedEditorSettings = settings
        preferences = next
        editor.setTabWidth(next.effectiveIndentSize)
        plainTextLanguage.tabs = next.effectiveIndentType == IndentType.Tabs
        syntax.textMate?.let(::indent)
    }

    /** A grammar's indentation, as the settings have it. */
    private fun indent(language: TextMateLanguage) {
        language.setTabSize(preferences.effectiveIndentSize)
        language.useTab(preferences.effectiveIndentType == IndentType.Tabs)
    }

    /** Recolor the native view in place; never replace its text or history. */
    fun applyPalette(next: IcodePalette) {
        if (appliedPalette == next) return
        editor.markerColors = MarkerColors(next.green.toArgb(), next.blue.toArgb(), next.error.toArgb())
        val failure = runCatching {
            SyntaxTheme.use(next)
            editor.colorScheme = PaletteColorScheme(next, editorColors(next))
            appliedPalette = next
        }.exceptionOrNull()
        failure?.let { Log.e("iCodeEditor", "TextMate theme setup failed", it) }
        syntax.themeFailed(failure)
    }

    /** A language server started, stopped or changed what it offers: the file's semantic tokens are asked again. */
    fun languageServersChanged() = semantic.refresh()

    fun release() {
        semantic.release()
        scope.cancel()
        syntax.release()
        editor.release()
    }
}

private class ConfigurablePlainTextLanguage : EmptyLanguage() {
    var tabs: Boolean = false
    override fun useTab(): Boolean = tabs
}

/** Where Sora reports a tap in the gutter: the line numbers, the marker lane beside them, the divider. */
private val GUTTER = setOf(EditorMotionEvent.REGION_LINE_NUMBER, EditorMotionEvent.REGION_DIVIDER_MARGIN, EditorMotionEvent.REGION_DIVIDER)
