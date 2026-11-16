package dev.icode.mobile.ui.syntax

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import dev.icode.mobile.designsystem.IcodePalette
import dev.icode.mobile.designsystem.LocalIcodePalette
import dev.icode.mobile.domain.GitConflictFile
import dev.icode.mobile.domain.GitDiffHunk
import dev.icode.mobile.domain.GitDiffLineKind

// Views that draw code a line at a time (docs/editor-and-language-services.md): the diff view, the
// editor's change sheet and conflict resolution. Each tokenizes whole texts, so a line inside a block
// comment or a long string is coloured as it is in the editor, and draws each line with its slice of
// its text's spans.

/** Where a line drawn alone takes its colours from: line [line] (from 0) of text [text] of the texts tokenized whole. */
internal data class SyntaxPlace(val text: Int, val line: Int)

/** [spans] of a text, line by line: lines split on '\n' only, as [syntaxSpans] splits them. */
internal class SyntaxLines(text: String, private val spans: List<SyntaxSpan>) {
    private val starts: IntArray = run {
        val starts = mutableListOf(0)
        text.forEachIndexed { at, char -> if (char == '\n') starts += at + 1 }
        starts.toIntArray()
    }
    private val length = text.length

    /**
     * Line [index]'s spans, counted from the line's own start and cut at [shown] characters (a diff line
     * is drawn without its '\r'); none past the last line.
     */
    fun line(index: Int, shown: Int): List<SyntaxSpan> {
        if (index !in starts.indices) return emptyList()
        val start = starts[index]
        val end = minOf(start + shown, if (index + 1 < starts.size) starts[index + 1] - 1 else length)
        // Spans are in order and never overlap: the first that ends past the line's start, by halves.
        var low = 0
        var high = spans.size
        while (low < high) {
            val middle = (low + high) / 2
            if (spans[middle].end <= start) low = middle + 1 else high = middle
        }
        val line = mutableListOf<SyntaxSpan>()
        var at = low
        while (at < spans.size && spans[at].start < end) {
            val span = spans[at++]
            line += SyntaxSpan(maxOf(span.start, start) - start, minOf(span.end, end) - start, span.style)
        }
        return line
    }
}

/** The texts a diff's lines are coloured from, and each line's place in them: [places] by hunk, then by line. */
internal class DiffSources(val texts: List<String>, val places: List<List<SyntaxPlace>>)

/**
 * Where [hunks]' lines take their colours from. With the whole [old] and [new] texts they come from (the
 * editor's change sheet has both), each line is coloured as its line in the whole file, unchanged and added
 * lines from [new], removed ones from [old]. Without them, or when the hunks no longer match them (the text
 * changed after its changes were counted), each hunk's sides are tokenized apart: Git's patch holds only
 * those lines, so a hunk starts from the grammar's first state.
 */
internal fun diffSources(hunks: List<GitDiffHunk>, old: String? = null, new: String? = null): DiffSources {
    if (old != null && new != null) {
        val oldLines = old.split('\n')
        val newLines = new.split('\n')
        val matches = hunks.all { hunk ->
            hunk.lines.all { line ->
                val number = (if (line.kind == GitDiffLineKind.Removed) line.oldLine else line.newLine) ?: return@all false
                (if (line.kind == GitDiffLineKind.Removed) oldLines else newLines).getOrNull(number - 1)?.removeSuffix("\r") == line.text
            }
        }
        if (matches) {
            return DiffSources(
                listOf(old, new),
                hunks.map { hunk ->
                    hunk.lines.map { line ->
                        if (line.kind == GitDiffLineKind.Removed) SyntaxPlace(OLD, line.oldLine!! - 1) else SyntaxPlace(NEW, line.newLine!! - 1)
                    }
                },
            )
        }
    }
    val texts = mutableListOf<String>()
    val places = hunks.mapIndexed { index, hunk ->
        val oldSide = mutableListOf<String>()
        val newSide = mutableListOf<String>()
        val placed = hunk.lines.map { line ->
            if (line.kind == GitDiffLineKind.Removed) {
                SyntaxPlace(index * 2 + OLD, oldSide.size).also { oldSide += line.text }
            } else {
                SyntaxPlace(index * 2 + NEW, newSide.size).also {
                    newSide += line.text
                    if (line.kind == GitDiffLineKind.Context) oldSide += line.text
                }
            }
        }
        texts += oldSide.joinToString("\n")
        texts += newSide.joinToString("\n")
        placed
    }
    return DiffSources(texts, places)
}

