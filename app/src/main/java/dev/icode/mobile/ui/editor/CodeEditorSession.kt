package dev.icode.mobile.ui.editor

import android.content.Context
import android.graphics.Typeface
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.R
import dev.icode.mobile.domain.EditorFontFamily
import dev.icode.mobile.domain.EditorSettings
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.IndentType
import dev.icode.mobile.domain.WorkspacePreferences
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/** One view/content/undo history shared by the Editor and Find routes. */
class CodeEditorSession(context: Context, initialPalette: IcodePalette = IcodePalette.Dark) {
    val editor = CodeEditor(context)
    var coloringError: String? = null
        private set

    private var location: String? = null
    private var scope: String? = null
    private var acceptedText: String = ""
    private var pendingLocalText: String? = null
    private var pendingReveal: Pair<Int, Int>? = null
    private var onTextChanged: (String) -> Unit = {}
    private val plainTextLanguage = ConfigurablePlainTextLanguage()
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
        // competing with selection, caret dragging, or the IME.
        editor.setScalable(true)
        editor.setScaleTextSizes(
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9f, context.resources.displayMetrics),
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 28f, context.resources.displayMetrics),
        )
        editor.setWordwrap(true)
        editor.setTabWidth(preferences.effectiveIndentSize)
        editor.setEditorLanguage(plainTextLanguage)
        applyPalette(initialPalette)
        editor.subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                val text = editor.text.toString()
                pendingLocalText = text
                onTextChanged(text)
            }
        }
    }

    fun bind(document: OpenDocument, onChange: (String) -> Unit) {
        onTextChanged = onChange
        if (location != document.location) {
            location = document.location
            acceptedText = document.text
            pendingLocalText = null
            editor.setText(document.text)
            applyLanguage(document)
        } else {
            val viewText = editor.text.toString()
            when {
                document.text == viewText -> {
                    acceptedText = document.text
                    pendingLocalText = null
                }
                pendingLocalText != null -> acceptedText = document.text
                document.text != acceptedText -> {
                    acceptedText = document.text
                    editor.setText(document.text)
                    applyLanguage(document)
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
        appliedEditorSettings = settings
        preferences = next
        editor.setTabWidth(next.effectiveIndentSize)
        plainTextLanguage.tabs = next.effectiveIndentType == IndentType.Tabs
        (editor.editorLanguage as? TextMateLanguage)?.apply {
            setTabSize(next.effectiveIndentSize)
            useTab(next.effectiveIndentType == IndentType.Tabs)
        }
    }

    /** Recolor the native view in place; never replace its text or history. */
    fun applyPalette(next: IcodePalette) {
        if (appliedPalette == next) return
        coloringError = runCatching {
            TextMateAssets.setTheme(editor.context.applicationContext, next)
            editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance()).apply {
                setColor(EditorColorScheme.WHOLE_BACKGROUND, next.backgroundDeep.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, next.backgroundDeep.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER, next.textMuted.toArgb())
                setColor(EditorColorScheme.LINE_NUMBER_CURRENT, next.accent.toArgb())
                setColor(EditorColorScheme.TEXT_NORMAL, next.textPrimary.toArgb())
                setColor(EditorColorScheme.CURRENT_LINE, next.surface.toArgb())
                setColor(EditorColorScheme.SELECTION_INSERT, next.accent.toArgb())
                setColor(EditorColorScheme.SELECTION_HANDLE, next.accent.toArgb())
                setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, next.accent.copy(alpha = 0.28f).toArgb())
                setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, next.accent.toArgb())
                setColor(EditorColorScheme.PROBLEM_ERROR, next.error.toArgb())
                setColor(EditorColorScheme.PROBLEM_WARNING, next.amber.toArgb())
                setColor(EditorColorScheme.MINIMAP_BACKGROUND, next.surfaceElevated.toArgb())
            }
            appliedPalette = next
            null
        }.getOrElse { error ->
            Log.e("iCodeEditor", "TextMate theme setup failed", error)
            "Syntax coloring is unavailable: ${error.message ?: error.javaClass.simpleName}"
        }
    }

    /** By the file's name, or for a script without an extension, its `#!` line (read up to its end, at most 256 characters). */
    private fun applyLanguage(document: OpenDocument) {
        val name = document.name
        val nextScope = FileTypes.of(name, document.text.substringBefore('\n').take(FIRST_LINE_CHARS)).grammar
        if (nextScope == scope) return
        scope = nextScope
        runCatching {
            editor.setEditorLanguage(if (nextScope == null || coloringError != null) {
                plainTextLanguage
            } else {
                TextMateLanguage.create(nextScope, false).apply {
                    setTabSize(preferences.effectiveIndentSize)
                    useTab(preferences.effectiveIndentType == IndentType.Tabs)
                }
            })
        }.onFailure { error ->
            Log.e("iCodeEditor", "Grammar load failed for $name", error)
            coloringError = "Syntax coloring is unavailable for $name"
            editor.setEditorLanguage(plainTextLanguage)
        }
    }

    fun release() = editor.release()
}

private class ConfigurablePlainTextLanguage : EmptyLanguage() {
    var tabs: Boolean = false
    override fun useTab(): Boolean = tabs
}

/** Per-document Sora views preserve each tab's undo, caret, scroll and fold state. */
class CodeEditorSessions(private val context: Context) {
    private val sessions = mutableMapOf<String, CodeEditorSession>()
    private var preferences = WorkspacePreferences()
    private var palette = IcodePalette.Dark

    fun get(location: String): CodeEditorSession =
        sessions.getOrPut(location) {
            CodeEditorSession(context, palette).also {
                it.applyPreferences(preferences)
                it.applyPalette(palette)
            }
        }

    fun applyPreferences(next: WorkspacePreferences) {
        preferences = next
        sessions.values.forEach { it.applyPreferences(next) }
    }

    fun applyPalette(next: IcodePalette) {
        palette = next
        sessions.values.forEach { it.applyPalette(next) }
    }

    fun releaseClosed(openLocations: Set<String>) {
        val closed = sessions.keys.filterNot { it in openLocations }
        closed.forEach { location -> sessions.remove(location)?.release() }
    }

    fun releaseAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
    }
}

@Composable
fun EditorCanvas(
    session: CodeEditorSession,
    document: OpenDocument,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // AndroidView's factory is not rerun when its parameters change. Keying
    // the host to the session prevents one tab's native View being shown while
    // a different session receives the new document in update.
    key(session) {
        AndroidView(
            factory = {
                // Navigation swaps Compose hosts but retains this editor and its undo history.
                (session.editor.parent as? ViewGroup)?.removeView(session.editor)
                session.editor
            },
            update = { view ->
                check(view === session.editor) { "Editor View and document session diverged" }
                session.bind(document, onTextChanged)
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** A `#!` line longer than this is not one a script would start with. */
private const val FIRST_LINE_CHARS = 256
