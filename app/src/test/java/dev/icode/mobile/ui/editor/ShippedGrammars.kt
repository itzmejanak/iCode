package dev.icode.mobile.ui.editor

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** The grammars the APK ships, read from the assets as the editor reads them (JVM tests only). */
internal object ShippedGrammars {
    private val assets = File("src/main/assets")

    class Grammar(val scope: String, val scopes: Set<String>, val includes: Set<String>)

    val all: List<Grammar> by lazy {
        val languages = JSONObject(File(assets, "textmate/languages.json").readText()).getJSONArray("languages")
        (0 until languages.length()).map { index ->
            val entry = languages.getJSONObject(index)
            val file = File(assets, entry.getString("grammar"))
            val text = file.readText()
            if (file.name.endsWith(".json")) {
                val grammar = JSONObject(text)
                val body = grammar.keys().asSequence().filter { it != "name" }.map { grammar.get(it) }.toList()
                Grammar(entry.getString("scopeName"), body.flatMap(::names).toSet(), body.flatMap(::includes).toSet())
            } else {
                // A plist: the grammar's own title is the first <key>name</key>, not a scope.
                val names = PLIST_NAME.findAll(text).map { it.groupValues[2] }.drop(1).flatMap(::words).toSet()
                val includes = PLIST_INCLUDE.findAll(text).map { it.groupValues[1] }.mapNotNull(::external).toSet()
                Grammar(entry.getString("scopeName"), names, includes)
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
