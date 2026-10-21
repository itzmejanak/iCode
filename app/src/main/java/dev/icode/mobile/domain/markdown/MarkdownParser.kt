package dev.icode.mobile.domain.markdown

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.intellij.markdown.parser.StreamingMarkdownFile
import org.intellij.markdown.parser.MarkdownParser as JetBrainsParser

/**
 * Markdown read as GitHub reads it (docs/markdown-rendering-plan.md §3.1), by the JetBrains parser in
 * its GFM flavour: math, tables, task lists, strikethrough, alerts and autolinks.
 */
object MarkdownParser {
    /**
     * A file, whole: a leading `---` block of YAML is its front matter. [checkCancelled] throws to stop
     * a parse whose text has changed since.
     */
    fun file(text: String, checkCancelled: () -> Unit = {}): MarkdownDocument {
        val front = FRONT_MATTER.find(text)
        val body: CharSequence = front?.let { text.substring(it.range.last + 1) } ?: text
        val tree = JetBrainsParser(GFMFlavourDescriptor(), cancellationToken = CancellationToken(checkCancelled)).buildMarkdownTreeFromString(body)
        val reader = MarkdownBlockReader(body)
        val pieces = front?.let { listOf(MarkdownPiece.Block(MarkdownBlock.FrontMatter(it.groupValues[1]))) }.orEmpty() + tree.children.flatMap(reader::pieces)
        return MarkdownAssembly.document(MarkdownAssembly.blocks(pieces), reader.references, reader.footnotes)
    }

    /** The front matter's text, between `---` and `---` (or `...`), at a file's very start. */
    private val FRONT_MATTER = Regex("\\A---[ \\t]*\\r?\\n([\\s\\S]*?)\\r?\\n(?:---|\\.\\.\\.)[ \\t]*(?:\\r?\\n|\\z)")
}

/**
 * An assistant's answer as it streams (§3.6). The parser's streaming file re-reads only the end it has
 * not settled; what it settled is converted once and kept, so each chunk costs the end, not the
 * whole. `\(…\)` and `\[…\]` are math ([MathDelimiters]). Not for two threads at once.
 */
class MarkdownStream {
    private var fed = ""
    private var whole = false
    private var delimiters = MathDelimiters()
    private var file: StreamingMarkdownFile = EmptyStreamingMarkdownFile(GFMFlavourDescriptor())
    private val source = StringBuilder()
    private var settledReader = MarkdownBlockReader(source)
    private val settled = mutableListOf<MarkdownPiece>()
    private var settledNodes = 0
    private var document = MarkdownDocument.Empty

    /** The document of [text], the answer so far; [complete] once nothing more will come. */
    fun update(text: String, complete: Boolean): MarkdownDocument {
        if (text == fed && (whole || !complete)) return document
        // Not a continuation of what was read (a retried answer), or more after the end: read anew.
        if (!text.startsWith(fed) || whole) reset()
        val added = delimiters.feed(text.substring(fed.length)) + if (complete) delimiters.finish() else ""
        fed = text
        whole = complete
        if (added.isNotEmpty()) {
            source.append(added)
            file.append(added)
        }
        file.stableChildren.drop(settledNodes).forEach { settled += settledReader.pieces(it) }
        settledNodes = file.stableChildren.size
        val tail = MarkdownBlockReader(source)
        val pieces = settled + file.unstableTail.flatMap(tail::pieces)
        document = MarkdownAssembly.document(
            MarkdownAssembly.blocks(pieces),
            // The first definition of a label holds, as in a whole parse: the settled part's come first.
            tail.references + settledReader.references,
            tail.footnotes + settledReader.footnotes,
        )
        return document
    }

    private fun reset() {
        fed = ""
        whole = false
        delimiters = MathDelimiters()
        file = EmptyStreamingMarkdownFile(GFMFlavourDescriptor())
        source.clear()
        settledReader = MarkdownBlockReader(source)
        settled.clear()
        settledNodes = 0
        document = MarkdownDocument.Empty
    }
}
