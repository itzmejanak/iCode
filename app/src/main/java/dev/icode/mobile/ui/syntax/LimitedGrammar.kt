package dev.icode.mobile.ui.syntax

import java.time.Duration
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.grammar.IToken
import org.eclipse.tm4e.core.grammar.ITokenizeLineResult

/**
 * [grammar] within [SyntaxLimits], for every caller: Sora's analyzer asks for 2 s a line and the whole
 * line, and this is the one place both are bounded. A line gets at most [SyntaxLimits.LINE_TIME]. A line
 * longer than [SyntaxLimits.LINE_CHARS] is tokenized only up to there and the rest is one plain token
 * of the grammar's own scope; the next line goes on from the state the long line started in, as
 * VS Code does, so one minified line does not leave the lines after it inside a string.
 */
internal class LimitedGrammar(private val grammar: IGrammar) : IGrammar by grammar {
    override fun tokenizeLine(lineText: String): ITokenizeLineResult<Array<IToken>> = tokenizeLine(lineText, null, null)

    override fun tokenizeLine(lineText: String, prevState: IStateStack?, timeLimit: Duration?): ITokenizeLineResult<Array<IToken>> {
        val cut = SyntaxLimits.cut(lineText)
        if (cut == lineText.length) return grammar.tokenizeLine(lineText, prevState, limited(timeLimit))
        val head = grammar.tokenizeLine(lineText.substring(0, cut), prevState, limited(timeLimit))
        val tokens = head.tokens.filter { it.startIndex < cut } + PlainToken(cut, lineText.length, listOf(grammar.scopeName))
        return Result(tokens.toTypedArray(), prevState, head.isStoppedEarly)
    }

    override fun tokenizeLine2(lineText: String): ITokenizeLineResult<IntArray> = tokenizeLine2(lineText, null, null)

    override fun tokenizeLine2(lineText: String, prevState: IStateStack?, timeLimit: Duration?): ITokenizeLineResult<IntArray> {
        val cut = SyntaxLimits.cut(lineText)
        if (cut == lineText.length) return grammar.tokenizeLine2(lineText, prevState, limited(timeLimit))
        val head = grammar.tokenizeLine2(lineText.substring(0, cut), prevState, limited(timeLimit))
        // Pairs of (start, metadata); the plain token's metadata is an empty line's at the grammar's root.
        val kept = head.tokens.asList().chunked(2).filter { it[0] < cut }.flatten()
        val plain = grammar.tokenizeLine2("", null, SyntaxLimits.LINE_TIME).tokens[1]
        return Result((kept + listOf(cut, plain)).toIntArray(), prevState, head.isStoppedEarly)
    }

    private fun limited(timeLimit: Duration?): Duration =
        if (timeLimit == null || timeLimit.isZero || timeLimit > SyntaxLimits.LINE_TIME) SyntaxLimits.LINE_TIME else timeLimit

    private class Result<T>(private val tokens: T, private val state: IStateStack?, private val stoppedEarly: Boolean) : ITokenizeLineResult<T> {
        override fun getTokens(): T = tokens
        override fun getRuleStack(): IStateStack? = state
        override fun isStoppedEarly(): Boolean = stoppedEarly
    }

    private class PlainToken(private var start: Int, private val end: Int, private val scopes: List<String>) : IToken {
        override fun getStartIndex(): Int = start
        override fun setStartIndex(startIndex: Int) {
            start = startIndex
        }
        override fun getEndIndex(): Int = end
        override fun getScopes(): List<String> = scopes
    }
}
