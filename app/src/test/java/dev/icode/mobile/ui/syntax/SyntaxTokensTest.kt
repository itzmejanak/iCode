package dev.icode.mobile.ui.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.designsystem.everyPalette
import dev.icode.mobile.designsystem.syntaxColor
import dev.icode.mobile.ui.editor.ShippedGrammars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxTokensTest {
    private fun spans(language: String, text: String): List<SyntaxSpan> {
        val scope = ShippedGrammars.catalog.byId(language)!!.scope
        return syntaxSpans(LimitedGrammar(ShippedGrammars.load(ShippedGrammars.registry(), scope)), text)
    }

    private fun List<SyntaxSpan>.at(text: String, part: String): SyntaxStyle? {
        val index = text.indexOf(part)
        check(index >= 0) { part }
        return firstOrNull { index >= it.start && index < it.end }?.style
    }

    @Test fun `a Kotlin sample's spans have the roles the editor colours it with`() {
        val text = "fun main() {\n    val n = 42 // note\n    println(\"hi\")\n}"
        val spans = spans("kotlin", text)
        assertEquals(SyntaxRole.Keyword, spans.at(text, "fun")?.role)
        assertEquals(SyntaxRole.Number, spans.at(text, "42")?.role)
        assertEquals(SyntaxRole.Comment, spans.at(text, "// note")?.role)
        assertEquals(SyntaxRole.String, spans.at(text, "\"hi\"")?.role)
        // Plain text has no span; spans are in order and never overlap.
        assertEquals(SyntaxRole.Function, spans.at(text, "main")?.role)
        assertNull(spans.at(text, "    val"))
        assertTrue(spans.zipWithNext().all { (a, b) -> a.end <= b.start })
    }

    @Test fun `a Markdown sample's spans carry their roles and styles`() {
        val text = "# Title\n**bold** and *it*\n- item\n`code`"
        val spans = spans("markdown", text)
        assertEquals(SyntaxStyle(SyntaxRole.Heading, bold = true), spans.at(text, "Title"))
        assertEquals(SyntaxStyle(SyntaxRole.Strong, bold = true), spans.at(text, "bold"))
        assertEquals(SyntaxStyle(SyntaxRole.Emphasis, italic = true), spans.at(text, "it*"))
        assertEquals(SyntaxRole.ListMarker, spans.at(text, "- ")?.role)
        assertEquals(SyntaxRole.String, spans.at(text, "code")?.role)
    }

    @Test fun `the same text is tokenized once`() {
        val cache = SyntaxCache()
        var calls = 0
        val tokenize = { calls++; listOf(SyntaxSpan(0, 3, SyntaxStyle(SyntaxRole.Keyword))) }
        val first = cache.tokens("kotlin", "val x", tokenize)
        assertSame(first, cache.tokens("kotlin", "val x", tokenize))
        // Equal text from another string is the same entry.
        assertSame(first, cache.tokens("kotlin", StringBuilder("val").append(" x").toString(), tokenize))
        assertEquals(1, calls)
        cache.tokens("java", "val x", tokenize)
        cache.tokens("kotlin", "val y", tokenize)
        assertEquals(3, calls)
    }

    @Test fun `the cache drops the least recently used texts past its size`() {
        val cache = SyntaxCache(maxChars = 10)
        var calls = 0
        val tokenize = { calls++; emptyList<SyntaxSpan>() }
        cache.tokens("a", "12345", tokenize)
        cache.tokens("a", "abcde", tokenize)
        cache.tokens("a", "12345", tokenize)
        assertEquals(2, calls)
        // Over 10 characters: "abcde", used least recently, goes.
        cache.tokens("a", "vwxyz", tokenize)
        cache.tokens("a", "12345", tokenize)
        assertEquals(3, calls)
        cache.tokens("a", "abcde", tokenize)
        assertEquals(4, calls)
        // A text longer than the whole cache is tokenized each time, and pushes nothing out.
        cache.tokens("a", "0123456789ab", tokenize)
        cache.tokens("a", "0123456789ab", tokenize)
        cache.tokens("a", "abcde", tokenize)
        assertEquals(6, calls)
    }

    @Test fun `what cannot be coloured is not kept, and a block past the limit is not tokenized`() {
        val cache = SyntaxCache()
        var calls = 0
        assertNull(cache.tokens("broken", "x") { calls++; null })
        assertNull(cache.tokens("broken", "x") { calls++; null })
        assertEquals(2, calls)
        assertNull(cache.tokens("kotlin", "x".repeat(SyntaxLimits.BLOCK_CHARS + 1)) { calls++; emptyList() })
        assertEquals(2, calls)
    }

    /** A3: the spans are roles, so each palette colours the same spans without tokenizing again. */
    @Test fun `a palette change recolours without tokenizing`() {
        val cache = SyntaxCache()
        var calls = 0
        val text = "val n = 42"
        val tokenize = { calls++; listOf(SyntaxSpan(0, 3, SyntaxStyle(SyntaxRole.Keyword)), SyntaxSpan(8, 10, SyntaxStyle(SyntaxRole.Number, bold = true))) }
        val keywords = everyPalette.map { palette ->
            val drawn = syntaxString(text, checkNotNull(cache.tokens("kotlin", text, tokenize)), palette)
            assertEquals(text, drawn.text)
            assertEquals(FontWeight.Bold, drawn.spanStyles.single { it.start == 8 }.item.fontWeight)
            drawn.spanStyles.single { it.start == 0 }.item.color.also { assertEquals(palette.syntaxColor(SyntaxRole.Keyword), it) }
        }
        assertEquals(1, calls)
        assertTrue(keywords.toSet().size > 1)
    }

    @Test fun `plain spans keep the text's own colour`() {
        val drawn = syntaxString("x y", listOf(SyntaxSpan(0, 1, SyntaxStyle(SyntaxRole.Plain, italic = true))), everyPalette.first())
        assertEquals(Color.Unspecified, drawn.spanStyles.single().item.color)
    }
}
