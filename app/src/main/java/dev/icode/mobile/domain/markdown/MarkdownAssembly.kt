package dev.icode.mobile.domain.markdown

/**
 * A piece of a block sequence: a block, or HTML opening or closing a container whose content is the
 * Markdown between — `<details>`, then paragraphs, then `</details>` in a later HTML block, as
 * GitHub's READMEs write it.
 */
internal sealed interface MarkdownPiece {
    data class Block(val block: MarkdownBlock) : MarkdownPiece
    data class Open(val container: MarkdownContainer) : MarkdownPiece
    data class Close(val name: String) : MarkdownPiece
}

/** What an HTML container makes of the blocks in it; [name] its tag, which its end tag names. */
internal sealed interface MarkdownContainer {
    val name: String

    data class Details(val summary: List<MarkdownInline>, val open: Boolean) : MarkdownContainer {
        override val name = "details"
    }

    data class Aligned(override val name: String, val alignment: MarkdownAlignment) : MarkdownContainer

    data object Quote : MarkdownContainer {
        override val name = "blockquote"
    }

    /** A `<div>` or `<p>` without `align`: its blocks, as they are. */
    data class Plain(override val name: String) : MarkdownContainer
}

internal object MarkdownAssembly {
    /** [pieces] as blocks: each container holds what is up to its end tag, or to the end. */
    fun blocks(pieces: List<MarkdownPiece>): List<MarkdownBlock> {
        class Level(val container: MarkdownContainer?, val blocks: MutableList<MarkdownBlock> = mutableListOf())
        val stack = ArrayDeque(listOf(Level(null)))
        fun close() {
            val level = stack.removeLast()
            stack.last().blocks += wrap(checkNotNull(level.container), level.blocks)
        }
        pieces.forEach { piece ->
            when (piece) {
                is MarkdownPiece.Block -> stack.last().blocks += piece.block
                is MarkdownPiece.Open -> stack.addLast(Level(piece.container))
                // An end tag nothing open names closes nothing, as in a browser.
                is MarkdownPiece.Close -> stack.indexOfLast { it.container?.name == piece.name }.takeIf { it > 0 }?.let { match ->
                    repeat(stack.size - match) { close() }
                }
            }
        }
        while (stack.size > 1) close()
        return stack.single().blocks
    }

    /**
     * The document of [blocks]: its references resolved, its footnotes numbered by first reference, its
     * anchors. What resolving leaves as it was is the same object, so the screen skips it.
     */
    fun document(
        blocks: List<MarkdownBlock>,
        references: Map<String, MarkdownReference>,
        footnotes: Map<String, List<MarkdownInline>>,
    ): MarkdownDocument {
        val taken = mutableMapOf<String, Int>()
        val anchors = linkedMapOf<String, Int>()
        // Each footnote referred to, and the block that first refers to it.
        val referred = linkedMapOf<String, Int>()
        blocks.forEachIndexed { index, block ->
            visit(block, onBlock = { if (it is MarkdownBlock.Heading) anchors.putIfAbsent(MarkdownLinks.unique(MarkdownLinks.slug(it.content.plain()), taken), index) }) { inline ->
                if (inline is MarkdownInline.FootnoteReference && inline.label in footnotes) referred.putIfAbsent(inline.label, index)
            }
        }
        val resolver = Resolver(references, referred.keys.withIndex().associate { (at, label) -> label to at + 1 })
        return MarkdownDocument(
            blocks = blocks.shared(resolver::block),
            footnotes = referred.entries.mapIndexed { at, (label, from) -> MarkdownFootnote(label, at + 1, footnotes.getValue(label).shared(resolver::inline), from) },
            anchors = anchors,
        )
    }

    /** Links to defined labels made direct, footnote references numbered; the rest as it was. */
    private class Resolver(private val references: Map<String, MarkdownReference>, private val numbers: Map<String, Int>) {
        fun block(block: MarkdownBlock): MarkdownBlock = when (block) {
            is MarkdownBlock.Heading -> block.content.shared(::inline).let { if (it === block.content) block else block.copy(content = it) }
            is MarkdownBlock.Paragraph -> block.content.shared(::inline).let { if (it === block.content) block else block.copy(content = it) }
            is MarkdownBlock.ListBlock -> block.items.shared { item ->
                item.blocks.shared(::block).let { if (it === item.blocks) item else item.copy(blocks = it) }
            }.let { if (it === block.items) block else block.copy(items = it) }
            is MarkdownBlock.Quote -> block.blocks.shared(::block).let { if (it === block.blocks) block else block.copy(blocks = it) }
            is MarkdownBlock.Alert -> block.blocks.shared(::block).let { if (it === block.blocks) block else block.copy(blocks = it) }
            is MarkdownBlock.Aligned -> block.blocks.shared(::block).let { if (it === block.blocks) block else block.copy(blocks = it) }
            is MarkdownBlock.Details -> {
                val summary = block.summary.shared(::inline)
                val blocks = block.blocks.shared(::block)
                if (summary === block.summary && blocks === block.blocks) block else block.copy(summary = summary, blocks = blocks)
            }
            is MarkdownBlock.Table -> {
                val header = block.header.shared { it.shared(::inline) }
                val rows = block.rows.shared { row -> row.shared { it.shared(::inline) } }
                if (header === block.header && rows === block.rows) block else block.copy(header = header, rows = rows)
            }
            is MarkdownBlock.Code, is MarkdownBlock.Math, MarkdownBlock.Rule, is MarkdownBlock.FrontMatter, is MarkdownBlock.Source -> block
        }

