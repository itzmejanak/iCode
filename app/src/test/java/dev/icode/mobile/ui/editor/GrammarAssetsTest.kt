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
     * reason. So each include is either shipped or a language named here as not
     * shipped, and this list is exactly those (a stale name fails too).
     */
    @Test fun `every grammar a grammar pulls in is shipped, or named as not shipped`() {
        val missing = ShippedGrammars.all.flatMap { it.includes }.filter { it !in shipped }.toSet()
        assertEquals(NOT_SHIPPED, missing.toSortedSet())
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
        /** Languages other grammars can embed (a Markdown fence, an assembly block in C) that iCode does not ship. */
        val NOT_SHIPPED = sortedSetOf(
            "source.arm", "source.asm", "source.asp.vb.net", "source.clojure", "source.coffee",
            "source.elixir", "source.erlang", "source.fsharp", "source.glsl", "source.graphql",
            "source.html", "source.js.jquery", "source.js.regexp", "source.json.comments", "source.julia",
            "source.objc", "source.perl", "source.perl.6", "source.powershell", "source.r",
            "source.regexp.python", "source.scala", "source.x86", "source.x86_64", "text.bibtex",
            "text.haml", "text.html.derivative", "text.html.handlebars", "text.html.javadoc", "text.html.php.blade",
            "text.log", "text.pug", "text.slim", "text.tex.latex",
        )
    }
}
