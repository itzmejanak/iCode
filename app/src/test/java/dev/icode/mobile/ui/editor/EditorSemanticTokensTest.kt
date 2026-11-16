package dev.icode.mobile.ui.editor

import dev.icode.mobile.designsystem.SyntaxRole
import dev.icode.mobile.domain.LanguageHighlights
import dev.icode.mobile.domain.SemanticToken
import dev.icode.mobile.domain.SemanticTokens
import dev.icode.mobile.ui.syntax.SyntaxStyle
import java.util.Collections
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When one editor asks for semantic tokens, and when it does not. */
class EditorSemanticTokensTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val asked = Collections.synchronizedList(mutableListOf<String>())
    private val shown = Collections.synchronizedList(mutableListOf<SemanticLines?>())
    private var coloured = true
    private val text = listOf("def f(a):", "    a.b")

    private val server = object : LanguageHighlights {
        override suspend fun semanticTokens(location: String): SemanticTokens {
            asked += location
            return SemanticTokens(text.joinToString("\n"), listOf(SemanticToken(0, 6, 1, "parameter", emptySet()), SemanticToken(1, 6, 1, "property", emptySet())))
        }
    }

    private fun tokens(source: LanguageHighlights = server, enabled: Boolean = true) =
        EditorSemanticTokens(scope, coloured = { coloured }, show = { shown += it }, background = Dispatchers.Unconfined, lineOf = text::getOrNull).apply {
            this.enabled = enabled
            this.source = source
        }

    @After
    fun stop() = scope.cancel()

    @Test
    fun `an open file with a grammar asks its server, and the answer's runs are shown`() = runBlocking {
        tokens().open("/w/main.py")
        withTimeout(5_000) { while (shown.lastOrNull() == null) delay(5) }
        assertEquals(listOf("/w/main.py"), asked)
        val lines = shown.last()!!
        assertEquals(listOf(SemanticRun(6, 7, SyntaxStyle(SyntaxRole.Variable))), lines.runs(0))
        assertEquals(listOf(SemanticRun(6, 7, SyntaxStyle(SyntaxRole.Attribute))), lines.runs(1))
    }

    @Test
    fun `with the setting off, no server, or no grammar, nothing is asked and nothing is drawn over the grammar`() = runBlocking {
        tokens(enabled = false).open("/w/a.py")
        tokens(source = LanguageHighlights.None).open("/w/b.py")
        coloured = false
        tokens().apply { open("/w/c.py"); edited(); refresh() }
        delay(EditorSemanticTokens.QUIET_MILLIS + 100)
        assertTrue(asked.toString(), asked.isEmpty())
        assertTrue(shown.all { it == null })
    }

    @Test
    fun `turning the setting off clears the runs, and typing asks again only once it stops`() = runBlocking {
        val answered = CompletableDeferred<Unit>()
        val editor = tokens(object : LanguageHighlights {
            override suspend fun semanticTokens(location: String): SemanticTokens? = server.semanticTokens(location).also { answered.complete(Unit) }
        })
        editor.open("/w/main.py")
        withTimeout(5_000) { answered.await() }
        repeat(3) { editor.edited() }
        delay(EditorSemanticTokens.QUIET_MILLIS + 200)
        // The open's question, then one for the three keystrokes: each new one cancels the one before.
        assertEquals(2, asked.size)
        editor.enabled = false
        assertNull(shown.last())
    }
}
