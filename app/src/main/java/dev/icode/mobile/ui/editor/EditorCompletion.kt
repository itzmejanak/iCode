package dev.icode.mobile.ui.editor

import android.os.Bundle
import dev.icode.mobile.domain.LanguageCompletionItem
import dev.icode.mobile.domain.LanguageCompletions
import dev.icode.mobile.domain.LanguageTextEdit
import dev.icode.mobile.domain.TextPosition
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.lang.completion.CompletionCancelledException
import io.github.rosemoe.sora.lang.completion.CompletionItem
import io.github.rosemoe.sora.lang.completion.CompletionItemKind
import io.github.rosemoe.sora.lang.completion.CompletionPublisher
import io.github.rosemoe.sora.lang.completion.snippet.parser.CodeSnippetParser
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ContentReference
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/** Where the editor's completions come from: the language servers serving the file at [location]. */
internal class EditorCompleter(val location: String, val completions: LanguageCompletions)

/**
 * [inner] with the language servers' completions (docs/lsp-plan.md §7, L7): asked on Sora's
 * completion thread, the asking one interrupted by the next keystroke (a strong interruption), and
 * filtered to what was typed. Without a [IcodeCodeEditor.completer] it is [inner] as it was.
 */
internal class CompletingLanguage(private val inner: Language, private val editor: IcodeCodeEditor) : Language by inner {
    override fun getInterruptionLevel(): Int = if (editor.completer != null) Language.INTERRUPTION_LEVEL_STRONG else inner.interruptionLevel

    override fun requireAutoComplete(content: ContentReference, position: CharPosition, publisher: CompletionPublisher, extraArguments: Bundle) {
        val completer = editor.completer ?: return inner.requireAutoComplete(content, position, publisher, extraArguments)
        val line = content.getLine(position.line)
        val prefix = EditorCompletionRules.prefixOf(line, position.column)
        val items = try {
            runBlocking { withTimeoutOrNull(WAIT_MILLIS) { completer.completions.complete(completer.location, TextPosition(position.line, position.column)) } }
        } catch (interrupted: InterruptedException) {
            throw CompletionCancelledException()
        } ?: return
        publisher.checkCancelled()
        publisher.addItems(EditorCompletionRules.matching(items, prefix).map { LanguageCompletion(it, prefix.length, completer) })
    }

    private companion object {
        /** A little past the request's own 3 s, which ends it first. */
        const val WAIT_MILLIS = 3_500L
    }
}

/** One server item in Sora's list; chosen, it applies the server's edits and its snippet. */
private class LanguageCompletion(
    private val item: LanguageCompletionItem,
    prefixLength: Int,
    private val completer: EditorCompleter,
) : CompletionItem(item.label, item.detail) {
    init {
        this.prefixLength = prefixLength
        kind = item.kind?.let { name -> CompletionItemKind.entries.firstOrNull { it.name == name } } ?: CompletionItemKind.Text
        sortText = item.sortText
        filterText = item.filterText
        deprecated = item.deprecated
    }

    override fun performCompletion(editor: CodeEditor, text: Content, position: CharPosition) {
        val caret = editor.cursor.left().index
        val start = item.edit?.let { text.indexOf(it.range.start) } ?: (caret - prefixLength).coerceAtLeast(0)
        // The server's range ends where the caret was asked about; what was typed since goes too.
        val end = maxOf(item.edit?.let { text.indexOf(it.range.end) } ?: caret, caret)
        val shift = applyElsewhere(text, item.additionalEdits, start, end)
        val at = start + shift
        val insert = item.edit?.newText ?: item.insertText
        val snippet = if (item.snippet) runCatching { CodeSnippetParser.parse(insert) }.getOrNull() else null
        if (snippet != null) {
            text.delete(at, end + shift)
            editor.snippetController.startSnippet(at, snippet)
        } else {
            text.replace(at, end + shift, if (item.snippet) EditorCompletionRules.plain(insert) else insert)
        }
        if (item.resolvable && item.additionalEdits.isEmpty()) resolveImports(editor, text, at)
    }

    /**
     * The server fills in an item's imports only when asked (`completionItem/resolve`): asked off the
     * main thread once it is inserted, and applied only if nothing changed since and they are above it.
     */
    private fun resolveImports(editor: CodeEditor, text: Content, insertedAt: Int) {
        val version = text.documentVersion
        Thread {
            val resolved = runCatching { runBlocking { withTimeoutOrNull(RESOLVE_MILLIS) { completer.completions.resolve(completer.location, item) } } }.getOrNull()
            val imports = resolved?.additionalEdits.orEmpty()
            if (imports.isEmpty()) return@Thread
            editor.post {
                if (text.documentVersion != version) return@post
                text.beginBatchEdit()
                try {
                    applyElsewhere(text, imports.filter { text.indexOf(it.range.end) <= insertedAt }, insertedAt, insertedAt)
                } finally {
                    text.endBatchEdit()
                }
            }
        }.apply { isDaemon = true }.start()
    }

    /** [edits] outside start–end, from the end back so each one's places hold; returns how far those before moved the insertion. */
    private fun applyElsewhere(text: Content, edits: List<LanguageTextEdit>, start: Int, end: Int): Int {
        var shift = 0
        edits.map { text.indexOf(it.range.start) to (text.indexOf(it.range.end) to it.newText) }
            .filter { (from, rest) -> rest.first <= start || from >= end }
            .sortedByDescending { it.first }
            .forEach { (from, rest) ->
                text.replace(from, rest.first, rest.second)
                if (rest.first <= start) shift += rest.second.length - (rest.first - from)
            }
        return shift
    }

    override fun performCompletion(editor: CodeEditor, text: Content, line: Int, column: Int) = Unit

    private fun Content.indexOf(position: TextPosition): Int {
        val line = position.line.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
        return getCharIndex(line, position.column.coerceIn(0, getColumnCount(line)))
    }

    private companion object {
        const val RESOLVE_MILLIS = 1_500L
    }
}

/** Which completions show for what was typed, in what order. */
internal object EditorCompletionRules {
    const val MAX_SHOWN = 200

    /** A snippet's text without its tab stops and placeholders, for one Sora cannot read: `${1:x}` is `x`, `$0` nothing. */
    fun plain(snippet: String): String =
        snippet.replace(Regex("\\$\\{\\d+:([^}]*)}"), "$1").replace(Regex("\\$\\{\\d+}|\\$\\d+"), "")

    /** The identifier being typed before [column]: what the list filters by. */
    fun prefixOf(line: CharSequence, column: Int): String {
        var start = column.coerceAtMost(line.length)
        while (start > 0 && (line[start - 1].isLetterOrDigit() || line[start - 1] == '_' || line[start - 1] == '$')) start--
        return line.subSequence(start, column.coerceAtMost(line.length)).toString()
    }

    /** [items] whose filter text holds [prefix]'s letters in order, case aside; the server's order, then a closer match first. */
    fun matching(items: List<LanguageCompletionItem>, prefix: String): List<LanguageCompletionItem> {
        val wanted = prefix.lowercase()
        return items.filter { subsequence(wanted, (it.filterText ?: it.label).lowercase()) }
            .sortedWith(compareBy<LanguageCompletionItem>({ !(it.filterText ?: it.label).startsWith(prefix, ignoreCase = true) }, { it.sortText ?: it.label }))
            .take(MAX_SHOWN)
    }

    private fun subsequence(needle: String, hay: String): Boolean {
        var at = 0
        for (char in hay) if (at < needle.length && needle[at] == char) at++
        return at == needle.length
    }
}
