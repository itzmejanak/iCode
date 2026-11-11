package dev.icode.mobile.ui.editor

import dev.icode.mobile.domain.FileTypes
import org.eclipse.tm4e.languageconfiguration.internal.model.LanguageConfiguration
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarAssetsTest {
    private val shipped = ShippedGrammars.all.map { it.scope }.toSet()

    /**
     * A grammar that pulls in one the APK does not ship loses whatever that one
     * colours — YAML shipped without its YAML 1.2 grammar was plain for that
     * reason. So each include is either shipped or a scope named here, with the
     * reason it is not shipped, and this list is exactly those (a stale name fails too).
     */
    @Test fun `every grammar a grammar pulls in is shipped, or named as not shipped`() {
        val missing = ShippedGrammars.all.flatMap { it.includes }.filter { it !in shipped }.toSet()
        assertEquals(NOT_SHIPPED.keys, missing.toSortedSet())
    }

    @Test fun `every shipped grammar is opened by a file type or pulled in by another`() {
        val opened = FileTypes.rules.mapNotNull { it.type.language }.mapNotNull { ShippedGrammars.catalog.byId(it)?.scope }.toSet()
        val included = ShippedGrammars.all.flatMap { it.includes }.toSet()
        assertEquals(emptySet<String>(), shipped - opened - included)
    }

    /**
     * The engine in Sora reads every entry of a captures table as a rule; VS Code's
     * skips the rest. A note such as YAML's `"comment": "https://…"` there made the
     * editor's analysis fail for YAML and for Markdown, which embeds it.
     */
    @Test fun `every capture is a rule the editor's engine can read`() {
        val notRules = ShippedGrammars.files.values
            .filter { it.name.endsWith(".json") }
            .flatMap { file -> captureEntries(JSONObject(file.readText())).filter { it.second !is JSONObject }.map { "${file.name} ${it.first}" } }
        assertEquals(emptyList<String>(), notRules)
    }

    /**
     * Each language loads, with what it includes, and tokenizes in tm4e (on the JVM, with joni): a grammar
     * the engine cannot read throws here, as Racket's did with a repository entry that was a list. About
     * 7 s for every language, each in a cold registry.
     */
    @Test fun `every shipped grammar loads and tokenizes`() {
        val lines = listOf("a = \"b\" // c # d", "<x y='z'>1.5</x>", "fun f(x) { return 2 }")
        val failed = ShippedGrammars.catalog.all.mapNotNull { language ->
            runCatching { ShippedGrammars.scopes(ShippedGrammars.load(ShippedGrammars.registry(), language.scope), lines) }
                .exceptionOrNull()?.let { "${language.id}: $it" }
        }
        assertEquals(emptyList<String>(), failed)
    }

    /** VS Code's configuration files are JSON with comments and trailing commas; strings are left whole. */
    private fun stripJsonc(text: String): String {
        val out = StringBuilder()
        var i = 0
        var inString = false
        while (i < text.length) {
            val c = text[i]
            when {
                inString -> {
                    out.append(c)
                    if (c == '\\' && i + 1 < text.length) out.append(text[++i]) else if (c == '"') inString = false
                }
                c == '"' -> { inString = true; out.append(c) }
                text.startsWith("//", i) -> { while (i < text.length && text[i] != '\n') i++; continue }
                text.startsWith("/*", i) -> { i = text.indexOf("*/", i).let { if (it < 0) text.length else it + 2 }; continue }
                else -> out.append(c)
            }
            i++
        }
        return out.toString().replace(Regex(",(\\s*[}\\]])"), "$1")
    }

    private fun captureEntries(value: Any?): List<Pair<String, Any?>> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().flatMap { key: String ->
            val child = value.get(key)
            val own = if (key.endsWith("aptures") && child is JSONObject) {
                child.keys().asSequence().toList().map { capture: String -> capture to child.get(capture) }
            } else emptyList()
            own + captureEntries(child)
        }
        is JSONArray -> (0 until value.length()).flatMap { captureEntries(value.get(it)) }
        else -> emptyList()
    }

    /** Each language's brackets, comments and indent rules, read by the parser the editor itself uses. */
    @Test fun `every language configuration loads in the editor's own parser`() {
        val configurations = ShippedGrammars.catalog.all.mapNotNull { language ->
            language.configuration?.let { language.id to ShippedGrammars.asset(it) }
        }
        assertTrue(configurations.size > 30)
        // What the file declares must come back from the parser: a field it cannot read is dropped with only a log line.
        val unreadable = configurations.filter { (_, file) ->
            val declared = JSONObject(stripJsonc(file.readText()))
            val comments = declared.optJSONObject("comments")
            val loaded = runCatching { LanguageConfiguration.load(file.reader()) }.getOrNull()
            loaded == null ||
                (comments?.has("lineComment") == true && loaded.comments?.lineComment == null) ||
                (comments?.has("blockComment") == true && loaded.comments?.blockComment == null) ||
                (declared.has("brackets") && loaded.brackets.isNullOrEmpty())
        }.map { it.first }
        assertEquals(emptyList<String>(), unreadable)
    }

    private companion object {
        /** A name another grammar uses where the shipped grammar of that language has another scope. */
        const val ALIAS = "another name for a shipped grammar's scope"

        /** Embedded only, and declared by no grammar in VS Code 307b60e7, Shiki 8f1828eb or those GitHub Linguist vendors. */
        const val NONE = "no grammar in VS Code, Shiki or the grammars GitHub Linguist vendors has this scope"

        /**
         * Scopes other grammars include (a fence, an assembly block in C, a template's script) that iCode
         * does not ship, each with why; their text keeps its enclosing colour. Writing them is
         * tools/import-grammars.py's to decide, not a hand edit to the grammars (docs/editor-grammar-provenance.md).
         */
        val NOT_SHIPPED = sortedMapOf(
            "source.arm" to "$NONE (inline assembly in C and C++)",
            "source.asm" to "$NONE (inline assembly in C, C++ and Nim; Shiki's assembly is source.asm.x86_64)",
            "source.asy" to "$NONE (Asymptote in LaTeX)",
            "source.asymptote" to "$NONE (Asymptote in LaTeX)",
            "source.c++" to "$ALIAS: source.cpp (in Crystal and MDX)",
            "source.css.sass" to "$ALIAS: source.sass (in Vue Vine)",
            "source.dosbatch" to "$ALIAS: source.batchfile (in Org)",
            "source.dts" to "$NONE (device trees in reStructuredText)",
            "source.fs" to "$ALIAS: source.fsharp (in Org)",
            "source.gfm" to "$ALIAS: text.html.markdown (in MDX)",
            "source.html" to "$ALIAS: text.html.basic (in SassDoc)",
            "source.js.jquery" to "$NONE (in Ruby and Crystal heredocs)",
            "source.kconfig" to "$NONE (in reStructuredText)",
            "source.less" to "$ALIAS: source.css.less (in Pug)",
            "source.lisp" to "$ALIAS: source.commonlisp (in Org)",
            "source.postcss" to "$ALIAS: source.css.postcss (in Vue)",
            "source.pug" to "$ALIAS: text.pug (in Org)",
            "source.raku" to "$ALIAS: source.perl.6 (in MDX)",
            "source.smarty" to "$NONE (in Smarty templates)",
            "source.scss" to "$ALIAS: source.css.scss (in Haml)",
            "source.srs" to "$NONE (in Turtle)",
            "source.syntax.regexp.tmLanguage" to "$NONE (regular expressions in VS Code snippets)",
            "source.twig" to "$ALIAS: text.html.twig (in AsciiDoc and LaTeX's Markdown)",
            "source.vbnet" to "$ALIAS: source.asp.vb.net (in MDX)",
            "source.vue" to "$ALIAS: text.html.vue (in Vue's HTML and Vue Vine)",
            "source.x86_64" to "$NONE (inline assembly in C and C++)",
            "text.html.asciidoc" to "$ALIAS: text.asciidoc (in MDX)",
            "text.json" to "$ALIAS: source.json (in Hurl)",
            "text.md" to "$ALIAS: text.html.markdown (in MDX)",
            "text.plain" to "$NONE (plain text in Haml)",
            "text.xml.svg" to "$ALIAS: text.xml (in MDX)",
        )
    }
}
