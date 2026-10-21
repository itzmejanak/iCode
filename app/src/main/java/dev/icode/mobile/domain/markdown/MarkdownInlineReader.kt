package dev.icode.mobile.domain.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.entities.Entities

/**
 * A line of Markdown — a paragraph's, a heading's, a cell's — from the parser's nodes to iCode's
 * inlines. Whitespace as a browser draws GitHub's HTML: a line end is a space, runs of spaces are one.
 * [table]: a cell's, where `\|` in a code span is a `|` (GFM).
 */
internal class MarkdownInlineReader(private val source: CharSequence, private val table: Boolean = false) {
    fun read(nodes: List<ASTNode>): List<MarkdownInline> {
        val bits = mutableListOf<MarkdownHtml.Bit>()
        add(nodes, bits)
        return tidied(MarkdownHtml.inlines(MarkdownHtml.tree(bits)), edges = true)
    }

    private fun add(nodes: List<ASTNode>, bits: MutableList<MarkdownHtml.Bit>) {
        // A line's indentation and quote markers, after a line end, are not text.
        var lineStart = false
        nodes.forEachIndexed { at, node ->
            val type = node.type
            if (lineStart && (type == MarkdownTokenTypes.WHITE_SPACE || type == MarkdownTokenTypes.BLOCK_QUOTE)) return@forEachIndexed
            lineStart = type == MarkdownTokenTypes.EOL
            // `<me@example.com>` comes as `<`, the address and `>`, side by side: one link.
            if (type == MarkdownTokenTypes.LT && nodes.getOrNull(at + 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK) return@forEachIndexed
            if (type == MarkdownTokenTypes.GT && nodes.getOrNull(at - 1)?.type == MarkdownTokenTypes.EMAIL_AUTOLINK) return@forEachIndexed
            val inline: MarkdownInline? = when (type) {
                MarkdownTokenTypes.EOL -> MarkdownInline.Text(" ")
                MarkdownTokenTypes.HARD_LINE_BREAK -> MarkdownInline.LineBreak
                MarkdownElementTypes.EMPH -> styled(MarkdownStyle.Emphasis, node)
                MarkdownElementTypes.STRONG -> styled(MarkdownStyle.Strong, node)
                GFMElementTypes.STRIKETHROUGH -> styled(MarkdownStyle.Strike, node)
                MarkdownElementTypes.CODE_SPAN -> MarkdownInline.Code(codeSpan(node))
                MarkdownElementTypes.INLINE_LINK, MarkdownElementTypes.FULL_REFERENCE_LINK -> link(node)
                MarkdownElementTypes.SHORT_REFERENCE_LINK -> shortReference(node)
                MarkdownElementTypes.IMAGE -> image(node)
                MarkdownElementTypes.AUTOLINK -> autolink(node)
                MarkdownTokenTypes.EMAIL_AUTOLINK -> text(node).let { MarkdownInline.Link(MarkdownDestination.Url("mailto:$it"), null, listOf(MarkdownInline.Text(it))) }
                GFMTokenTypes.GFM_AUTOLINK -> text(node).let { url ->
                    MarkdownInline.Link(MarkdownDestination.Url(if (url.startsWith("www.", ignoreCase = true)) "http://$url" else url), null, listOf(MarkdownInline.Text(url)))
                }
                GFMElementTypes.INLINE_MATH, GFMElementTypes.BLOCK_MATH -> MarkdownInline.Math(math(node, source))
                MarkdownTokenTypes.HTML_TAG -> null.also { bits += MarkdownHtml.bits(text(node)) }
                // `==x==` is not GitHub's: drawn as written.
                GFMElementTypes.HIGHLIGHT -> MarkdownInline.Text(text(node))
                else -> if (node.children.isEmpty()) MarkdownInline.Text(MarkdownCharacters.decode(text(node), escapes = true)) else null.also { add(node.children, bits) }
            }
            inline?.let { bits += MarkdownHtml.Bit.Content(it) }
        }
    }

    /** Emphasis, strong or strike: what is between its delimiter runs. */
    private fun styled(style: MarkdownStyle, node: ASTNode): MarkdownInline {
        val marks = node.children.takeWhile { it.type == MarkdownTokenTypes.EMPH || it.type == GFMTokenTypes.TILDE }.size
            .coerceAtMost(node.children.size / 2)
        return MarkdownInline.Styled(style, read(node.children.subList(marks, node.children.size - marks)))
    }

    /** CommonMark's code span: line ends are spaces; one space each side goes when both are there. */
    private fun codeSpan(node: ASTNode): String {
        val first = node.children.first()
        val last = node.children.last()
        var code = source.substring(first.endOffset, last.startOffset).replace('\n', ' ')
        if (code.length >= 2 && code.startsWith(' ') && code.endsWith(' ') && code.isNotBlank()) code = code.substring(1, code.length - 1)
        return if (table) code.replace("\\|", "|") else code
    }

    private fun link(node: ASTNode): MarkdownInline {
        val text = node.child(MarkdownElementTypes.LINK_TEXT)
        val children = text?.let { read(it.children.drop(1).dropLast(1)) }.orEmpty()
        return MarkdownInline.Link(destination(node, text), title(node), children)
    }

    /** `[label]`: a link if [label] is defined, a footnote reference for `[^label]`. */
    private fun shortReference(node: ASTNode): MarkdownInline {
        val label = node.child(MarkdownElementTypes.LINK_LABEL)
        val inner = label?.let(::inside).orEmpty()
        if (inner.startsWith("^")) return MarkdownInline.FootnoteReference(MarkdownLinks.label(inner.drop(1)), text(node))
        return MarkdownInline.Link(destination(node, null), null, label?.let { read(it.children.drop(1).dropLast(1)) }.orEmpty())
    }

    private fun image(node: ASTNode): MarkdownInline? {
        val link = node.children.firstOrNull { it.type != MarkdownTokenTypes.EXCLAMATION_MARK } ?: return null
        val text = link.child(MarkdownElementTypes.LINK_TEXT) ?: link.child(MarkdownElementTypes.LINK_LABEL)
        val alt = text?.let { read(it.children.drop(1).dropLast(1)).plain() }.orEmpty()
        return MarkdownInline.Image(destination(link, link.child(MarkdownElementTypes.LINK_TEXT), written = text(node)), alt, title(link))
    }

    /** `<https://…>`. */
    private fun autolink(node: ASTNode): MarkdownInline {
        val address = text(node).removeSurrounding("<", ">")
        return MarkdownInline.Link(MarkdownDestination.Url(address), null, listOf(MarkdownInline.Text(address)))
    }

    /** Written in place, or a reference: `[text][label]`, `[text][]`, `[label]`; [written] as it is, for an undefined one. */
    private fun destination(node: ASTNode, linkText: ASTNode?, written: String = text(node)): MarkdownDestination {
        node.child(MarkdownElementTypes.LINK_DESTINATION)?.let { destination ->
            val href = text(destination).let { if (it.startsWith("<") && it.endsWith(">")) it.substring(1, it.length - 1) else it }
            return MarkdownDestination.Url(MarkdownCharacters.decode(href, escapes = true))
        }
        val label = node.child(MarkdownElementTypes.LINK_LABEL)?.let(::inside).takeUnless { it.isNullOrBlank() } ?: linkText?.let(::inside).orEmpty()
        return MarkdownDestination.Reference(MarkdownLinks.label(label), written)
    }

    /** What is between [node]'s brackets. */
    private fun inside(node: ASTNode): String = source.substring(node.startOffset + 1, node.endOffset - 1)

    private fun title(node: ASTNode): String? =
        node.child(MarkdownElementTypes.LINK_TITLE)?.let { MarkdownCharacters.decode(text(it).drop(1).dropLast(1), escapes = true) }

    private fun text(node: ASTNode): String = source.substring(node.startOffset, node.endOffset)
}

/**
 * [items] as a browser draws them: adjacent text joined, runs of whitespace one space, none against a
 * line break; at the [edges] of a line (a paragraph's, a heading's), none at either end.
 */
internal fun tidied(items: List<MarkdownInline>, edges: Boolean): List<MarkdownInline> {
    val merged = mutableListOf<MarkdownInline>()
    items.forEach { item ->
        val inner = when (item) {
            is MarkdownInline.Styled -> item.copy(children = tidied(item.children, edges = false))
            is MarkdownInline.Link -> item.copy(children = tidied(item.children, edges = false))
            else -> item
        }
        val last = merged.lastOrNull()
        if (inner is MarkdownInline.Text && last is MarkdownInline.Text) merged[merged.lastIndex] = MarkdownInline.Text(last.text + inner.text) else merged += inner
    }
    val out = merged.map { if (it is MarkdownInline.Text) MarkdownInline.Text(it.text.replace(WHITESPACE_RUN, " ")) else it }.toMutableList()
    out.indices.forEach { at ->
        val item = out[at]
        if (item !is MarkdownInline.Text) return@forEach
        val afterBreak = at == 0 && edges || out.getOrNull(at - 1) == MarkdownInline.LineBreak
        val beforeBreak = at == out.lastIndex && edges || out.getOrNull(at + 1) == MarkdownInline.LineBreak
        out[at] = MarkdownInline.Text(item.text.let { if (afterBreak) it.trimStart() else it }.let { if (beforeBreak) it.trimEnd() else it })
    }
    return out.filterNot { it is MarkdownInline.Text && it.text.isEmpty() }
}

private val WHITESPACE_RUN = Regex("[ \t\n\r]+")

/** [math]'s LaTeX: what is between its dollars, without a quote's `>` markers. */
internal fun math(node: ASTNode, source: CharSequence): String {
    val dollars = node.children.filter { it.type == GFMTokenTypes.DOLLAR }
    if (dollars.size < 2) return ""
    val from = dollars.first().endOffset
    val to = dollars.last().startOffset
    val markers = node.children.filter { it.type == MarkdownTokenTypes.BLOCK_QUOTE }
    return buildString {
        var at = from
        markers.filter { it.startOffset >= from && it.endOffset <= to }.forEach { marker ->
            append(source, at, marker.startOffset)
            at = marker.endOffset
        }
        append(source, at, to)
    }.trim()
}

internal fun ASTNode.child(type: IElementType): ASTNode? = children.firstOrNull { it.type == type }

/** What [this] reads as plain text: an image's alt, a heading's slug. */
internal fun List<MarkdownInline>.plain(): String = joinToString("") { inline ->
    when (inline) {
        is MarkdownInline.Text -> inline.text
        is MarkdownInline.Styled -> inline.children.plain()
        is MarkdownInline.Code -> inline.text
        is MarkdownInline.Link -> inline.children.plain()
        is MarkdownInline.Image -> inline.alt
        is MarkdownInline.Math -> inline.latex
        MarkdownInline.LineBreak -> " "
        is MarkdownInline.FootnoteReference, is MarkdownInline.Source -> ""
    }
}

/** Text as Markdown writes it, as it reads: entities and, outside HTML, backslash escapes resolved. */
internal object MarkdownCharacters {
    fun decode(raw: CharSequence, escapes: Boolean): String = (if (escapes) WITH_ESCAPES else ENTITIES).replace(raw) { match ->
        val groups = match.groupValues
        when {
            groups[1].isNotEmpty() -> Entities.map[match.value]?.let(::character) ?: match.value
            groups[2].isNotEmpty() -> character(groups[2].toInt())
            groups[3].isNotEmpty() -> character(groups[3].toInt(16))
            else -> groups[4]
        }
    }

    private fun character(code: Int): String = if (code in 1..0x10FFFF && code !in 0xD800..0xDFFF) String(Character.toChars(code)) else "�"

    private val ENTITIES = Regex("&(?:([a-zA-Z][a-zA-Z0-9]*)|#([0-9]{1,7})|#[xX]([a-fA-F0-9]{1,6}));")
    private val WITH_ESCAPES = Regex(ENTITIES.pattern + """|\\([!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~])""")
}
