package dev.icode.mobile.ui.editor

import java.io.File
import org.eclipse.tm4e.core.grammar.IGrammar
import org.eclipse.tm4e.core.grammar.IStateStack
import org.eclipse.tm4e.core.registry.IGrammarSource
import org.eclipse.tm4e.core.registry.IRegistryOptions
import org.eclipse.tm4e.core.registry.Registry
import org.json.JSONArray
import org.json.JSONObject

/** The grammars the APK ships, read from the assets as the editor reads them (JVM tests only). */
internal object ShippedGrammars {
    private val assets = File("src/main/assets")

    class Grammar(val scope: String, val scopes: Set<String>, val includes: Set<String>)

    /** Each registered scope and its grammar file, in `languages.json` order. */
    val files: Map<String, File> by lazy {
        val languages = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        (0 until languages.length()).associate { index ->
            val entry = languages.getJSONObject(index)
            entry.getString("scopeName") to File(assets, entry.getString("grammar"))
        }
    }

    fun source(scope: String): IGrammarSource? = files[scope]?.let(IGrammarSource::fromFile)

    /**
     * A new tm4e registry over the shipped grammars, through tm4e's public API only. Each call is
     * cold: nothing parsed or compiled yet. On the JVM its regex engine is joni, not Oniguruma.
     */
    fun registry(): Registry = Registry(object : IRegistryOptions {
        override fun getGrammarSource(scopeName: String): IGrammarSource? = source(scopeName)
    })

    fun load(registry: Registry, scope: String): IGrammar = checkNotNull(registry.loadGrammar(scope)) { "$scope not loaded" }

    /** Each line's tokens as `start:scope scope…`, the line's state carried to the next as the editor does. */
    fun scopes(grammar: IGrammar, lines: List<String>): List<List<String>> {
        var state: IStateStack? = null
        return lines.map { line ->
            val result = grammar.tokenizeLine(line, state, null)
            state = result.ruleStack
            result.tokens.map { "${it.startIndex}:${it.scopes.joinToString(" ")}" }
        }
    }

    val all: List<Grammar> by lazy {
        files.map { (scope, file) ->
            val text = file.readText()
            if (file.name.endsWith(".json")) {
                val grammar = JSONObject(text)
                val body = grammar.keys().asSequence().filter { it != "name" }.map { grammar.get(it) }.toList()
                Grammar(scope, body.flatMap(::names).toSet(), body.flatMap(::includes).toSet())
            } else {
                // A plist: the grammar's own title is the first <key>name</key>, not a scope.
                val names = PLIST_NAME.findAll(text).map { it.groupValues[2] }.drop(1).flatMap(::words).toSet()
                val includes = PLIST_INCLUDE.findAll(text).map { it.groupValues[1] }.mapNotNull(::external).toSet()
                Grammar(scope, names, includes)
            }
        }
    }

    private fun names(value: Any?): List<String> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().flatMap { key: String ->
            val child = value.get(key)
            if ((key == "name" || key == "contentName") && child is String) words(child) else names(child)
        }
        is JSONArray -> (0 until value.length()).flatMap { names(value.get(it)) }
        else -> emptyList()
    }

    private fun includes(value: Any?): List<String> = when (value) {
        is JSONObject -> value.keys().asSequence().toList().flatMap { key: String ->
            val child = value.get(key)
            if (key == "include" && child is String) listOfNotNull(external(child)) else includes(child)
        }
        is JSONArray -> (0 until value.length()).flatMap { includes(value.get(it)) }
        else -> emptyList()
    }

    /** Another grammar a rule pulls in by scope; `#local`, `$self` and `$base` stay inside. */
    private fun external(include: String): String? =
        include.substringBefore('#').takeIf { it.isNotEmpty() && !it.startsWith("$") }

    private fun words(text: String) = text.split(' ').filter(String::isNotEmpty)

    private val PLIST_NAME = Regex("<key>(name|contentName)</key>\\s*<string>([^<]*)</string>")
    private val PLIST_INCLUDE = Regex("<key>include</key>\\s*<string>([^<]*)</string>")
}
