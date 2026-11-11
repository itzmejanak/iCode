package dev.icode.mobile.ui.syntax

import dev.icode.mobile.ui.editor.ShippedGrammars
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.Registry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lazy, per-language loading (docs/syntax-highlighting-plan.md D4; A1, A2), through the engine's own
 * loading logic on the shipped catalog and grammars, each into a plain tm4e registry. [broken]
 * languages throw when added, as a grammar that cannot be parsed does.
 */
class GrammarLoaderTest {
    private val added = mutableListOf<String>()
    private val reported = mutableListOf<String>()

    private fun loader(broken: Set<String> = emptySet()) = GrammarLoader(
        ShippedGrammars.catalog,
        { Registry() },
        { language ->
            check(language.id !in broken) { "${language.id} cannot be parsed" }
            added += language.id
            addGrammar(IGrammarSource.fromFile(ShippedGrammars.asset(language.grammar)))
        },
    ) { language, _ -> reported += language.id }

    private fun Registry.grammar(id: String): IGrammar =
        checkNotNull(grammarForScopeName(ShippedGrammars.catalog.byId(id)!!.scope)) { "$id not in the registry" }

    private fun scopes(grammar: IGrammar, line: String) = ShippedGrammars.scopes(grammar, listOf(line)).single().joinToString(" ")

    @Test fun `nothing is parsed until a language is asked for`() {
        loader()
        assertEquals(emptyList<String>(), added)
    }

    @Test fun `asking for Kotlin parses Kotlin only`() {
        val registry = loader().load("kotlin")
        assertEquals(listOf("kotlin"), added)
        assertTrue(scopes(registry.grammar("kotlin"), "fun main() = Unit").contains("keyword"))
    }

    @Test fun `asking for HTML parses HTML and what it includes, CSS and JavaScript`() {
        val registry = loader().load("html")
        assertEquals(listOf("html", "css", "javascript"), added)
        assertTrue(scopes(registry.grammar("html"), STYLE).contains(CSS_PROPERTY))
    }

    @Test fun `a closure is followed through every include, and a scope the catalog lacks is left out`() {
        val loader = loader()
        val markdown = ShippedGrammars.catalog.byId("markdown")!!
        val closure = loader.closure(markdown).map { it.scope }
        assertEquals(markdown.scope, closure.first())
        assertTrue(closure.containsAll(listOf("text.html.basic", "source.css", "source.js")))
        assertTrue("a fence's language is in the closure", "source.julia" in closure)
        val haml = loader.closure(ShippedGrammars.catalog.byId("haml")!!).map { it.scope }
        assertTrue("text.plain is not shipped", "text.plain" in ShippedGrammars.catalog.byId("haml")!!.includes && "text.plain" !in haml)
        val reachable = closure.flatMap { ShippedGrammars.catalog.byScope(it)!!.includes }.filter { ShippedGrammars.catalog.byScope(it) != null }
        assertTrue("every shipped include of a member is in the closure", closure.containsAll(reachable))
    }

    @Test fun `each load is a registry of its own, so no two threads share a grammar`() {
        val loader = loader()
        assertNotSame(loader.load("json"), loader.load("json"))
    }

    @Test fun `a grammar that throws fails its own language only, and is reported once`() {
        val loader = loader(broken = setOf("kotlin"))
        val first = assertThrows(IllegalStateException::class.java) { loader.load("kotlin") }
        val again = assertThrows(IllegalStateException::class.java) { loader.load("kotlin") }
        assertSame(first, again)
        assertSame(first, loader.failure("kotlin"))
        assertEquals(listOf("kotlin"), reported)
        assertTrue(scopes(loader.load("java").grammar("java"), "class A {}").contains("meta.class.java"))
        assertNotNull(loader.load("json").grammar("json"))
        assertNull(loader.failure("java"))
        assertNull(loader.failure("json"))
    }

    @Test fun `a broken grammar that another includes leaves the includer coloured, without its rules`() {
        val loader = loader(broken = setOf("css"))
        val html = loader.load("html").grammar("html")
        assertNull(loader.failure("html"))
        assertNotNull(loader.failure("css"))
        assertTrue(scopes(html, "<p class=\"a\">x</p>").contains("entity.name.tag"))
        assertTrue("CSS rules are left out", CSS_PROPERTY !in scopes(html, STYLE))
        loader.load("html")
        assertEquals("css is tried once", listOf("css"), reported)
        assertThrows(IllegalStateException::class.java) { loader.load("css") }
    }

    private companion object {
        const val STYLE = "<style>p { color: red; }</style>"
        const val CSS_PROPERTY = "support.type.property-name.css"
    }
}
