package dev.icode.mobile.domain.markdown

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.impl.ListCompositeNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

/**
 * Markdown's blocks from the parser's nodes to iCode's (docs/markdown-rendering-plan.md §3.1). It
 * gathers the link reference definitions and footnotes it meets; [source] is the text the nodes'
 * offsets are in.
 */
internal class MarkdownBlockReader(private val source: CharSequence) {
    val references = linkedMapOf<String, MarkdownReference>()
    val footnotes = linkedMapOf<String, List<MarkdownInline>>()
    private val inlines = MarkdownInlineReader(source)
    private val cells = MarkdownInlineReader(source, table = true)

    /** One of the document's top nodes: a block, several (a paragraph split by display math), or HTML opening or closing a container. */
    fun pieces(node: ASTNode): List<MarkdownPiece> = when (node.type) {
        MarkdownElementTypes.PARAGRAPH -> paragraph(node)
        MarkdownElementTypes.HTML_BLOCK -> MarkdownHtmlBlocks.pieces(
            MarkdownHtml.tree(MarkdownHtml.bits(node.children.filter { it.type == MarkdownTokenTypes.HTML_BLOCK_CONTENT }.joinToString("\n", transform = ::text))),
        )
        MarkdownElementTypes.LINK_DEFINITION -> emptyList<MarkdownPiece>().also { definition(node) }
        else -> listOfNotNull(block(node)?.let(MarkdownPiece::Block))
    }

    /** [nodes] as blocks, with the containers HTML opens closed at their end. */
    fun blocks(nodes: List<ASTNode>): List<MarkdownBlock> = MarkdownAssembly.blocks(nodes.flatMap(::pieces))

    private fun block(node: ASTNode): MarkdownBlock? = when (node.type) {
        in ATX -> heading(ATX.indexOf(node.type) + 1, node.child(MarkdownTokenTypes.ATX_CONTENT), atx = true)
        MarkdownElementTypes.SETEXT_1 -> heading(1, node.child(MarkdownTokenTypes.SETEXT_CONTENT), atx = false)
        MarkdownElementTypes.SETEXT_2 -> heading(2, node.child(MarkdownTokenTypes.SETEXT_CONTENT), atx = false)
        MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> list(node)
        MarkdownElementTypes.BLOCK_QUOTE -> MarkdownBlock.Quote(blocks(node.children))
        GFMElementTypes.ALERT -> {
            val title = node.child(GFMTokenTypes.ALERT_TITLE)?.let(::text).orEmpty().removePrefix("[!").removeSuffix("]")
            MarkdownBlock.Alert(MarkdownAlertKind.entries.firstOrNull { it.name.equals(title, ignoreCase = true) } ?: MarkdownAlertKind.Note, blocks(node.children))
        }
        MarkdownElementTypes.CODE_FENCE -> fence(node)
        MarkdownElementTypes.CODE_BLOCK -> MarkdownBlock.Code(null, indented(node))
        GFMElementTypes.TABLE -> table(node)
        MarkdownTokenTypes.HORIZONTAL_RULE -> MarkdownBlock.Rule
        else -> null
    }

    /**
     * A paragraph's lines. `$$…$$` in it is display math, drawn on its own between what is before and
     * after it, as GitHub draws it. A line beginning `[^label]:` begins that footnote's definition.
     */
    private fun paragraph(node: ASTNode): List<MarkdownPiece> {
        val pieces = mutableListOf<MarkdownPiece>()
        val children = node.children
        val run = mutableListOf<ASTNode>()
        var footnote: String? = null
        fun flush() {
            val content = inlines.read(run)
            run.clear()
            val label = footnote
            if (label != null) footnotes.putIfAbsent(label, content) else if (content.isNotEmpty()) pieces += MarkdownPiece.Block(MarkdownBlock.Paragraph(content))
        }
        var at = 0
        while (at < children.size) {
            val child = children[at]
            val label = footnoteLabel(children, at)
            when {
                child.type == GFMElementTypes.BLOCK_MATH -> {
                    flush()
                    footnote = null
                    pieces += MarkdownPiece.Block(MarkdownBlock.Math(math(child, source)))
                }
                label != null -> {
                    flush()
                    footnote = label
                    at++ // its colon
                }
                else -> run += child
            }
            at++
        }
        flush()
        return pieces
    }

    /** The label of the footnote whose definition begins at [at], `[^label]:` at a line's start; null when none does. */
    private fun footnoteLabel(children: List<ASTNode>, at: Int): String? {
        val node = children[at]
        if (node.type != MarkdownElementTypes.SHORT_REFERENCE_LINK || children.getOrNull(at + 1)?.type != MarkdownTokenTypes.COLON) return null
        val before = children.subList(0, at).lastOrNull { it.type != MarkdownTokenTypes.WHITE_SPACE && it.type != MarkdownTokenTypes.BLOCK_QUOTE }
        if (before != null && before.type != MarkdownTokenTypes.EOL) return null
        val written = text(node)
        return if (written.startsWith("[^")) MarkdownLinks.label(written.substring(2, written.length - 1)) else null
    }

