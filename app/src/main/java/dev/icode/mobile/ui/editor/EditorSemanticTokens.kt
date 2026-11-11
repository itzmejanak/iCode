package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.LanguageHighlights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * When one editor asks the language servers for its file's semantic tokens (docs/syntax-highlighting-plan.md
 * D8): when the file opens and its grammar has loaded, when a server starts or changes what it offers,
 * and [QUIET_MILLIS] after typing stops. A new question cancels the one before it. Nothing is asked
 * with the setting off, with no [source], or while the file has no grammar's colours (none, or past
 * the limits), and then the editor draws its grammar's colours alone. Main thread.
 */
internal class EditorSemanticTokens(
    private val scope: CoroutineScope,
    /** Whether the file is coloured by its grammar now. */
    private val coloured: () -> Boolean,
    /** The runs to draw over the grammar's colours; null for none. */
    private val show: (SemanticLines?) -> Unit,
    /** Where an answer is turned into runs: not the main thread. */
    private val background: CoroutineDispatcher = Dispatchers.Default,
    /** Each line's text in the editor now, for [SemanticAnswer.over]. */
    private val lineOf: (Int) -> String?,
) {
    var source: LanguageHighlights = LanguageHighlights.None
        set(value) {
            if (field === value) return
            field = value
            refresh()
        }

    /** The editor setting (Settings › Editor › Semantic Colouring). */
    var enabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            refresh()
        }

    private var location: String? = null
    private var job: Job? = null

    /** The editor shows the file at [location] now: the last file's runs go. */
    fun open(location: String) {
        if (this.location == location) return
        this.location = location
        clear()
        refresh()
    }

    /** The text was edited: asked again once typing stops. */
    fun edited() = ask(QUIET_MILLIS)

    /** Asked again at once, or the runs cleared when nothing may be asked now. */
    fun refresh() {
        if (!asks()) clear() else ask(0)
    }

    fun release() {
        job?.cancel()
        location = null
    }

    private fun asks(): Boolean = enabled && source !== LanguageHighlights.None && location != null && coloured()

    private fun ask(after: Long) {
        job?.cancel()
        if (!asks()) return
        val at = location ?: return
        val from = source
        job = scope.launch {
            delay(after)
            val answer = withContext(background) { from.semanticTokens(at)?.let { SemanticAnswer.of(it) } } ?: return@launch
            if (location == at && asks()) show(answer.over(lineOf))
        }
    }

    private fun clear() {
        job?.cancel()
        show(null)
    }

    companion object {
        /** After the last keystroke: past the documents' own quiet time, so the change has gone out. */
        const val QUIET_MILLIS = 500L
    }
}
