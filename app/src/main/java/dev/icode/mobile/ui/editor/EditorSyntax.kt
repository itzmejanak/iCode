package dev.icode.mobile.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.icode.mobile.domain.FileTypes
import dev.icode.mobile.domain.OpenDocument
import dev.icode.mobile.ui.syntax.SyntaxEngine
import dev.icode.mobile.ui.syntax.SyntaxLimits
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

/**
 * How one editor's document is coloured (docs/syntax-highlighting-plan.md D4, D7): its language by the
 * file's name or `#!` line, its grammar from [SyntaxEngine], and plain text, with a one-line [note],
 * when it cannot be coloured. Main thread.
 */
internal class EditorSyntax(
    private val editor: IcodeCodeEditor,
    private val plain: Language,
    /** Sets a new grammar's indentation from the settings. */
    private val indent: (TextMateLanguage) -> Unit,
) {
    /** Why the document is not coloured, said under the editor; null while it is, or while it simply has no grammar. */
    var note: String? by mutableStateOf(null)
        private set

    /** The document's grammar while it has one. */
    var textMate: TextMateLanguage? = null
        private set

    private var language: String? = null
    private var themeNote: String? = null
    private var released = false

    /** The palette's theme could not be made ([error]), so nothing is coloured; null once it is made again. */
    fun themeFailed(error: Throwable?) {
        themeNote = error?.let { "Colouring is off: ${it.message ?: it.javaClass.simpleName}" }
        note = themeNote
    }

    /**
     * [document]'s colouring. The text shows at once, plain; its colours follow when its grammar has
     * loaded. A file over the file limit ([SyntaxLimits.colours]) or whose grammar failed stays plain.
     */
    fun apply(document: OpenDocument) {
        val wanted = FileTypes.of(document.name, document.text.take(FIRST_LINE_CHARS).substringBefore('\n')).language
        val large = wanted != null && !SyntaxLimits.colours(document.text)
        val next = wanted.takeUnless { large || themeNote != null }
        note = themeNote ?: if (large) TOO_LARGE else next?.takeIf { SyntaxEngine.failure(it) != null }?.let(::failed)
        if (next == language) return
        language = next
        usePlain()
        if (next == null) return
        SyntaxEngine.language(editor.context, next) { result ->
            // Another file, or none, since the request: its own request applies instead.
            if (language != next || released) {
                result.getOrNull()?.destroy()
                return@language
            }
            result.onSuccess { grammar ->
                indent(grammar)
                editor.setEditorLanguage(FileLineBreaksLanguage(grammar) { editor.separator })
                textMate = grammar
            }.onFailure { note = failed(next) }
        }
    }

    fun release() {
        released = true
    }

    private fun usePlain() {
        textMate = null
        editor.setEditorLanguage(plain)
    }

    private fun failed(language: String) = "Colouring is off for $language: its grammar could not be loaded"

    private companion object {
        /** A `#!` line longer than this is not one a script would start with. */
        const val FIRST_LINE_CHARS = 256
        const val TOO_LARGE = "Colouring is off for files this large"
    }
}
