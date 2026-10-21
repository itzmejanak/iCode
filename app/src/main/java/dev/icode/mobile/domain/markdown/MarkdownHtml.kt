package dev.icode.mobile.domain.markdown

/**
 * The HTML iCode draws in Markdown, natively (docs/markdown-rendering-plan.md §3.5): inline styles,
 * links, images and line breaks; paragraphs and headings with `align`; quotes, `<pre>`, lists,
 * tables, rules and `<details>`. Comments are hidden. Any other tag shows as it was written, muted:
 * nothing vanishes, and nothing runs.
 */
internal object MarkdownHtml {
    /** A tag, or Markdown's own content between tags. */
    sealed interface Bit {
        data class Open(val name: String, val attributes: Map<String, String>, val source: String) : Bit
        data class Close(val name: String, val source: String) : Bit
        data class Content(val inline: MarkdownInline) : Bit
    }

    /** What tags make, once each is matched with its end. */
    sealed interface Node {
        data class Element(
            val name: String,
            val attributes: Map<String, String>,
            val children: List<Node>,
            val source: String,
            /** False when the HTML ended before its end tag. */
            val closed: Boolean,
            val closeSource: String = "",
        ) : Node

        data class Content(val inline: MarkdownInline) : Node

        /** An end tag nothing opened here: it may close what an earlier block opened. */
        data class StrayClose(val name: String, val source: String) : Node
    }

    /** [html]'s tags and text: an HTML block's content, or one inline tag. Comments are dropped. */
    fun bits(html: String): List<Bit> {
        val bits = mutableListOf<Bit>()
        var at = 0
        TOKEN.findAll(html).forEach { match ->
            if (match.range.first > at) bits += text(html.substring(at, match.range.first))
            at = match.range.last + 1
            val (close, name, attributes) = match.destructured
            when {
                match.value.startsWith("<!--") -> Unit
                name.isEmpty() -> bits += Bit.Content(MarkdownInline.Source(match.value))
                close == "/" -> bits += Bit.Close(name.lowercase(), match.value)
                else -> bits += Bit.Open(name.lowercase(), attributes(attributes), match.value)
            }
        }
        if (at < html.length) bits += text(html.substring(at))
        return bits
    }

    /** [bits] as elements, each matched with its end tag as a browser would: an end closes what it skips over. */
    fun tree(bits: List<Bit>): List<Node> {
        class Open(val bit: Bit.Open, val children: MutableList<Node> = mutableListOf())
        val root = mutableListOf<Node>()
        val stack = ArrayDeque<Open>()
        fun current() = stack.lastOrNull()?.children ?: root
        fun close(open: Open, closed: Boolean, source: String = "") =
            Node.Element(open.bit.name, open.bit.attributes, open.children, open.bit.source, closed, source)
        bits.forEach { bit ->
            when (bit) {
                is Bit.Content -> current() += Node.Content(bit.inline)
                is Bit.Open -> if (bit.name in VOID || bit.source.endsWith("/>")) {
                    current() += Node.Element(bit.name, bit.attributes, emptyList(), bit.source, closed = true)
                } else {
                    stack.addLast(Open(bit))
                }
                is Bit.Close -> {
                    val match = stack.indexOfLast { it.bit.name == bit.name }
                    if (match < 0) {
                        current() += Node.StrayClose(bit.name, bit.source)
                    } else {
                        while (stack.size > match + 1) {
                            val inner = stack.removeLast()
                            stack.last().children += close(inner, closed = true)
                        }
                        val done = stack.removeLast()
                        current() += close(done, closed = true, bit.source)
                    }
                }
            }
        }
        while (stack.isNotEmpty()) {
            val open = stack.removeLast()
            current() += close(open, closed = false)
        }
        return root
    }

    /** [nodes] in a line of text. */
    fun inlines(nodes: List<Node>): List<MarkdownInline> = nodes.flatMap { node ->
        when (node) {
            is Node.Content -> listOf(node.inline)
            is Node.StrayClose -> if (node.name in KNOWN) emptyList() else listOf(MarkdownInline.Source(node.source))
            is Node.Element -> element(node)
        }
    }

