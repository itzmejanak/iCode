package dev.icode.mobile.domain.markdown

/**
 * A Markdown document as iCode draws it (docs/markdown-rendering-plan.md §3.1). Its references are
 * resolved: a link to a defined label leads where the definition says, and a footnote reference has
 * its number. A block that did not change since the last reading is the same object, so the screen
 * does not draw it again.
 */
data class MarkdownDocument(
    val blocks: List<MarkdownBlock>,
    /** The footnotes referred to, numbered as GitHub numbers them: in the order of first reference. */
    val footnotes: List<MarkdownFootnote> = emptyList(),
    /** Each heading's anchor, GitHub's slug, and the index in [blocks] of the block it is in. */
    val anchors: Map<String, Int> = emptyMap(),
) {
    companion object {
        val Empty = MarkdownDocument(emptyList())
    }
}

/** Where `[text][label]` points. */
data class MarkdownReference(val destination: String, val title: String?)

/** A footnote, [referredFrom] the index of the block that first refers to it. */
data class MarkdownFootnote(val label: String, val number: Int, val content: List<MarkdownInline>, val referredFrom: Int)

enum class MarkdownAlignment { Start, Center, End }

/** GitHub's alerts, `> [!NOTE]` and the rest. */
enum class MarkdownAlertKind { Note, Tip, Important, Warning, Caution }

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val content: List<MarkdownInline>) : MarkdownBlock

    data class Paragraph(val content: List<MarkdownInline>) : MarkdownBlock

    /** Bullets when [start] is null; numbered from [start] otherwise. */
    data class ListBlock(val start: Int?, val loose: Boolean, val items: List<MarkdownListItem>) : MarkdownBlock

    data class Quote(val blocks: List<MarkdownBlock>) : MarkdownBlock

    data class Alert(val kind: MarkdownAlertKind, val blocks: List<MarkdownBlock>) : MarkdownBlock

    /** A fenced or indented block of code; [language] the fence's first word. */
    data class Code(val language: String?, val text: String) : MarkdownBlock

    /** Display math: `$$…$$`, or a ```` ```math ```` fence. */
    data class Math(val latex: String) : MarkdownBlock

    /** Every row as wide as the table; [header] empty when it has none (an HTML table without `<th>`). */
    data class Table(
        val alignments: List<MarkdownAlignment>,
        val header: List<List<MarkdownInline>>,
        val rows: List<List<List<MarkdownInline>>>,
    ) : MarkdownBlock

    data object Rule : MarkdownBlock

    /** HTML's `align` on a `<p>`, `<div>` or heading, or a `<center>`. */
    data class Aligned(val alignment: MarkdownAlignment, val blocks: List<MarkdownBlock>) : MarkdownBlock

    /** `<details>`: [summary] shown, [blocks] behind it. */
    data class Details(val summary: List<MarkdownInline>, val open: Boolean, val blocks: List<MarkdownBlock>) : MarkdownBlock

    /** A file's leading `---` block of YAML. */
    data class FrontMatter(val text: String) : MarkdownBlock

    /** HTML outside the set iCode draws, shown as it was written. */
    data class Source(val text: String) : MarkdownBlock
}

/** A list item; [checked] null when it is not a task. */
data class MarkdownListItem(val checked: Boolean?, val blocks: List<MarkdownBlock>)

enum class MarkdownStyle { Emphasis, Strong, Strike, Underline, Subscript, Superscript, Mark }

sealed interface MarkdownInline {
    data class Text(val text: String) : MarkdownInline

    data class Styled(val style: MarkdownStyle, val children: List<MarkdownInline>) : MarkdownInline

    /** A code span; [key] for HTML's `<kbd>`. */
    data class Code(val text: String, val key: Boolean = false) : MarkdownInline

    data class Link(val destination: MarkdownDestination, val title: String?, val children: List<MarkdownInline>) : MarkdownInline

    data class Image(val destination: MarkdownDestination, val alt: String, val title: String?, val width: Int? = null) : MarkdownInline

    data class Math(val latex: String) : MarkdownInline

    data object LineBreak : MarkdownInline

    /** `[^label]`: its footnote's [number]; null when no footnote [label] is defined, and it is drawn as written, [source]. */
    data class FootnoteReference(val label: String, val source: String, val number: Int? = null) : MarkdownInline

    /** HTML outside the set iCode draws, shown as it was written. */
    data class Source(val text: String) : MarkdownInline
}

/** A link's or image's target: written in place, or by a reference label defined elsewhere. */
sealed interface MarkdownDestination {
    data class Url(val href: String) : MarkdownDestination

    /** A label; in a [MarkdownDocument], one nothing defines, drawn as written, [source]. */
    data class Reference(val label: String, val source: String) : MarkdownDestination
}