/**
 * A conflicted file's two versions, coloured whole: [texts] are the file with every conflict taken as its
 * current side, then as its incoming side. Lines outside the conflicts and current sides are coloured from
 * the first, incoming sides from the second, so each side reads as the file it would make.
 */
internal class ConflictSources(
    val texts: List<String>,
    /** Each line outside the conflicts, by its index in the file, to its place. */
    val context: Map<Int, SyntaxPlace>,
    /** Each conflict, by the index of its `<<<<<<<` line, to the places of its current then incoming lines. */
    val sides: Map<Int, Pair<List<SyntaxPlace>, List<SyntaxPlace>>>,
)

internal fun conflictSources(conflict: GitConflictFile): ConflictSources {
    val current = mutableListOf<String>()
    val incoming = mutableListOf<String>()
    val context = mutableMapOf<Int, SyntaxPlace>()
    val sides = mutableMapOf<Int, Pair<List<SyntaxPlace>, List<SyntaxPlace>>>()
    var line = 0
    while (line < conflict.lines.size) {
        val section = conflict.sections.firstOrNull { it.startLine == line }
        if (section == null) {
            context[line] = SyntaxPlace(CURRENT, current.size)
            current += conflict.lines[line]
            incoming += conflict.lines[line]
            line++
        } else {
            sides[section.startLine] = section.current.indices.map { SyntaxPlace(CURRENT, current.size + it) } to
                section.incoming.indices.map { SyntaxPlace(INCOMING, incoming.size + it) }
            current += section.current
            incoming += section.incoming
            line = section.endLine + 1
        }
    }
    return ConflictSources(listOf(current.joinToString("\n"), incoming.joinToString("\n")), context, sides)
}

/** [texts]' spans, each text tokenized whole, to draw a line at a time in the current palette. */
internal class SyntaxColouring(private val lines: List<SyntaxLines>?, private val palette: IcodePalette) {
    /** [text], the line at [place], coloured as it is there; plain while the spans are made, or with no place. */
    fun line(place: SyntaxPlace?, text: String): AnnotatedString {
        val spans = place?.let { lines?.getOrNull(it.text)?.line(it.line, text.length) }.orEmpty()
        return syntaxString(text, spans, palette)
    }
}

/**
 * [texts] tokenized whole as catalog language [language] on the read-only thread, as [rememberSyntax]
 * does one text, for views that draw them a line at a time. Plain until every text's spans are made, and
 * for a null [language], one whose grammar failed, or a text past [SyntaxLimits.BLOCK_CHARS].
 */
@Composable
internal fun rememberSyntaxLines(language: String?, texts: List<String>): SyntaxColouring {
    val context = LocalContext.current
    val palette = LocalIcodePalette.current
    // The lines with the texts they are for: until new texts' come, the old ones do not apply to them.
    val made by produceState<Pair<List<String>, List<SyntaxLines>>?>(null, language, texts) {
        value = language?.let { id -> texts to texts.map { SyntaxLines(it, SyntaxEngine.tokens(context, id, it).orEmpty()) } }
    }
    val lines = made?.takeIf { it.first == texts }?.second
    return remember(lines, palette) { SyntaxColouring(lines, palette) }
}

private const val OLD = 0
private const val NEW = 1
private const val CURRENT = 0
private const val INCOMING = 1
