package dev.icode.mobile.ui.syntax

import dev.icode.mobile.ui.editor.ShippedGrammars
import java.time.Duration
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.grammar.IToken
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every caller's tokenizing within [SyntaxLimits] (docs/editor-and-language-services.md). */
class LimitedGrammarTest {
    private fun js(): IGrammar = ShippedGrammars.load(ShippedGrammars.registry(), "source.js")

    /** One minified line of 1 MiB, as `SyntaxBenchmark` measures it. */
    private val minified = buildString {
        var i = 0
        while (length < 1 shl 20) append("function a$i(b,c){return b+c*2>3?\"s$i\":[1,2,{k:'v'}]};var x$i=/re[a-z]+/g;").also { i++ }
    }

    @Test fun `a 1 MiB line is tokenized only to the line limit, then plain, within the time limit`() {
        val grammar = js()
        val limited = LimitedGrammar(grammar)
        val before = limited.tokenizeLine2("var a = 1;", null, null).ruleStack
        val started = System.nanoTime()
        // Sora's analyzer asks for 2 s a line.
        val result = limited.tokenizeLine2(minified, before, Duration.ofSeconds(2))
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("took $millis ms", millis < 1_000)
        val starts = result.tokens.filterIndexed { index, _ -> index % 2 == 0 }
        assertEquals(SyntaxLimits.LINE_CHARS, starts.last())
        assertEquals("the rest is the root's plain token", grammar.tokenizeLine2("").tokens[1], result.tokens.last())
        assertSame("the next line goes on from the long line's start", before, result.ruleStack)
    }

    @Test fun `a long line of a fast grammar is coloured up to the cut`() {
        val json = "[" + "{\"id\":1,\"ok\":true},".repeat(20_000) + "{}]"
        val tokens = LimitedGrammar(ShippedGrammars.load(ShippedGrammars.registry(), "source.json")).tokenizeLine(json, null, null).tokens
        assertTrue("${tokens.size} tokens", tokens.size > 1_000)
        assertEquals(SyntaxLimits.LINE_CHARS, tokens.last().startIndex)
    }

    @Test fun `scopes past the cut are one plain token of the grammar's scope`() {
        val tokens = LimitedGrammar(js()).tokenizeLine(minified, null, null).tokens
        val last = tokens.last()
        assertEquals(SyntaxLimits.LINE_CHARS, last.startIndex)
        assertEquals(minified.length, last.endIndex)
        assertEquals(listOf("source.js"), last.scopes)
        assertTrue(tokens.dropLast(1).all { it.startIndex < SyntaxLimits.LINE_CHARS })
    }

    @Test fun `a line within the limit is tokenized as the grammar tokenizes it`() {
        val line = "const x = { a: [1, 'two'] }; // done"
        val grammar = js()
        val limited = LimitedGrammar(js())
        assertArrayEquals(grammar.tokenizeLine2(line).tokens, limited.tokenizeLine2(line).tokens)
        assertEquals(
            grammar.tokenizeLine(line).tokens.map { "${it.startIndex}:${it.scopes}" },
            limited.tokenizeLine(line).tokens.map { "${it.startIndex}:${it.scopes}" },
        )
    }

    @Test fun `no line gets more than the time limit, whatever is asked for`() {
        val asked = mutableListOf<Duration?>()
        val inner = js()
        val recording = object : IGrammar by inner {
            override fun tokenizeLine2(lineText: String, prevState: IStateStack?, timeLimit: Duration?): ITokenizeLineResult<IntArray> {
                asked += timeLimit
                return inner.tokenizeLine2(lineText, prevState, timeLimit)
            }

            override fun tokenizeLine(lineText: String, prevState: IStateStack?, timeLimit: Duration?): ITokenizeLineResult<Array<IToken>> {
                asked += timeLimit
                return inner.tokenizeLine(lineText, prevState, timeLimit)
            }
        }
        val limited = LimitedGrammar(recording)
        limited.tokenizeLine2("a", null, Duration.ofSeconds(2))
        limited.tokenizeLine2("a", null, null)
        limited.tokenizeLine("a", null, Duration.ZERO)
        limited.tokenizeLine2("a", null, Duration.ofMillis(5))
        assertEquals(listOf(SyntaxLimits.LINE_TIME, SyntaxLimits.LINE_TIME, SyntaxLimits.LINE_TIME, Duration.ofMillis(5)), asked)
    }
}
