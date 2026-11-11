package dev.icode.mobile.ui.syntax

import dev.icode.mobile.data.git.GitConflictParser
import dev.icode.mobile.data.git.GitDiffParser
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.GitDiffLineKind
import dev.icode.mobile.domain.LineDiff
import dev.icode.mobile.ui.editor.ShippedGrammars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Code drawn a line at a time colours each line as its whole file does (docs/syntax-highlighting-plan.md S5, A4 extended). */
class SyntaxLinesTest {
    private val kotlin = languageOf("Demo.kt", "")!!

    private fun spans(text: String): List<SyntaxSpan> {
        val scope = ShippedGrammars.catalog.byId(kotlin)!!.scope
        return syntaxSpans(LimitedGrammar(ShippedGrammars.load(ShippedGrammars.registry(), scope)), text)
    }

    /** Each text of [texts] tokenized whole, by line, as rememberSyntaxLines makes them. */
    private fun lines(texts: List<String>) = texts.map { SyntaxLines(it, spans(it)) }

    /** Line [index] (from 0) of [text]'s spans when the whole of [text] is tokenized: what the editor colours it with. */
    private fun whole(text: String, index: Int): List<SyntaxSpan> = SyntaxLines(text, spans(text)).line(index, text.split('\n')[index].length)

    private val old = """
        package demo

        /**
         * Greets.
         */
        fun greet(name: String): String {
            val text = ${"\"\"\""}
                Hi, ${'$'}name
            ${"\"\"\""}.trimIndent()
            return text
        }
    """.trimIndent()

    private val new = """
        package demo

        /**
         * Greets someone.
         */
        fun greet(name: String): String {
            val text = ${"\"\"\""}
                Hello, ${'$'}name
            ${"\"\"\""}.trimIndent()
            return text // done
        }
    """.trimIndent()

    @Test fun `with the whole texts, every line of a diff has the spans its whole file gives it`() {
        val hunks = LineDiff.hunks(old, new)
        val sources = diffSources(hunks, old, new)
        assertEquals(listOf(old, new), sources.texts)
        val made = lines(sources.texts)
        var checked = 0
        hunks.forEachIndexed { at, hunk ->
            hunk.lines.forEachIndexed { index, line ->
                val place = sources.places[at][index]
                val expected = if (line.kind == GitDiffLineKind.Removed) whole(old, line.oldLine!! - 1) else whole(new, line.newLine!! - 1)
                assertEquals(line.text, expected, made[place.text].line(place.line, line.text.length))
                checked++
            }
        }
        assertTrue(checked >= 6)
        // The added line inside the raw string, and inside the comment, are coloured as there, not as code.
        val inString = hunks.flatMap { it.lines }.first { it.text.contains("Hello") }
        assertEquals(SyntaxRole.String, whole(new, inString.newLine!! - 1).single().style.role)
        val inComment = hunks.flatMap { it.lines }.first { it.text.contains("someone") }
        assertEquals(SyntaxRole.Comment, whole(new, inComment.newLine!! - 1).first().style.role)
    }

    @Test fun `each unchanged and added line of Git's patch has the spans the whole new file gives it`() {
        val patch = """
            diff --git a/Demo.kt b/Demo.kt
            --- a/Demo.kt
            +++ b/Demo.kt
            @@ -6,6 +6,6 @@ fun greet(name: String): String {
             fun greet(name: String): String {
                 val text = ${"\"\"\""}
            -        Hi, ${'$'}name
            +        Hello, ${'$'}name
                 ${"\"\"\""}.trimIndent()
            -    return text
            +    return text // done
             }
        """.trimIndent()
        val hunks = GitDiffParser.parse(patch).single().hunks
        val sources = diffSources(hunks)
        val made = lines(sources.texts)
        val shown = hunks.single().lines.filter { it.kind != GitDiffLineKind.Removed }
        assertEquals(6, shown.size)
        hunks.single().lines.forEachIndexed { index, line ->
            if (line.kind == GitDiffLineKind.Removed) return@forEachIndexed
            val place = sources.places[0][index]
            assertEquals(line.text, whole(new, line.newLine!! - 1), made[place.text].line(place.line, line.text.length))
        }
        // A removed line is coloured from the old side: inside the old file's raw string too.
        val removed = hunks.single().lines.indexOfFirst { it.kind == GitDiffLineKind.Removed }
        val place = sources.places[0][removed]
        assertEquals(SyntaxRole.String, made[place.text].line(place.line, hunks.single().lines[removed].text.length).single().style.role)
    }

    @Test fun `hunks that no longer match the whole texts are coloured from their own lines`() {
        val hunks = LineDiff.hunks(old, new)
        // The text gained a line at its top after its changes were counted: their line numbers are one off.
        val sources = diffSources(hunks, old, "// moved\n$new")
        assertEquals(hunks.size * 2, sources.texts.size)
        val sameAlone = diffSources(hunks)
        assertEquals(sameAlone.texts, sources.texts)
        assertEquals(sameAlone.places, sources.places)
    }

    @Test fun `a conflict's sides are coloured by the file's language, each as the file it would make`() {
        val text = listOf(
            "fun pick(): String {",
            "<<<<<<< HEAD",
            "    return \"ours\" // kept",
            "=======",
            "    return 42",
            ">>>>>>> theirs",
            "}",
        ).joinToString("\n")
        val conflict = GitConflictParser.parse("src/Pick.kt", text)
        val section = conflict.sections.single()
        val sources = conflictSources(conflict)
        val language = languageOf(conflict.path.substringAfterLast('/'), conflict.lines.first())
        assertEquals(kotlin, language)
        val current = "fun pick(): String {\n    return \"ours\" // kept\n}"
        val incoming = "fun pick(): String {\n    return 42\n}"
        assertEquals(listOf(current, incoming), sources.texts)
        val made = lines(sources.texts)
        val (ours, theirs) = sources.sides.getValue(section.startLine)
        val oursSpans = made[ours.single().text].line(ours.single().line, section.current.single().length)
        val theirsSpans = made[theirs.single().text].line(theirs.single().line, section.incoming.single().length)
        assertEquals(whole(current, 1), oursSpans)
        assertEquals(whole(incoming, 1), theirsSpans)
        assertTrue(oursSpans.map { it.style.role }.containsAll(listOf(SyntaxRole.Control, SyntaxRole.String, SyntaxRole.Comment)))
        assertTrue(theirsSpans.any { it.style.role == SyntaxRole.Number })
        // The lines outside the conflict: coloured from the current version, at their places in it.
        assertEquals(SyntaxPlace(0, 0), sources.context[0])
        assertEquals(SyntaxPlace(0, 2), sources.context[section.endLine + 1])
    }

    @Test fun `a line's spans start at the line and stop at what is drawn`() {
        val style = SyntaxStyle(SyntaxRole.Keyword)
        val lines = SyntaxLines("val a\r\nval b", listOf(SyntaxSpan(0, 3, style), SyntaxSpan(4, 6, style), SyntaxSpan(7, 10, style)))
        // The '\r' is not drawn: a span reaching it stops at the line's end.
        assertEquals(listOf(SyntaxSpan(0, 3, style), SyntaxSpan(4, 5, style)), lines.line(0, 5))
        assertEquals(listOf(SyntaxSpan(0, 3, style)), lines.line(1, 5))
        assertEquals(emptyList<SyntaxSpan>(), lines.line(2, 5))
    }
}