    /** A heading's text; an ATX heading's closing `#`s are not part of it. */
    private fun heading(level: Int, content: ASTNode?, atx: Boolean): MarkdownBlock {
        val read = content?.let { inlines.read(it.children) }.orEmpty()
        val last = read.lastOrNull()
        val trimmed = if (atx && last is MarkdownInline.Text && ATX_CLOSING.containsMatchIn(last.text)) {
            read.dropLast(1) + listOfNotNull(last.text.replace(ATX_CLOSING, "").takeIf { it.isNotEmpty() }?.let(MarkdownInline::Text))
        } else {
            read
        }
        return MarkdownBlock.Heading(level, trimmed)
    }

    private fun list(node: ASTNode): MarkdownBlock {
        val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        val start = if (node.type == MarkdownElementTypes.ORDERED_LIST) {
            items.firstOrNull()?.child(MarkdownTokenTypes.LIST_NUMBER)?.let { text(it).takeWhile(Char::isDigit).toIntOrNull() } ?: 1
        } else {
            null
        }
        return MarkdownBlock.ListBlock(
            start,
            loose = (node as? ListCompositeNode)?.loose == true,
            items = items.map { item ->
                MarkdownListItem(item.child(GFMTokenTypes.CHECK_BOX)?.let { text(it).contains('x', ignoreCase = true) }, blocks(item.children))
            },
        )
    }

    /** A fence's lines, as written; ```` ```math ```` is display math. */
    private fun fence(node: ASTNode): MarkdownBlock {
        val language = node.child(MarkdownTokenTypes.FENCE_LANG)?.let { text(it).trim().substringBefore(' ') }?.takeIf { it.isNotEmpty() }
        var opened = false
        val code = buildString {
            node.children.forEach { child ->
                when (child.type) {
                    MarkdownTokenTypes.CODE_FENCE_CONTENT -> append(text(child))
                    MarkdownTokenTypes.EOL -> if (opened) append('\n') else opened = true
                }
            }
        }.removeSuffix("\n")
        return if (language.equals("math", ignoreCase = true)) MarkdownBlock.Math(code.trim()) else MarkdownBlock.Code(language, code)
    }

    /** An indented block's lines without their four columns of indentation. */
    private fun indented(node: ASTNode): String = buildString {
        node.children.forEach { child ->
            when (child.type) {
                MarkdownTokenTypes.CODE_LINE -> append(text(child).let { line -> if (line.startsWith("\t")) line.drop(1) else line.drop(line.takeWhile { it == ' ' }.length.coerceAtMost(4)) })
                MarkdownTokenTypes.EOL -> append('\n')
            }
        }
    }.trimEnd('\n')

    /** GFM's table: alignments from its delimiter row; each row as wide as the header, as GFM has it. */
    private fun table(node: ASTNode): MarkdownBlock {
        val header = node.child(GFMElementTypes.HEADER)?.let(::cellsOf).orEmpty()
        val alignments = node.child(GFMTokenTypes.TABLE_SEPARATOR)?.let(::text).orEmpty().trim().removePrefix("|").removeSuffix("|").split('|').map { written ->
            val cell = written.trim()
            when {
                cell.startsWith(':') && cell.endsWith(':') -> MarkdownAlignment.Center
                cell.endsWith(':') -> MarkdownAlignment.End
                else -> MarkdownAlignment.Start
            }
        }
        val width = header.size
        return MarkdownBlock.Table(
            alignments = List(width) { alignments.getOrNull(it) ?: MarkdownAlignment.Start },
            header = header,
            rows = node.children.filter { it.type == GFMElementTypes.ROW }.map { row -> cellsOf(row).let { cells -> List(width) { cells.getOrNull(it).orEmpty() } } },
        )
    }

    private fun cellsOf(row: ASTNode): List<List<MarkdownInline>> = row.children.filter { it.type == GFMTokenTypes.CELL }.map { cells.read(it.children) }

    /** `[label]: destination "title"`; `[^label]: text` defines a footnote. The first definition of a label holds. */
    private fun definition(node: ASTNode) {
        val label = node.child(MarkdownElementTypes.LINK_LABEL)?.let { text(it).drop(1).dropLast(1) } ?: return
        if (label.startsWith("^")) {
            val written = source.substring(node.child(MarkdownElementTypes.LINK_LABEL)!!.endOffset, node.endOffset).removePrefix(":").trim()
            footnotes.putIfAbsent(MarkdownLinks.label(label.drop(1)), listOf(MarkdownInline.Text(MarkdownCharacters.decode(written, escapes = true))))
            return
        }
        val destination = node.child(MarkdownElementTypes.LINK_DESTINATION)?.let(::text).orEmpty().let { if (it.startsWith("<") && it.endsWith(">")) it.drop(1).dropLast(1) else it }
        val title = node.child(MarkdownElementTypes.LINK_TITLE)?.let { MarkdownCharacters.decode(text(it).drop(1).dropLast(1), escapes = true) }
        references.putIfAbsent(MarkdownLinks.label(label), MarkdownReference(MarkdownCharacters.decode(destination, escapes = true), title))
    }

    private fun text(node: ASTNode): String = source.substring(node.startOffset, node.endOffset)

    private companion object {
        val ATX = listOf(
            MarkdownElementTypes.ATX_1, MarkdownElementTypes.ATX_2, MarkdownElementTypes.ATX_3,
            MarkdownElementTypes.ATX_4, MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6,
        )

        /** `## Title ##`: a run of `#`s after a space, at the end. */
        val ATX_CLOSING = Regex("(^|\\s+)#+\\s*$")
    }
}