    private fun element(node: Node.Element): List<MarkdownInline> {
        val children by lazy { inlines(node.children) }
        STYLES[node.name]?.let { return listOf(MarkdownInline.Styled(it, children)) }
        return when (node.name) {
            "code", "tt", "samp", "var" -> listOf(MarkdownInline.Code(plain(node.children)))
            "kbd" -> listOf(MarkdownInline.Code(plain(node.children), key = true))
            "br" -> listOf(MarkdownInline.LineBreak)
            "a" -> node.attributes["href"]?.let { listOf(MarkdownInline.Link(MarkdownDestination.Url(it), node.attributes["title"], children)) } ?: children
            "img" -> node.attributes["src"]?.let {
                listOf(MarkdownInline.Image(MarkdownDestination.Url(it), node.attributes["alt"].orEmpty(), node.attributes["title"], node.attributes["width"]?.toIntOrNull()))
            } ?: emptyList()
            in KNOWN -> children
            else -> listOf(MarkdownInline.Source(node.source)) + children + listOfNotNull(node.closeSource.takeIf { it.isNotEmpty() }?.let(MarkdownInline::Source))
        }
    }

    /** The text in [nodes], tags left out: a `<code>`'s or `<pre>`'s. */
    fun plain(nodes: List<Node>): String = nodes.joinToString("") { node ->
        when (node) {
            is Node.Content -> (node.inline as? MarkdownInline.Text)?.text ?: (node.inline as? MarkdownInline.Source)?.text.orEmpty()
            is Node.Element -> if (node.name == "br") "\n" else plain(node.children)
            is Node.StrayClose -> ""
        }
    }

    /** An attribute's `align` as an alignment. */
    fun alignment(attributes: Map<String, String>): MarkdownAlignment? = when (attributes["align"]?.lowercase()) {
        "center" -> MarkdownAlignment.Center
        "right" -> MarkdownAlignment.End
        "left" -> MarkdownAlignment.Start
        else -> null
    }

    private fun text(raw: String): Bit = Bit.Content(MarkdownInline.Text(MarkdownCharacters.decode(raw, escapes = false)))

    private fun attributes(text: String): Map<String, String> = ATTRIBUTE.findAll(text).associate { match ->
        val (name, double, single, bare) = match.destructured
        name.lowercase() to MarkdownCharacters.decode(double.ifEmpty { single.ifEmpty { bare } }, escapes = false)
    }

    /** Elements that never have content. */
    private val VOID = setOf("br", "hr", "img", "wbr", "input", "source", "col", "area", "meta", "link", "base", "embed", "track", "param")

    private val STYLES = mapOf(
        "b" to MarkdownStyle.Strong, "strong" to MarkdownStyle.Strong,
        "i" to MarkdownStyle.Emphasis, "em" to MarkdownStyle.Emphasis, "cite" to MarkdownStyle.Emphasis, "dfn" to MarkdownStyle.Emphasis,
        "s" to MarkdownStyle.Strike, "strike" to MarkdownStyle.Strike, "del" to MarkdownStyle.Strike,
        "ins" to MarkdownStyle.Underline, "u" to MarkdownStyle.Underline,
        "sub" to MarkdownStyle.Subscript, "sup" to MarkdownStyle.Superscript, "mark" to MarkdownStyle.Mark,
    )

    /** Tags drawn by what they hold, or as blocks (MarkdownBlockReader): never shown as source. */
    val BLOCKS = setOf(
        "p", "div", "center", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "hr", "details", "summary",
        "ul", "ol", "li", "table", "thead", "tbody", "tfoot", "tr", "th", "td", "caption",
        "dl", "dt", "dd", "figure", "figcaption", "section", "article", "header", "footer", "nav", "main", "aside",
    )

    private val KNOWN = STYLES.keys + BLOCKS + setOf(
        "code", "tt", "samp", "var", "kbd", "br", "a", "img", "span", "abbr", "q", "time", "bdo", "font", "big", "small", "wbr", "picture", "source", "ruby", "rt", "rp",
    )

    private val TOKEN = Regex(
        """<!--[\s\S]*?(?:-->|$)|<(/?)([A-Za-z][A-Za-z0-9-]*)((?:\s+[^\s"'>/=]+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'=<>`]+))?)*)\s*/?>|<![^>]*>|<\?[\s\S]*?\?>""",
    )
    private val ATTRIBUTE = Regex("""([^\s"'>/=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+)))?""")
}