        fun inline(inline: MarkdownInline): MarkdownInline = when (inline) {
            is MarkdownInline.Styled -> inline.children.shared(::inline).let { if (it === inline.children) inline else inline.copy(children = it) }
            is MarkdownInline.Link -> {
                val children = inline.children.shared(::inline)
                val defined = (inline.destination as? MarkdownDestination.Reference)?.let { references[it.label] }
                when {
                    defined != null -> inline.copy(destination = MarkdownDestination.Url(defined.destination), title = inline.title ?: defined.title, children = children)
                    children === inline.children -> inline
                    else -> inline.copy(children = children)
                }
            }
            is MarkdownInline.Image -> (inline.destination as? MarkdownDestination.Reference)?.let { references[it.label] }
                ?.let { inline.copy(destination = MarkdownDestination.Url(it.destination), title = inline.title ?: it.title) } ?: inline
            is MarkdownInline.FootnoteReference -> numbers[inline.label].let { if (it == inline.number) inline else inline.copy(number = it) }
            else -> inline
        }
    }

    /** [this] with [transform] applied; the same list when it changed nothing. */
    private inline fun <T> List<T>.shared(transform: (T) -> T): List<T> {
        var changed: MutableList<T>? = null
        forEachIndexed { at, item ->
            val next = transform(item)
            if (changed == null && next !== item) changed = ArrayList<T>(size).apply { addAll(this@shared.subList(0, at)) }
            changed?.add(next)
        }
        return changed ?: this
    }

    private fun wrap(container: MarkdownContainer, blocks: List<MarkdownBlock>): List<MarkdownBlock> = when (container) {
        is MarkdownContainer.Details -> listOf(MarkdownBlock.Details(container.summary, container.open, blocks))
        is MarkdownContainer.Aligned -> listOf(MarkdownBlock.Aligned(container.alignment, blocks))
        MarkdownContainer.Quote -> listOf(MarkdownBlock.Quote(blocks))
        is MarkdownContainer.Plain -> blocks
    }

    /** [document]'s formulas, LaTeX and whether it is display math, each once: its footnotes' too. */
    fun formulas(document: MarkdownDocument): Set<Pair<String, Boolean>> {
        val formulas = linkedSetOf<Pair<String, Boolean>>()
        val inline = { item: MarkdownInline -> if (item is MarkdownInline.Math) formulas += item.latex to false }
        val blocks = document.blocks + document.footnotes.map { MarkdownBlock.Paragraph(it.content) }
        blocks.forEach { block -> visit(block, onBlock = { if (it is MarkdownBlock.Math) formulas += it.latex to true }, onInline = inline) }
        return formulas
    }

    /** Every block in [block], itself first, and every inline, in reading order. */
    private fun visit(block: MarkdownBlock, onBlock: (MarkdownBlock) -> Unit, onInline: (MarkdownInline) -> Unit) {
        fun inlines(items: List<MarkdownInline>) {
            items.forEach { item ->
                onInline(item)
                when (item) {
                    is MarkdownInline.Styled -> inlines(item.children)
                    is MarkdownInline.Link -> inlines(item.children)
                    else -> Unit
                }
            }
        }
        fun blocks(items: List<MarkdownBlock>) = items.forEach { visit(it, onBlock, onInline) }
        onBlock(block)
        when (block) {
            is MarkdownBlock.Heading -> inlines(block.content)
            is MarkdownBlock.Paragraph -> inlines(block.content)
            is MarkdownBlock.ListBlock -> block.items.forEach { blocks(it.blocks) }
            is MarkdownBlock.Quote -> blocks(block.blocks)
            is MarkdownBlock.Alert -> blocks(block.blocks)
            is MarkdownBlock.Table -> (listOf(block.header) + block.rows).forEach { row -> row.forEach(::inlines) }
            is MarkdownBlock.Aligned -> blocks(block.blocks)
            is MarkdownBlock.Details -> {
                inlines(block.summary)
                blocks(block.blocks)
            }
            is MarkdownBlock.Code, is MarkdownBlock.Math, MarkdownBlock.Rule, is MarkdownBlock.FrontMatter, is MarkdownBlock.Source -> Unit
        }
    }
}
