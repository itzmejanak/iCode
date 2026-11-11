package dev.icode.mobile.ui.editor

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.domain.Diagnostic
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.LanguageCompletions
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.domain.WorkspacePreferences

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
    /** The file's Git changes, marked in the gutter. */
    changes: List<GitDiffHunk> = emptyList(),
    /** A tap on a marked line: which change; null leaves the gutter as it is. */
    onChangeTapped: ((Int) -> Unit)? = null,
    /** The file's problems, drawn under their text; the same list again draws nothing new. */
    problems: List<Diagnostic> = emptyList(),
    /** The language servers' completions for it. */
    completions: LanguageCompletions = LanguageCompletions.None,
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
                session.editor.changes = changes
                session.onChangeTapped = onChangeTapped
                session.editor.problems = problems
                val completer = session.editor.completer
                if (completer?.location != document.location || completer.completions !== completions) {
                    session.editor.completer = EditorCompleter(document.location, completions).takeIf { completions !== LanguageCompletions.None }
                }
            },
            modifier = modifier.fillMaxSize(),
        )
    }
}
