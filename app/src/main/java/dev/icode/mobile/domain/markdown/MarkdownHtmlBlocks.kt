package dev.icode.mobile.domain.markdown

import dev.icode.mobile.domain.markdown.MarkdownHtml.Node

/**
 * An HTML block's elements as blocks (docs/editor-and-language-services.md). A container left open
 * at the block's end — `<details>`, `<div align="center">` — holds the Markdown after it, up to its
 * end tag ([MarkdownAssembly.blocks]).
 */
internal object MarkdownHtmlBlocks {
    fun pieces(nodes: List<Node>): List<MarkdownPiece> {
        val pieces = mutableListOf<MarkdownPiece>()
        val line = mutableListOf<Node>()
        fun flush() {
            val content = tidied(MarkdownHtml.inlines(line), edges = true)
            line.clear()
            if (content.isNotEmpty()) pieces += MarkdownPiece.Block(MarkdownBlock.Paragraph(content))
        }
        nodes.forEach { node ->
            when {
                node is Node.Element && node.name in MarkdownHtml.BLOCKS -> {
                    flush()
                    pieces += element(node)
                }
                node is Node.StrayClose && node.name in MarkdownHtml.BLOCKS -> {
                    flush()
                    pieces += MarkdownPiece.Close(node.name)
                }
                else -> line += node
            }
        }
        flush()
        return pieces
    }

    private fun element(node: Node.Element): List<MarkdownPiece> {
        val container = container(node)
        if (!node.closed && container != null) {
            return listOf(MarkdownPiece.Open(container)) + pieces(node.children.filterNot { it is Node.Element && it.name == "summary" })
        }
        val blocks by lazy { MarkdownAssembly.blocks(pieces(node.children)) }
        val aligned = MarkdownHtml.alignment(node.attributes) ?: MarkdownAlignment.Center.takeIf { node.name == "center" }
        fun maybeAligned(content: List<MarkdownBlock>) = aligned?.let { listOf(MarkdownBlock.Aligned(it, content)) } ?: content
        val made: List<MarkdownBlock> = when (node.name) {
            "h1", "h2", "h3", "h4", "h5", "h6" ->
                maybeAligned(listOf(MarkdownBlock.Heading(node.name[1].digitToInt(), tidied(MarkdownHtml.inlines(node.children), edges = true))))
            "blockquote" -> listOf(MarkdownBlock.Quote(blocks))
            "pre" -> listOf(pre(node))
            "hr" -> listOf(MarkdownBlock.Rule)
            "details" -> listOf(
                MarkdownBlock.Details(
                    summary(node),
                    open = "open" in node.attributes,
                    blocks = MarkdownAssembly.blocks(pieces(node.children.filterNot { it is Node.Element && it.name == "summary" })),
                ),
            )
            "summary" -> listOf(MarkdownBlock.Paragraph(tidied(MarkdownHtml.inlines(node.children), edges = true)))
            "ul", "ol" -> listOf(
                MarkdownBlock.ListBlock(
                    start = if (node.name == "ol") node.attributes["start"]?.toIntOrNull() ?: 1 else null,
                    loose = false,
                    items = node.children.filter { it is Node.Element && it.name == "li" }.map { item ->
                        MarkdownListItem(null, MarkdownAssembly.blocks(pieces((item as Node.Element).children)))
                    },
                ),
            )
            "table" -> listOf(table(node))
            else -> maybeAligned(blocks)
        }
        return made.map(MarkdownPiece::Block)
    }

    /** What an open [node] makes of the blocks after it; null for one that holds none (a `<ul>` left open is closed with its block). */
    private fun container(node: Node.Element): MarkdownContainer? = when (node.name) {
        "details" -> MarkdownContainer.Details(summary(node), "open" in node.attributes)
        "blockquote" -> MarkdownContainer.Quote
        "center" -> MarkdownContainer.Aligned(node.name, MarkdownAlignment.Center)
        "p", "div" -> MarkdownHtml.alignment(node.attributes)?.let { MarkdownContainer.Aligned(node.name, it) } ?: MarkdownContainer.Plain(node.name)
        else -> null
    }

    private fun summary(node: Node.Element): List<MarkdownInline> =
        node.children.firstOrNull { it is Node.Element && it.name == "summary" }
            ?.let { tidied(MarkdownHtml.inlines((it as Node.Element).children), edges = true) }
            ?.takeIf { it.isNotEmpty() } ?: listOf(MarkdownInline.Text("Details"))

    /** `<pre>`, its language from a `<code class="language-…">` in it, as GitHub's own HTML writes it. */
    private fun pre(node: Node.Element): MarkdownBlock {
        val code = node.children.singleOrNull { it is Node.Element && it.name == "code" } as? Node.Element
        val language = code?.attributes?.get("class")?.split(' ')?.firstNotNullOfOrNull { it.removePrefix("language-").removePrefix("lang-").takeIf { name -> name != it } }
        return MarkdownBlock.Code(language, MarkdownHtml.plain(node.children).removePrefix("\n").trimEnd('\n'))
    }

    /** An HTML table: its first row the header when it is all `<th>` or in a `<thead>`; rows as wide as the widest. */
    private fun table(node: Node.Element): MarkdownBlock {
        val sections = node.children.filterIsInstance<Node.Element>()
        val rows = sections.flatMap { if (it.name in SECTIONS) it.children.filterIsInstance<Node.Element>() else listOf(it) }.filter { it.name == "tr" }
        val cells = rows.map { row -> row.children.filterIsInstance<Node.Element>().filter { it.name == "th" || it.name == "td" } }
        val width = cells.maxOfOrNull { it.size } ?: 0
        val headed = sections.any { it.name == "thead" } || cells.firstOrNull()?.let { it.isNotEmpty() && it.all { cell -> cell.name == "th" } } == true
        fun row(cells: List<Node.Element>) = List(width) { at -> cells.getOrNull(at)?.let { tidied(MarkdownHtml.inlines(it.children), edges = true) }.orEmpty() }
        return MarkdownBlock.Table(
            alignments = List(width) { at -> cells.firstOrNull()?.getOrNull(at)?.let { MarkdownHtml.alignment(it.attributes) } ?: MarkdownAlignment.Start },
            header = if (headed) row(cells.first()) else emptyList(),
            rows = (if (headed) cells.drop(1) else cells).map(::row),
        )
    }

    private val SECTIONS = setOf("thead", "tbody", "tfoot")
}
