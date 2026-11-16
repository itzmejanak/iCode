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
 * [inner] with the language servers' completions (docs/editor-and-language-services.md): asked on Sora's
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

/**
 * One server item in Sora's list, its row drawn by [EditorCompletionWindow] from [item] and the
 * letters that [matched]; chosen, it applies the server's edits and its snippet.
 */
internal class LanguageCompletion(
    ranked: RankedCompletion,
    prefixLength: Int,
    private val completer: EditorCompleter,
) : CompletionItem(ranked.item.label, ranked.item.detail) {
    val item: LanguageCompletionItem = ranked.item
    val matched: IntArray = ranked.matched

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

/** One of the completions shown: the server's item, and which of its label's letters matched what was typed. */
internal class RankedCompletion(val item: LanguageCompletionItem, val matched: IntArray)

/**
 * Which completions show for what was typed, in what order (docs/editor-and-language-services.md), as VS
 * Code's suggest list: the typed letters in order, the first at the start of the word or of a word
 * inside it — `aw` is `await` and `AudioWorklet`, never `DataView` — the best match first.
 */
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

    /**
     * [items] that match [prefix] ([CompletionMatcher]), the best first, then by the server's
     * `sortText`, then the shorter; one item two servers both gave, once.
     */
    fun matching(items: List<LanguageCompletionItem>, prefix: String): List<RankedCompletion> {
        val matcher = CompletionMatcher()
        return items.asSequence()
            .distinctBy { Triple(it.label, it.kind, it.insertText) }
            .mapNotNull { item ->
                val filter = item.filterText ?: item.label
                val score = matcher.score(prefix, filter) ?: return@mapNotNull null
                // Its letters are drawn on the label: matched there, the filter text being another word.
                val matched = if (filter == item.label) matcher.positions() else matcher.match(prefix, item.label)?.positions ?: IntArray(0)
                Triple(item, score, matched)
            }
            .sortedWith(compareByDescending<Triple<LanguageCompletionItem, Int, IntArray>> { it.second }
                .thenBy { it.first.sortText ?: it.first.label }
                .thenBy { it.first.label.length }
                .thenBy { it.first.label })
            .take(MAX_SHOWN)
            .map { (item, _, matched) -> RankedCompletion(item, matched) }
            .toList()
    }
}

/** How well a word matches what was typed, and where. */
internal class CompletionMatch(val score: Int, val positions: IntArray)

/**
 * VS Code's completion matching (`fuzzyScore`, without a weak first match): [pattern]'s letters in
 * [word] in order, case aside, the first at a word start — the word's own, a camel hump (`Worklet` in
 * `AudioWorklet`, `Element` in `HTMLElement`), or after `_`, `$`, `-` or `.`. Of every way to place
 * them the best scores: each letter one, the same case one more, a word start [WORD_START] (the word's
 * first [FIRST]), each letter right after the one before [RUN], a gap [GAP] less; the whole word typed,
 * [WHOLE] more. One matcher keeps its tables between words: one list's thousands of items allocate none.
 */
internal class CompletionMatcher {
    private var score = IntArray(0)
    private var from = IntArray(0)
    private var word = ""
    private var size = 0
    private var best = -1

    /** [word]'s match of [pattern], with the positions of its letters; null for none. */
    fun match(pattern: String, word: String): CompletionMatch? = score(pattern, word)?.let { CompletionMatch(it, positions()) }

    /** [word]'s score for [pattern]; null when it does not match. [positions] are the last match's. */
    fun score(pattern: String, word: String): Int? {
        this.word = word
        size = pattern.length
        best = -1
        if (pattern.isEmpty()) return 0
        val n = pattern.length
        val m = word.length
        if (n > m || n > MAX_PATTERN) return null
        if (score.size < n * m) {
            score = IntArray(n * m)
            from = IntArray(n * m)
        }
        score.fill(NONE, 0, n * m)
        for (j in 0..m - n) {
            if (same(pattern[0], word[j]) && start(word, j)) score[j] = letter(pattern[0], word, j)
        }
        for (i in 1 until n) {
            var gapBest = NONE
            var gapAt = -1
            for (j in i until m - (n - 1 - i)) {
                // Every place two or more before this one, for a gap.
                val k = j - 2
                if (k >= i - 1 && score[(i - 1) * m + k] > gapBest) {
                    gapBest = score[(i - 1) * m + k]
                    gapAt = k
                }
                if (!same(pattern[i], word[j])) continue
                val run = score[(i - 1) * m + j - 1].takeIf { it != NONE }?.plus(RUN)
                val gap = gapBest.takeIf { it != NONE }?.minus(GAP)
                val previous = maxOf(run ?: NONE, gap ?: NONE).takeIf { it != NONE } ?: continue
                score[i * m + j] = previous + letter(pattern[i], word, j)
                from[i * m + j] = if (run != null && run >= (gap ?: NONE)) j - 1 else gapAt
            }
        }
        val last = (n - 1) * m
        var top = NONE
        for (j in n - 1 until m) if (score[last + j] > top) {
            top = score[last + j]
            best = j
        }
        if (top == NONE) return null
        return top + if (n == m) WHOLE else 0
    }

    /** Where the last [score]'s letters matched in its word, from the first. */
    fun positions(): IntArray {
        if (best < 0 || size == 0) return IntArray(0)
        val m = word.length
        val positions = IntArray(size)
        var j = best
        for (i in size - 1 downTo 0) {
            positions[i] = j
            if (i > 0) j = from[i * m + j]
        }
        return positions
    }

    private fun letter(typed: Char, word: String, at: Int): Int =
        1 + (if (typed == word[at]) 1 else 0) + when {
            at == 0 -> FIRST
            start(word, at) -> WORD_START
            else -> 0
        }

    private fun same(a: Char, b: Char) = a == b || a.lowercaseChar() == b.lowercaseChar()

    private fun start(word: String, at: Int): Boolean {
        if (at == 0) return true
        val before = word[at - 1]
        val here = word[at]
        return before in SEPARATORS ||
            here.isUpperCase() && before.isLowerCase() ||
            here.isUpperCase() && before.isUpperCase() && word.getOrNull(at + 1)?.isLowerCase() == true ||
            here.isLetterOrDigit() && !before.isLetterOrDigit()
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
        const val FIRST = 8
        const val WORD_START = 6
        const val RUN = 5
        const val GAP = 1
        const val WHOLE = 4
        const val MAX_PATTERN = 64
        const val SEPARATORS = "_\$-./"
    }
}
